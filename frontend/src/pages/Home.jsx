import { Link } from 'react-router-dom';
import { homePathFor, useAuth } from '../context/AuthContext';

const FLOW = [
  { n: 1, title: 'Enroll', svc: 'enrollment-service', evt: 'StudentEnrolled', text: 'Enrollment is saved as ACTIVE and an event is published to RabbitMQ after commit.' },
  { n: 2, title: 'Initialise progress', svc: 'progress-service', evt: 'ProgressInitialized', text: 'Progress consumes the event, counts modules and appends to its event store. 0 modules → EnrollmentFailed (saga compensation).' },
  { n: 3, title: 'Learn', svc: 'content-service', evt: 'ModuleCompleted', text: 'Marking a module complete publishes an event; the read model recalculates the percentage.' },
  { n: 4, title: 'Assess', svc: 'assessment-service', evt: 'AssessmentCompleted', text: 'Submitting answers grades the attempt and publishes the score.' },
  { n: 5, title: 'Complete', svc: 'progress-service', evt: 'CourseCompleted', text: '100% modules + passed assessment → CourseCompleted, consumed by enrollment, certificate and notification services.' },
  { n: 6, title: 'Certify', svc: 'certificate-service', evt: 'CertificateGenerated', text: 'A PDF certificate with a public verification code is issued and the student is notified.' },
];

const ACCOUNTS = [
  { role: 'Student', email: 'student1@example.com', password: 'Student@123' },
  { role: 'Instructor', email: 'instructor@example.com', password: 'Instructor@123' },
  { role: 'Admin', email: 'admin@example.com', password: 'Admin@123' },
];

export default function Home() {
  const { isAuthenticated, role, user } = useAuth();
  return (
    <div className="home">
      <section className="hero">
        <div>
          <span className="eyebrow">Scalable Microservices · Mini Project</span>
          <h1>A Learning Management System built from <span className="hl">event-driven microservices</span></h1>
          <p className="lead">
            Nine Spring Boot services behind an API gateway, communicating through RabbitMQ.
            Progress tracking uses <strong>CQRS</strong> and <strong>Event Sourcing</strong>, and course
            completion runs as a <strong>choreographed saga</strong>.
          </p>
          <div className="hero-actions">
            {isAuthenticated ? (
              <Link to={homePathFor(role)} className="btn btn-primary btn-lg">Continue as {user.name}</Link>
            ) : (
              <>
                <Link to="/login" className="btn btn-primary btn-lg">Log in</Link>
                <Link to="/register" className="btn btn-outline btn-lg">Create account</Link>
              </>
            )}
            <Link to="/verify" className="btn btn-ghost btn-lg">Verify a certificate</Link>
          </div>
        </div>
        <div className="hero-card card">
          <h3>Demo accounts</h3>
          <p className="muted small">Seeded on first start (development only).</p>
          <table className="table compact">
            <thead><tr><th>Role</th><th>Email</th><th>Password</th></tr></thead>
            <tbody>
              {ACCOUNTS.map((a) => (
                <tr key={a.email}>
                  <td><strong>{a.role}</strong></td>
                  <td><code>{a.email}</code></td>
                  <td><code>{a.password}</code></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="section-title">How a student's journey flows through the system</h2>
        <div className="flow">
          {FLOW.map((s) => (
            <div key={s.n} className="flow-step card">
              <div className="flow-num">{s.n}</div>
              <h4>{s.title}</h4>
              <div className="flow-svc">{s.svc}</div>
              <code className="flow-evt">{s.evt}</code>
              <p className="small muted">{s.text}</p>
            </div>
          ))}
        </div>
        <p className="muted small center">
          Events are processed asynchronously, so the UI polls briefly after each action. Open a course and watch the
          <strong> Event timeline</strong> grow — that is the event store behind the progress read model.
        </p>
      </section>

      <section className="arch card">
        <h3>Architecture at a glance</h3>
        <div className="arch-grid">
          <div><strong>Gateway :8080</strong><span>JWT check, CORS, routing via Eureka</span></div>
          <div><strong>user · course</strong><span>Identity (JWT issuer) and catalog</span></div>
          <div><strong>enrollment</strong><span>Saga participant; compensation on failure</span></div>
          <div><strong>content · assessment</strong><span>Modules, lessons, quizzes; publish events</span></div>
          <div><strong>progress</strong><span>CQRS read model + event store, rebuildable</span></div>
          <div><strong>certificate · notification</strong><span>PDF + QR verification; in-app/email alerts</span></div>
        </div>
      </section>
    </div>
  );
}
