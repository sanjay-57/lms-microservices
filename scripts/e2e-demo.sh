#!/usr/bin/env bash
# End-to-end demo of the complete event-driven workflow, entirely through the API Gateway:
#
#   register -> enroll (StudentEnrolled) -> progress 0% -> complete every module (ModuleCompleted)
#   -> fail the assessment, then pass it (AssessmentCompleted) -> Decision: 100% AND passed
#   -> CourseCompleted -> enrollment COMPLETED + CertificateGenerated -> notifications
#   + Saga compensation: enrolling in a course without modules -> EnrollmentFailed -> enrollment CANCELLED
#
# Requires curl and node (used only to parse JSON). Safe to re-run: a fresh student is registered each time.
# Usage: ./scripts/e2e-demo.sh   [GATEWAY=http://localhost:8080] [COURSE_ID=5]
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
COURSE_ID="${COURSE_ID:-5}"
OUT="$(cd "$(dirname "$0")/.." && pwd)/logs"
mkdir -p "$OUT"

step() { printf "\n\033[1;34m== %s\033[0m\n" "$*"; }
ok()   { printf "   \033[32m✔\033[0m %s\n" "$*"; }
fail() { printf "   \033[31m✘ %s\033[0m\n" "$*"; exit 1; }

# json '<js expression using d>'  (reads JSON from stdin)
json() { node -e "let s='';process.stdin.on('data',c=>s+=c).on('end',()=>{const d=JSON.parse(s);const r=($1);console.log(typeof r==='object'?JSON.stringify(r):r)})"; }

# api METHOD PATH TOKEN [BODY]  -> prints body, fails on HTTP >= 400
api() {
  local method=$1 path=$2 token=${3:-} body=${4:-}
  local args=(-s -w '\n%{http_code}' -X "$method" "$GATEWAY$path")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-H "Content-Type: application/json" -d "$body")
  local res code
  res=$(curl "${args[@]}"); code=${res##*$'\n'}; res=${res%$'\n'*}
  if [ "$code" -ge 400 ]; then echo "$method $path -> HTTP $code $res" >&2; return 1; fi
  printf '%s' "$res"
}

# poll_progress TOKEN STUDENT COURSE JS-CONDITION DESCRIPTION
poll_progress() {
  local i res
  for i in $(seq 1 30); do
    if res=$(api GET "/api/progress/student/$2/course/$3" "$1" 2>/dev/null) && [ "$(echo "$res" | json "$4")" = "true" ]; then
      echo "$res"; return 0
    fi
    sleep 1
  done
  fail "timed out waiting for: $5"
}

login() { api POST /api/users/login "" "{\"email\":\"$1\",\"password\":\"$2\"}" | json 'd.token'; }

step "1. Register a fresh student and log in (user-service, JWT)"
EMAIL="demo$(date +%s)@example.com"
STUDENT_ID=$(api POST /api/users/register "" "{\"name\":\"Demo Student\",\"email\":\"$EMAIL\",\"password\":\"Demo@1234\"}" | json 'd.id')
TOKEN=$(login "$EMAIL" "Demo@1234")
ADMIN=$(login admin@example.com Admin@123)
INSTRUCTOR=$(login instructor@example.com Instructor@123)
ok "student $EMAIL has id $STUDENT_ID"

step "2. Enroll in course $COURSE_ID (enrollment-service publishes StudentEnrolled)"
ENROLLMENT_ID=$(api POST /api/enrollments "$TOKEN" "{\"courseId\":$COURSE_ID}" | json 'd.id')
ok "enrollment $ENROLLMENT_ID created (ACTIVE)"
P=$(poll_progress "$TOKEN" "$STUDENT_ID" "$COURSE_ID" "d.totalModules>0 && d.status!=='FAILED'" "progress initialised")
ok "progress-service initialised progress: $(echo "$P" | json '`${d.percentage}% of ${d.totalModules} modules, status ${d.status}`')"

step "3. Complete every module (content-service publishes ModuleCompleted)"
for MODULE_ID in $(api GET "/api/modules/course/$COURSE_ID" "$TOKEN" | json 'd.map(m=>m.id).join(" ")'); do
  R=$(api POST "/api/modules/$MODULE_ID/complete" "$TOKEN")
  ok "module $MODULE_ID done ($(echo "$R" | json '`${d.completedModules}/${d.totalModules}`'))"
done
P=$(poll_progress "$TOKEN" "$STUDENT_ID" "$COURSE_ID" "Number(d.percentage)===100" "100% progress")
ok "progress = $(echo "$P" | json 'd.percentage')%, status $(echo "$P" | json 'd.status') (100% but assessment not passed -> no CourseCompleted yet)"

