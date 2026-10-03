# LMS – Service & Event Contracts (Phase 3/4)

All services: Spring Boot 3.3.5, Spring Cloud 2023.0.3, Java 17, package `com.lms.<name>`,
same structure/conventions as `enrollment-service` (copy its security/, exception/, event/, config/Rabbit*,
messaging/EventPublisher, OpenApiConfig). Run locally (no Docker). MySQL db `lms_<name>_db`
(`createDatabaseIfNotExist=true`), Eureka at `${EUREKA_URL:http://localhost:8761/eureka}`.

| Service | Port | DB | Consumes queue |
|---|---|---|---|
| progress-service | 8086 | lms_progress_db | progress.queue |
| certificate-service | 8087 | lms_certificate_db | certificate.queue |
| notification-service | 8088 | lms_notification_db | notification.queue |

Gateway routes: `/api/progress/**`, `/api/certificates/**`, `/api/notifications/**`.
Public (no JWT): `GET /api/certificates/verify/**`.

## Event envelope (unchanged)
`{ eventId, eventType, timestamp, aggregateId: "<studentId>-<courseId>", payload: {...} }`
Exchange `lms.events` (topic). Topology is declared identically in every messaging service
(`RabbitTopologyConfig`, copy it verbatim). Consumers must be idempotent (`processed_events` table keyed by eventId).

| eventType | routing key | producer | payload |
|---|---|---|---|
| StudentEnrolled | student.enrolled | enrollment | enrollmentId, studentId, studentName, studentEmail, courseId, courseTitle |
| ModuleCompleted | module.completed | content | moduleId, moduleTitle, courseId, studentId, completedModules, totalModules |
| AssessmentCompleted | assessment.completed | assessment | submissionId, assessmentId, courseId, studentId, score, totalMarks, percentage, passed |
| CourseCompleted | course.completed | progress | enrollmentId, studentId, studentName, studentEmail, courseId, courseTitle, finalScore (best %), completionDate (ISO-8601 string) |
| EnrollmentFailed | enrollment.failed | progress | enrollmentId, studentId, courseId, reason |
| CertificateGenerated | certificate.generated | certificate | certificateId, certificateNumber, verificationCode, studentId, studentName, studentEmail, courseId, courseTitle, certificateUrl |

## Service-to-service calls from event listeners (no user JWT available)
A listener mints a short-lived **service token** with the shared `JWT_SECRET`:
subject `<service-name>@lms.internal`, claims `userId=0`, `role=ADMIN`, `name=<service-name>`, 5 min expiry.
(Only progress-service needs this: `GET http://content-service/api/modules/course/{courseId}` to learn totalModules.)

## Progress rules
* StudentEnrolled → fetch module count. Count ≥ 1 → `ProgressInitialized` (0%). Content-service unreachable
  or 0 modules → publish **EnrollmentFailed** (saga compensation: enrollment → CANCELLED), progress status FAILED.
* percentage = completedModules / totalModules × 100 (2 decimals).
* **Decision**: percentage == 100 AND assessmentPassed == true → append `CourseCompleted` and publish it (once).
