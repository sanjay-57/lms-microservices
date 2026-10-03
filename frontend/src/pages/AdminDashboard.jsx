import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { coursesApi } from '../api/courses';
import { progressApi } from '../api/progress';
import { usersApi } from '../api/users';
import { useToast } from '../context/ToastContext';

export default function AdminDashboard() {
  const toast = useToast();
  const [users, setUsers] = useState(null);
  const [courses, setCourses] = useState(null);
  const [rebuilding, setRebuilding] = useState(false);
  const [rebuild, setRebuild] = useState(null);

  useEffect(() => {
    usersApi.list().then(setUsers).catch(() => setUsers([]));
    coursesApi.list().then(setCourses).catch(() => setCourses([]));
  }, []);

  const runRebuild = async () => {
    if (!window.confirm('Drop the progress read model and replay every stored event?')) return;
    setRebuilding(true);
    const started = performance.now();
    try {
      const res = await progressApi.rebuild();
      setRebuild({ ...res, ms: Math.round(performance.now() - started), at: new Date() });
      toast.success(`Read model rebuilt: ${res.aggregates} aggregates from ${res.events} events.`);
    } catch (err) {
      toast.apiError(err);
    } finally {
      setRebuilding(false);
    }
  };

  const byRole = (r) => (users || []).filter((u) => u.role === r).length;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Admin dashboard</h1>
          <p className="muted">System overview and operational tools.</p>
        </div>
      </div>
      <div className="stats">
        <div className="stat card"><span className="stat-value">{users ? users.length : '…'}</span><span className="stat-label">Users</span></div>
        <div className="stat card"><span className="stat-value">{users ? byRole('STUDENT') : '…'}</span><span className="stat-label">Students</span></div>
        <div className="stat card"><span className="stat-value">{users ? byRole('INSTRUCTOR') : '…'}</span><span className="stat-label">Instructors</span></div>
        <div className="stat card"><span className="stat-value">{courses ? courses.length : '…'}</span><span className="stat-label">Courses</span></div>
      </div>

      <div className="grid two">
        <div className="card">
          <h3>🔁 Rebuild progress read model</h3>
          <p className="muted small">
            Demonstrates <strong>Event Sourcing + CQRS</strong>: progress-service truncates its query-side tables and
            replays every event in the event store to reconstruct each student's progress. The result should be
            identical to the current state.
          </p>
          <button className="btn btn-primary" onClick={runRebuild} disabled={rebuilding}>
            {rebuilding ? 'Replaying events…' : 'Rebuild progress read model'}
          </button>
          {rebuild && (
            <div className="rebuild-result">
              <div><span className="big">{rebuild.aggregates}</span><span className="muted small">aggregates rebuilt</span></div>
              <div><span className="big">{rebuild.events}</span><span className="muted small">events replayed</span></div>
              <div><span className="big">{rebuild.ms} ms</span><span className="muted small">round-trip</span></div>
            </div>
          )}
        </div>
        <div className="card">
          <h3>Quick links</h3>
          <ul className="quick-links">
            <li><Link to="/admin/users">👥 Manage users</Link></li>
            <li><Link to="/courses">📚 All courses (incl. drafts)</Link></li>
            <li><Link to="/instructor/courses/new">➕ Create a course</Link></li>
            <li><Link to="/verify">✅ Verify a certificate</Link></li>
          </ul>
        </div>
      </div>
    </div>
  );
}
