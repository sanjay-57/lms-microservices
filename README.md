# Learning Management System – Event-Driven Spring Boot Microservices

Mini project for **Scalable Microservices** (topic 15: Learning Management System – User, Course, Enrollment,
Assessment, Progress, Certificate; course-completion events).

**Stack:** Spring Boot 3.3 · Spring Cloud Gateway · Eureka · Spring Data JPA · MySQL · RabbitMQ · React (Vite) ·
patterns: **Database-per-service, Saga, CQRS, Event Sourcing, Circuit Breaker, idempotent consumers, DLQ**.
Everything runs **locally without Docker**.

## 1. Architecture

```
                     React UI :5173
                          | REST (JWT)
                          v
            API Gateway :8080  (routing via Eureka, JWT check, CORS, circuit breaker per service)
                          |
   +--------+--------+----+-----+----------+----------+-----------+-------------+
   v        v        v          v          v          v           v             v
 user    course  enrollment  content   assessment  progress   certificate   notification
 :8081   :8082     :8083      :8084      :8085      :8086       :8087          :8088
   |        |        |          |          |          |           |             |
 user_db course_db enroll_db content_db assess_db progress_db cert_db    notification_db   (MySQL, one DB each)
                     \          |          |        / | \          |             |
                      \_________|__________|_______/  |  \_________|_____________/
                                RabbitMQ topic exchange "lms.events" (+ DLX / *.dlq)
 Eureka :8761 (service registry)                     event_store (Event Sourcing)
```

| Service | Port | Responsibility | Publishes | Consumes |
|---|---|---|---|---|
| discovery-server | 8761 | Eureka registry | – | – |
| api-gateway | 8080 | Single entry point, JWT, CORS, Resilience4j circuit breakers | – | – |
| user-service | 8081 | Register / login (JWT), users, roles | – | – |
| course-service | 8082 | Course catalogue | – | – |
| enrollment-service | 8083 | Enrollments, **saga participant** | StudentEnrolled | EnrollmentFailed, CourseCompleted |
| content-service | 8084 | Modules, lessons, module completion | ModuleCompleted | – |
| assessment-service | 8085 | Assessments, questions, auto-scoring | AssessmentCompleted | – |
| progress-service | 8086 | **CQRS + Event Sourcing**, completion decision, **saga** | CourseCompleted, EnrollmentFailed | StudentEnrolled, ModuleCompleted, AssessmentCompleted |
| certificate-service | 8087 | PDF certificate + QR code, public verification | CertificateGenerated | CourseCompleted |
| notification-service | 8088 | In-app notifications + e-mail (or log) | – | StudentEnrolled, CourseCompleted, CertificateGenerated |
| frontend | 5173 | React SPA | – | – |

## 2. The event-driven workflow

```
Student ─enroll─> Enrollment ──StudentEnrolled──> Progress (init 0%, asks Content for #modules)
                                              └─> Notification ("enrollment confirmed")
Student ─complete module─> Content ──ModuleCompleted──> Progress (x/y modules → %)
Student ─submit─> Assessment (score) ──AssessmentCompleted──> Progress
Progress: DECISION  progress = 100%  AND  assessment passed = TRUE
          └─yes─> CourseCompleted ──> Certificate (PDF+QR) ──CertificateGenerated──> Notification
                                  ──> Enrollment (status COMPLETED)
                                  ──> Notification ("congratulations")
Saga compensation: Progress cannot initialise (content-service down / course has no modules)
          ──EnrollmentFailed──> Enrollment (ACTIVE → CANCELLED)
```

Event envelope (same JSON in every service): `{eventId, eventType, timestamp, aggregateId: "<studentId>-<courseId>", payload}`.
Full payload contracts: [docs/CONTRACTS.md](docs/CONTRACTS.md).

| Routing key | Queue(s) |
|---|---|
| student.enrolled | progress.queue, notification.queue |
| module.completed | progress.queue |
| assessment.completed | progress.queue |
| course.completed | certificate.queue, notification.queue, enrollment.queue |
| certificate.generated | notification.queue |
| enrollment.failed | enrollment.queue |

## 3. How the patterns are implemented

