import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { coursesApi } from '../api/courses';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import Spinner from '../components/Spinner';

const EMPTY = { title: '', description: '', category: '', durationHours: '', price: '', status: 'DRAFT', instructorId: '' };

export default function CourseForm() {
  const { courseId } = useParams();
  const editing = !!courseId;
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [form, setForm] = useState(EMPTY);
  const [loading, setLoading] = useState(editing);
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  useEffect(() => {
    if (!editing) return;
    coursesApi.get(courseId).then((c) => {
      setForm({
        title: c.title || '', description: c.description || '', category: c.category || '',
        durationHours: c.durationHours ?? '', price: c.price ?? '', status: c.status || 'DRAFT',
        instructorId: c.instructorId ?? '',
      });
    }).catch((err) => toast.apiError(err)).finally(() => setLoading(false));
  }, [editing, courseId, toast]);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    const body = {
      title: form.title.trim(),
      description: form.description.trim() || null,
      category: form.category.trim(),
      durationHours: Number(form.durationHours),
      price: Number(form.price),
      status: form.status,
      instructorId: user.role === 'ADMIN' && form.instructorId ? Number(form.instructorId) : null,
    };
    try {
      const saved = editing ? await coursesApi.update(courseId, body) : await coursesApi.create(body);
      toast.success(editing ? 'Course updated.' : 'Course created — now add modules and an assessment.');
      navigate(`/manage/${saved.id}`);
    } catch (err) {
      toast.apiError(err);
    } finally {
      setBusy(false);
    }
  };

  if (loading) return <Spinner />;

  return (
    <div className="narrow">
      <Link to="/instructor" className="back-link">← My courses</Link>
      <form className="card form-card" onSubmit={submit}>
        <h1>{editing ? 'Edit course' : 'Create a course'}</h1>
        <label className="field"><span>Title *</span>
          <input required maxLength={200} value={form.title} onChange={set('title')} placeholder="e.g. Java Spring Boot Microservices" />
        </label>
        <label className="field"><span>Description</span>
          <textarea rows={4} maxLength={5000} value={form.description} onChange={set('description')} />
        </label>
        <div className="form-grid">
          <label className="field"><span>Category *</span>
            <input required maxLength={100} value={form.category} onChange={set('category')} placeholder="Programming" />
          </label>
          <label className="field"><span>Duration (hours) *</span>
            <input type="number" required min={1} step={1} value={form.durationHours} onChange={set('durationHours')} />
          </label>
          <label className="field"><span>Price (₹) *</span>
            <input type="number" required min={0} step="0.01" value={form.price} onChange={set('price')} />
          </label>
          <label className="field"><span>Status</span>
            <select value={form.status} onChange={set('status')}>
              <option value="DRAFT">Draft</option>
              <option value="PUBLISHED">Published</option>
              <option value="ARCHIVED">Archived</option>
            </select>
          </label>
          {user.role === 'ADMIN' && (
            <label className="field"><span>Instructor id (admin only)</span>
              <input type="number" min={1} value={form.instructorId} onChange={set('instructorId')} placeholder="defaults to you" />
            </label>
          )}
        </div>
        <p className="muted small">Students only see <strong>Published</strong> courses, and can enroll only in those.</p>
        <div className="row gap end">
          <Link to="/instructor" className="btn btn-ghost">Cancel</Link>
          <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : editing ? 'Save changes' : 'Create course'}</button>
        </div>
      </form>
    </div>
  );
}
