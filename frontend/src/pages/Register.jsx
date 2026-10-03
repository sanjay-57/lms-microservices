import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { usersApi } from '../api/users';
import { homePathFor, useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';

export default function Register() {
  const { login } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: '', email: '', password: '', role: 'STUDENT' });
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      await usersApi.register({ ...form, email: form.email.trim() });
      const u = await login(form.email.trim(), form.password);
      toast.success('Account created – you are now logged in.');
      navigate(homePathFor(u.role), { replace: true });
    } catch (err) {
      toast.apiError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Create account</h1>
        <p className="muted">Join as a student to learn, or as an instructor to teach.</p>
        <label className="field"><span>Full name</span>
          <input required maxLength={100} value={form.name} onChange={set('name')} />
        </label>
        <label className="field"><span>Email</span>
          <input type="email" required maxLength={150} value={form.email} onChange={set('email')} />
        </label>
        <label className="field"><span>Password</span>
          <input type="password" required minLength={6} maxLength={100} value={form.password} onChange={set('password')} />
          <small className="muted">At least 6 characters.</small>
        </label>
        <div className="field">
          <span>I am a…</span>
          <div className="segmented">
            {['STUDENT', 'INSTRUCTOR'].map((r) => (
              <button type="button" key={r} className={form.role === r ? 'active' : ''} onClick={() => setForm({ ...form, role: r })}>
                {r === 'STUDENT' ? '🎒 Student' : '🧑‍🏫 Instructor'}
              </button>
            ))}
          </div>
        </div>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        <p className="small center">Already registered? <Link to="/login">Log in</Link></p>
      </form>
    </div>
  );
}