step "4. Take the assessment (assessment-service publishes AssessmentCompleted)"
ASSESSMENT_ID=$(api GET "/api/assessments/course/$COURSE_ID" "$TOKEN" | json 'd[0].id')
KEY=$(api GET "/api/assessments/$ASSESSMENT_ID/questions" "$ADMIN")   # admin view includes correctOption
WRONG=$(echo "$KEY" | json '({answers:Object.fromEntries(d.map(q=>[q.id,q.correctOption==="A"?"B":"A"]))})')
R=$(api POST "/api/assessments/$ASSESSMENT_ID/submit" "$TOKEN" "$WRONG")
ok "attempt 1: $(echo "$R" | json '`${d.percentage}% passed=${d.passed}`')"
sleep 2
if [ "$(api GET "/api/progress/student/$STUDENT_ID/course/$COURSE_ID" "$TOKEN" | json 'd.status')" = "COMPLETED" ]; then
  fail "course must not complete with a failed assessment"
fi
ok "decision rule: 100% AND passed=false -> course NOT completed"
RIGHT=$(echo "$KEY" | json '({answers:Object.fromEntries(d.map(q=>[q.id,q.correctOption]))})')
R=$(api POST "/api/assessments/$ASSESSMENT_ID/submit" "$TOKEN" "$RIGHT")
ok "attempt 2: $(echo "$R" | json '`${d.percentage}% passed=${d.passed}`')"

step "5. Decision: progress = 100% AND passed = true -> CourseCompleted"
P=$(poll_progress "$TOKEN" "$STUDENT_ID" "$COURSE_ID" "d.status==='COMPLETED'" "course completion")
ok "progress status COMPLETED (best score $(echo "$P" | json 'd.bestScore')%)"
for i in $(seq 1 15); do
  STATUS=$(api GET "/api/enrollments/$ENROLLMENT_ID" "$TOKEN" | json 'd.status'); [ "$STATUS" = "COMPLETED" ] && break; sleep 1
done
[ "$STATUS" = "COMPLETED" ] && ok "enrollment-service consumed CourseCompleted -> enrollment COMPLETED" || fail "enrollment status $STATUS"

step "6. Certificate (certificate-service consumes CourseCompleted, publishes CertificateGenerated)"
for i in $(seq 1 15); do
  CERTS=$(api GET "/api/certificates/student/$STUDENT_ID" "$TOKEN"); [ "$(echo "$CERTS" | json 'd.length')" -gt 0 ] && break; sleep 1
done
CERT_ID=$(echo "$CERTS" | json 'd[0].id'); CODE=$(echo "$CERTS" | json 'd[0].verificationCode')
ok "certificate $(echo "$CERTS" | json 'd[0].certificateNumber') issued"
curl -sf -H "Authorization: Bearer $TOKEN" "$GATEWAY/api/certificates/$CERT_ID/download" -o "$OUT/certificate-demo.pdf"
ok "PDF saved to logs/certificate-demo.pdf ($(wc -c <"$OUT/certificate-demo.pdf" | tr -d ' ') bytes)"
ok "public verification (no token): $(api GET "/api/certificates/verify/$CODE" | json '`valid=${d.valid} ${d.studentName} - ${d.courseName}`')"

step "7. Notifications (notification-service consumed StudentEnrolled, CourseCompleted, CertificateGenerated)"
sleep 2
api GET "/api/notifications/user/$STUDENT_ID" "$TOKEN" | json 'd.map(n=>`   • [${n.type}] ${n.subject} (${n.status})`).join("\n")'

step "8. Event Sourcing: event_store history of aggregate $STUDENT_ID-$COURSE_ID"
api GET "/api/progress/events/$STUDENT_ID-$COURSE_ID" "$TOKEN" | json 'd.map(e=>`   v${e.version} ${e.eventType}`).join("\n")'

step "9. Saga compensation: enroll in a course that has no modules"
EMPTY_COURSE=$(api POST /api/courses "$INSTRUCTOR" \
  '{"title":"Empty Saga Demo Course","description":"No modules yet","category":"Demo","durationHours":1,"price":0,"status":"PUBLISHED"}' | json 'd.id')
E2=$(api POST /api/enrollments "$TOKEN" "{\"courseId\":$EMPTY_COURSE}" | json 'd.id')
ok "enrollment $E2 in course $EMPTY_COURSE created as ACTIVE"
for i in $(seq 1 15); do
  STATUS=$(api GET "/api/enrollments/$E2" "$TOKEN" | json 'd.status'); [ "$STATUS" = "CANCELLED" ] && break; sleep 1
done
[ "$STATUS" = "CANCELLED" ] && ok "progress-service published EnrollmentFailed -> enrollment compensated to CANCELLED" \
  || fail "expected CANCELLED, got $STATUS"
api DELETE "/api/courses/$EMPTY_COURSE" "$INSTRUCTOR" >/dev/null || true

printf "\n\033[1;32mEnd-to-end workflow completed successfully.\033[0m\n"
