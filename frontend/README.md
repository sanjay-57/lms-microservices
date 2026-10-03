# LMS Frontend (React + Vite)

Single-page app for the LMS microservices. All calls go through the **API gateway** (default `http://localhost:8080`);
the gateway's CORS config already allows `http://localhost:5173`.

## Requirements
Node 18+ (tested with Node 22) and npm.

## Run

```bash
cd frontend
npm install
cp .env.example .env      # optional – only needed to change the gateway URL (Windows: copy .env.example .env)
npm run dev               # http://localhost:5173
```

Production build: `npm run build` (output in `dist/`), preview it with `npm run preview` (also port 5173).

### Configuration

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080` | Base URL of the API gateway |

The dev server uses port **5173** with `strictPort` (it fails instead of switching port, because CORS only allows 5173).

## Demo accounts

| Role | Email | Password |
|---|---|---|
| Student | student1@example.com | Student@123 |
| Instructor | instructor@example.com | Instructor@123 |
| Admin | admin@example.com | Admin@123 |

## Routes

| Path | Access | Purpose |
|---|---|---|
| `/` | public | Overview of the event flow, demo accounts |
| `/login`, `/register` | public | Auth (register as STUDENT or INSTRUCTOR) |
| `/verify`, `/verify/:code` | public | Certificate verification |
| `/courses` | any logged-in | Catalog (students: enroll; instructors/admin: manage) |
| `/notifications` | any logged-in | Notifications, mark as read |
| `/my-learning` | STUDENT | Enrollments joined with progress (CQRS read model) |
| `/learn/:courseId` | STUDENT | Modules/lessons, mark complete, assessment, event timeline, cancel |
| `/certificates` | STUDENT | Certificates, PDF download, verify link |
| `/instructor` | INSTRUCTOR/ADMIN | My courses |
| `/instructor/courses/new`, `/instructor/courses/:id/edit` | INSTRUCTOR/ADMIN | Course form |
| `/manage/:courseId` | INSTRUCTOR/ADMIN | Modules & lessons, assessment & questions, students' progress |
| `/admin` | ADMIN | Stats + "Rebuild progress read model" |
| `/admin/users` | ADMIN | Users list, change role, delete |

## Notes
* The JWT and user are kept in `localStorage`; an axios interceptor adds `Authorization: Bearer …`. Any 401 logs out and redirects to `/login`.
* Progress is updated **asynchronously** by events (RabbitMQ → progress-service). After enrolling, completing a module or
  submitting an assessment the UI re-fetches progress after ~1.5 s and ~4 s and polls while progress is still initialising.
* The **Event timeline** on the course page shows the event store for aggregate `<studentId>-<courseId>`
  (`GET /api/progress/events/{aggregateId}`), i.e. the Event Sourcing history behind the progress read model.
* The unread notification badge polls every 10 s.

## Structure

```
src/
  api/          client.js (axios + interceptors) and one module per service
  context/      AuthContext (JWT/session), ToastContext (error/success toasts)
  components/   Navbar, ProtectedRoute, ProgressBar, StatusChip, EventTimeline, AssessmentPanel, …
  pages/        one file per route
  styles.css    all styling (light theme, responsive)
```