**Database per service** – 8 MySQL schemas (`lms_*_db`), no cross-database foreign keys; services share data only
through REST (with the caller's JWT, load-balanced via Eureka) or events.

**Saga (choreography)** – `enrollment-service` creates the enrollment `ACTIVE` and publishes `StudentEnrolled`.
`progress-service` tries to initialise progress; if content-service is unreachable or the course has no modules it
publishes `EnrollmentFailed`, and enrollment-service runs the **compensating transaction** (→ `CANCELLED`).
The happy path ends with `CourseCompleted` → enrollment `COMPLETED`.

**Event Sourcing** – `progress-service` never updates progress in place. Every change is appended to the
`event_store` table (`aggregate_id`, `event_type`, `event_data` JSON, `event_version`; unique
`(aggregate_id, event_version)` = optimistic concurrency). `CourseProgressAggregate` is rebuilt by replaying its
events before each command. `GET /api/progress/events/{aggregateId}` shows the history and
`POST /api/progress/rebuild` (admin) wipes the read model and rebuilds it purely from the event store.

**CQRS** – write side `ProgressCommandService` (event listener → aggregate → event store) is separated from the
read side `ProgressQueryService`, which only reads the denormalised `progress` read model maintained by
`ProgressProjector`.

**Reliability** – events are published only after the DB transaction commits; consumers retry 3× with back-off
and then dead-letter to `<queue>.dlq`; every consumer is idempotent (`processed_events` table keyed by `eventId`).

**Resilience / scalability** – the gateway wraps every route in a Resilience4j **circuit breaker**
(opens at 50 % failures, 15 s) with a JSON 503 fallback (`/actuator/circuitbreakers` shows state). Services are
stateless (JWT), discovered by name, and called through client-side load balancing, so you can start a second
instance of any service on another port (`java -jar target/x.jar --server.port=9084`) and Eureka + the gateway
balance between them; RabbitMQ consumers on the same queue become competing consumers automatically.

**Security** – JWT (HS256) issued by user-service, checked at the gateway and again in each service (roles
STUDENT / INSTRUCTOR / ADMIN with method-level `@PreAuthorize`). Background listeners that must call another
service use a short-lived **service token** (progress → content).

## 4. Prerequisites (install once)

| Tool | Version | macOS | Windows |
|---|---|---|---|
| Java JDK | 17+ | `brew install openjdk@17` | `winget install EclipseAdoptium.Temurin.17.JDK` |
| Maven | 3.9+ | `brew install maven` | `winget install Apache.Maven` |
| Node.js | 18+ | `brew install node` | `winget install OpenJS.NodeJS.LTS` |
| MySQL | 8.x | `brew install mysql && brew services start mysql` | MySQL Installer from dev.mysql.com |
| RabbitMQ | 3.12+ | `brew install rabbitmq && brew services start rabbitmq` | Erlang + RabbitMQ installers from rabbitmq.com/docs/install-windows |

After installing:
* MySQL root password must be `root` – or export `DB_PASSWORD=<yours>` before starting.
  (`mysql -uroot -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'root';"`)
  The databases are created automatically on first start (`sql/init.sql` is optional).
* RabbitMQ management UI: `rabbitmq-plugins enable rabbitmq_management` → http://localhost:15672 (guest/guest).

## 5. Run

### One command (macOS / Linux / Git Bash)
```bash
./scripts/start-all.sh      # builds all services, starts Eureka -> services -> gateway -> React
./scripts/e2e-demo.sh       # runs the whole workflow through the gateway and prints each step
./scripts/stop-all.sh
```
Logs are in `logs/<service>.log`. Options: `SKIP_BUILD=1`, `NO_FRONTEND=1`.

### Windows PowerShell
```powershell
.\scripts\start-all.ps1          # -SkipBuild, -NoFrontend
.\scripts\stop-all.ps1
```

### Manually (one terminal each, in this order)
```bash
cd discovery-server && mvn spring-boot:run          # wait until http://localhost:8761 is up
cd user-service && mvn spring-boot:run              # same for course-, enrollment-, content-, assessment-,
                                                     # progress-, certificate-, notification-service
cd api-gateway && mvn spring-boot:run
cd frontend && npm install && npm run dev
```
Run unit tests: `mvn test` inside any service folder (no database or broker needed).

## 6. URLs

| What | URL |
|---|---|
| React app | http://localhost:5173 |
| API Gateway | http://localhost:8080 |
| Eureka dashboard | http://localhost:8761 |
| RabbitMQ UI | http://localhost:15672 (guest / guest) – watch queues, DLQs |
| Swagger UI | http://localhost:808X/swagger-ui.html (X = 1…8; click **Authorize**, paste JWT) |
| Circuit breakers | http://localhost:8080/actuator/circuitbreakers |

## 7. Demo accounts (seeded on first start)

| Email | Password | Role |
|---|---|---|
| admin@example.com | Admin@123 | ADMIN |
| instructor@example.com | Instructor@123 | INSTRUCTOR (id 2) |
| instructor2@example.com | Instructor@123 | INSTRUCTOR (id 3) |
| student1@example.com / student2 / student3 | Student@123 | STUDENT |

Seed data: 5 published courses, 3–4 modules per course with 2–3 lessons each, one assessment per course
(5 questions × 20 marks, pass mark 60 %).

## 8. Demo script (what to show in a viva)

1. Eureka dashboard: all 9 applications registered.
2. React: log in as `student1`, enroll in a course → *My Learning* shows "Not started" (progress was created
   asynchronously by an event). RabbitMQ UI shows the message flow.
3. Open the course, *Mark module complete* for each module → progress bar moves; the **Event timeline** panel shows
   the event-sourced history.
4. Take the assessment: fail once (course stays *Awaiting assessment*), then pass → status **Completed**,
   enrollment **COMPLETED**, certificate appears under *Certificates* (download PDF, scan QR / open verify page),
   notifications bell shows the e-mails.
5. Saga: as instructor create a published course with no modules; as student enroll → enrollment becomes
   **CANCELLED** (compensation, see `enrollment-service` log "SAGA COMPENSATION").
6. Event sourcing: as admin click *Rebuild progress read model* → read model rebuilt from `event_store`.
7. Circuit breaker: stop notification-service, call `/api/notifications` a few times → instant 503 fallback,
   `/actuator/circuitbreakers` shows `OPEN`.

`./scripts/e2e-demo.sh` automates steps 2–5 and prints every stage.

## 9. API summary (all through the gateway)

| Method | Path | Access |
|---|---|---|
| POST | /api/users/register, /api/users/login | public |
| GET/PUT/DELETE | /api/users/{id}, GET /api/users | self / ADMIN |
| GET/POST/PUT/DELETE | /api/courses, /api/courses/{id}, /api/courses/instructor/{id} | students see PUBLISHED; owner instructor / ADMIN write |
| POST | /api/enrollments | STUDENT (self) / ADMIN |
| GET | /api/enrollments/student/{id}, /course/{id}, /{id}; PUT /{id}/cancel | owner / INSTRUCTOR / ADMIN |
| CRUD | /api/modules, /api/lessons | owner INSTRUCTOR / ADMIN write; authenticated read |
| POST | /api/modules/{id}/complete | enrolled STUDENT → ModuleCompleted |
| GET | /api/modules/course/{courseId}/completed | STUDENT (own completed module ids) |
| CRUD | /api/assessments, /api/assessments/{id}/questions | owner INSTRUCTOR / ADMIN (students never see answers) |
| POST | /api/assessments/{id}/submit | enrolled STUDENT → AssessmentCompleted |
| GET | /api/progress/student/{id}, /student/{id}/course/{courseId} | self / INSTRUCTOR / ADMIN |
| GET | /api/progress/course/{courseId} | INSTRUCTOR / ADMIN |
| GET | /api/progress/events/{studentId-courseId} | self / INSTRUCTOR / ADMIN (event store) |
| POST | /api/progress/rebuild | ADMIN (replay event store) |
| GET | /api/certificates/student/{id}, /{id}, /{id}/download (PDF) | owner / INSTRUCTOR / ADMIN |
| GET | /api/certificates/verify/{code} | **public** |
| GET | /api/notifications/user/{id}, /user/{id}/unread-count; PUT /{id}/read | self / ADMIN |
| GET | /api/notifications | ADMIN |

Errors always have the shape `{timestamp, status, message, path}`.

## 10. Configuration

All settings have defaults; override with environment variables (see `.env.example`): `DB_HOST/PORT/USERNAME/PASSWORD`,
`RABBITMQ_HOST/PORT/USER/PASS`, `EUREKA_URL`, `JWT_SECRET` (identical for all, ≥ 32 chars), `CORS_ALLOWED_ORIGINS`,
`CERTIFICATE_VERIFY_BASE_URL`, and for real e-mails `MAIL_ENABLED=true MAIL_HOST MAIL_PORT MAIL_USERNAME MAIL_PASSWORD`
(otherwise e-mails are logged and stored with status `LOGGED`).

## 11. Troubleshooting

| Problem | Fix |
|---|---|
| `start-all.sh`: MySQL / RabbitMQ not reachable | Start them (`brew services start mysql rabbitmq`, or Windows Services) |
| `Access denied for user 'root'` | Set `DB_PASSWORD` to your MySQL root password |
| Port already in use | Another process uses 8080–8088/8761/5173 (`lsof -i :8080`, Windows `netstat -ano \| findstr :8080`) |
| 503 "… is currently unavailable" from gateway | Service down or not yet registered in Eureka (wait ~30 s after start) |
| Progress stays "Initialising…" | progress-service not running, or check `progress.queue.dlq` in RabbitMQ UI and `logs/progress-service.log` |
| Enrollment becomes CANCELLED immediately | Expected saga compensation: the course has no modules (or content-service was down) |
| 401 everywhere | Token expired (log in again) or `JWT_SECRET` differs between services |
| CORS error in browser | Frontend must run on http://localhost:5173 (or set `CORS_ALLOWED_ORIGINS`) |

## 12. Project structure

```
lms-project final/
├── discovery-server/  api-gateway/
├── user-service/  course-service/  enrollment-service/  content-service/  assessment-service/
├── progress-service/       aggregate/ (event-sourced aggregate), projection/, service/ (command vs query)
├── certificate-service/    pdf/ (OpenPDF + ZXing QR)
├── notification-service/   mail/
├── frontend/               React + Vite
├── docs/CONTRACTS.md       event & port contracts
├── scripts/                start-all / stop-all (.sh, .ps1), e2e-demo.sh
└── sql/init.sql            optional DB creation
```

## 13. Known limitations / future work

* No transactional outbox: if RabbitMQ is down exactly at commit time the event is logged as failed, not resent.
* Shared HMAC JWT secret (an OAuth2 authorization server with RS256 would be the production choice).
* Single MySQL server hosting all schemas (logically separated); in production each service would get its own instance.
* Centralised config (Spring Cloud Config) and distributed tracing (Micrometer + Zipkin) are not included.
