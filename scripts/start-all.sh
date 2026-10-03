#!/usr/bin/env bash
# Builds and starts the whole LMS locally (no Docker).
# Prerequisites: Java 17+, Maven 3.9+, Node 18+, MySQL 8 on :3306 and RabbitMQ on :5672 already running.
# Usage:  ./scripts/start-all.sh            (build + start everything)
#         SKIP_BUILD=1 ./scripts/start-all.sh
#         NO_FRONTEND=1 ./scripts/start-all.sh
# Logs: ./logs/<service>.log     Stop: ./scripts/stop-all.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/logs"
PIDS="$ROOT/.pids"
MVN="${MVN:-mvn}"
mkdir -p "$LOGS" "$PIDS"

# Configuration shared by all services (override by exporting before running this script).
export DB_HOST="${DB_HOST:-localhost}" DB_PORT="${DB_PORT:-3306}"
export DB_USERNAME="${DB_USERNAME:-root}" DB_PASSWORD="${DB_PASSWORD:-root}"
export RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}" RABBITMQ_PORT="${RABBITMQ_PORT:-5672}"
export RABBITMQ_USER="${RABBITMQ_USER:-guest}" RABBITMQ_PASS="${RABBITMQ_PASS:-guest}"
export EUREKA_URL="${EUREKA_URL:-http://localhost:8761/eureka}"
export JWT_SECRET="${JWT_SECRET:-change-this-lms-dev-secret-key-min-32-chars-long}"

SERVICES=(user-service course-service enrollment-service content-service assessment-service
          progress-service certificate-service notification-service)
JAVA_OPTS="${JAVA_OPTS:--Xms64m -Xmx256m}"

port_of() {
  case $1 in
    user-service) echo 8081;; course-service) echo 8082;; enrollment-service) echo 8083;;
    content-service) echo 8084;; assessment-service) echo 8085;; progress-service) echo 8086;;
    certificate-service) echo 8087;; notification-service) echo 8088;;
  esac
}

port_open() { (exec 3<>"/dev/tcp/localhost/$1") 2>/dev/null; }

wait_for() { # name url seconds
  local name=$1 url=$2 timeout=${3:-120} i=0
  printf "  waiting for %-22s" "$name"
  until curl -sf "$url" >/dev/null 2>&1; do
    sleep 2; i=$((i + 2))
    if [ "$i" -ge "$timeout" ]; then echo " TIMEOUT (see logs/$name.log)"; return 1; fi
  done
  echo " UP"
}

start() { # name
  local name=$1
  local jar
  jar=$(ls "$ROOT/$name"/target/*.jar 2>/dev/null | grep -v original | head -1)
  [ -n "$jar" ] || { echo "No jar for $name - run without SKIP_BUILD"; exit 1; }
  # shellcheck disable=SC2086
  nohup java $JAVA_OPTS -jar "$jar" >"$LOGS/$name.log" 2>&1 &
  echo $! >"$PIDS/$name.pid"
  echo "  started $name (pid $!)"
}

echo "== Checking infrastructure"
port_open "$DB_PORT" || { echo "MySQL is not reachable on $DB_HOST:$DB_PORT - start it first"; exit 1; }
port_open "$RABBITMQ_PORT" || { echo "RabbitMQ is not reachable on $RABBITMQ_HOST:$RABBITMQ_PORT - start it first"; exit 1; }
echo "  MySQL and RabbitMQ reachable"

if [ -z "${SKIP_BUILD:-}" ]; then
  echo "== Building all services (mvn package -DskipTests)"
  for svc in discovery-server api-gateway "${SERVICES[@]}"; do
    echo "  building $svc"
    (cd "$ROOT/$svc" && "$MVN" -q -B package -DskipTests) || { echo "Build failed: $svc"; exit 1; }
  done
fi

echo "== Starting Eureka"
start discovery-server
wait_for discovery-server http://localhost:8761/actuator/health 120

echo "== Starting microservices"
for svc in "${SERVICES[@]}"; do start "$svc"; done
for svc in "${SERVICES[@]}"; do wait_for "$svc" "http://localhost:$(port_of "$svc")/actuator/health" 180; done

echo "== Starting API Gateway"
start api-gateway
wait_for api-gateway http://localhost:8080/actuator/health 120

if [ -z "${NO_FRONTEND:-}" ]; then
  echo "== Starting React frontend"
  (cd "$ROOT/frontend" && { [ -d node_modules ] || npm install --silent; } \
     && nohup npm run dev >"$LOGS/frontend.log" 2>&1 & echo $! >"$PIDS/frontend.pid")
  wait_for frontend http://localhost:5173 90
fi

cat <<MSG

All services are up.
  Frontend          http://localhost:5173
  API Gateway       http://localhost:8080
  Eureka            http://localhost:8761
  RabbitMQ UI       http://localhost:15672  (guest/guest)
Give Eureka ~30 s to propagate registrations before the first gateway call.
Run the end-to-end demo:  ./scripts/e2e-demo.sh
MSG
