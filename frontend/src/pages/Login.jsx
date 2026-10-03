import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { homePathFor, useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

const DEMO = [
  ['Student', 'student1@example.com', 'Student@123'],
  ['Instructor', 'instructor@example.com', 'Instructor@123'],
  ['Admin', 'admin@example.com', 'Admin@123'],
];

export default function Login() {
  const { login, isAuthenticated, role } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: '', password: '' });
  const [busy, setBusy] = useState(false);

  if (isAuthenticated) return <Navigate to={homePathFor(role)} replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const u = await login(form.email.trim(), form.password);
      toast.success(`Welcome back, ${u.name}!`);
      navigate(location.state?.from || homePathFor(u.role), { replace: true });
    } catch (err) {
      toast.apiError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Log in</h1>
        <p className="muted">Access your courses, progress and certificates.</p>
        <label className="field">
          <span>Email</span>
          <input type="email" required autoComplete="username" value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })} />
        </label>
        <label className="field">
          <span>Password</span>
          <input type="password" required autoComplete="current-password" value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })} />
        </label>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Signing in…' : 'Log in'}</button>
        <div className="demo-fill">
          <span className="muted small">Quick fill:</span>
          {DEMO.map(([r, e, p]) => (
            <button type="button" key={e} className="btn btn-ghost btn-sm" onClick={() => setForm({ email: e, password: p })}>{r}</button>
          ))}
        </div>
        <p className="small center">No account? <Link to="/register">Register</Link></p>
      </form>
    </div>
  );
}
