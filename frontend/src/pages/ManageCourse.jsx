import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { assessmentsApi } from '../api/assessments';
import { contentApi } from '../api/content';
import { coursesApi } from '../api/courses';
import { enrollmentsApi } from '../api/enrollments';
import { progressApi } from '../api/progress';
import { usersApi } from '../api/users';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import EmptyState from '../components/EmptyState';
import EventTimeline from '../components/EventTimeline';
import ProgressBar from '../components/ProgressBar';
import Spinner from '../components/Spinner';
import StatusChip from '../components/StatusChip';
import { aggregateId, fmtDate, fmtPrice } from '../utils';

/* ---------------- Content tab ---------------- */

const EMPTY_LESSON = { title: '', description: '', videoUrl: '', documentUrl: '', durationMinutes: '' };

function LessonForm({ moduleId, onAdded }) {
  const toast = useToast();
  const [f, setF] = useState(EMPTY_LESSON);
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const l = await contentApi.createLesson({
        moduleId,
        title: f.title.trim(),
        description: f.description.trim() || null,
        videoUrl: f.videoUrl.trim() || null,
        documentUrl: f.documentUrl.trim() || null,
        durationMinutes: f.durationMinutes === '' ? null : Number(f.durationMinutes),
      });
      toast.success('Lesson added.');
      setF(EMPTY_LESSON);
      onAdded(l);
    } catch (err) {
      toast.apiError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="inline-form" onSubmit={submit}>
      <div className="form-grid">
        <label className="field"><span>Lesson title *</span><input required maxLength={200} value={f.title} onChange={set('title')} /></label>
        <label className="field"><span>Duration (min)</span><input type="number" min={0} value={f.durationMinutes} onChange={set('durationMinutes')} /></label>
        <label className="field"><span>Video URL</span><input type="url" maxLength={500} value={f.videoUrl} onChange={set('videoUrl')} placeholder="https://www.w3schools.com/html/mov_bbb.mp4" /></label>
        <label className="field"><span>Document URL</span><input type="url" maxLength={500} value={f.documentUrl} onChange={set('documentUrl')} placeholder="https://…/notes.pdf" /></label>
      </div>
      <label className="field"><span>Description</span><input maxLength={5000} value={f.description} onChange={set('description')} /></label>
      <div className="row end"><button className="btn btn-primary btn-sm" disabled={busy}>{busy ? 'Adding…' : '+ Add lesson'}</button></div>
    </form>
  );
}

function ContentTab({ courseId }) {
  const toast = useToast();
  const [modules, setModules] = useState(null);
  const [lessons, setLessons] = useState({});
  const [openForm, setOpenForm] = useState(null);
  const [mf, setMf] = useState({ title: '', description: '', sequenceNumber: '' });
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const mods = [...await contentApi.modulesByCourse(courseId)].sort((a, b) => a.sequenceNumber - b.sequenceNumber);
      setModules(mods);
      const ls = await Promise.all(mods.map((m) => contentApi.lessonsByModule(m.id).catch(() => [])));
      setLessons(Object.fromEntries(mods.map((m, i) => [m.id, ls[i]])));
    } catch (err) {
      setModules([]);
      toast.apiError(err);
    }
  }, [courseId, toast]);

  useEffect(() => { load(); }, [load]);

  const nextSeq = modules && modules.length ? Math.max(...modules.map((m) => m.sequenceNumber)) + 1 : 1;

  const addModule = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      await contentApi.createModule({
        courseId,
        title: mf.title.trim(),
        description: mf.description.trim() || null,
        sequenceNumber: Number(mf.sequenceNumber || nextSeq),
      });
      toast.success('Module added.');
      setMf({ title: '', description: '', sequenceNumber: '' });
      load();
    } catch (err) {
      toast.apiError(err);
    } finally {
      setBusy(false);
    }
  };

  const removeModule = async (m) => {
    if (!window.confirm(`Delete module “${m.title}” and all its lessons?`)) return;
    try { await contentApi.deleteModule(m.id); toast.info('Module deleted.'); load(); } catch (err) { toast.apiError(err); }
  };

  const removeLesson = async (l) => {
    if (!window.confirm(`Delete lesson “${l.title}”?`)) return;
    try {
      await contentApi.deleteLesson(l.id);
      setLessons((prev) => ({ ...prev, [l.moduleId]: prev[l.moduleId].filter((x) => x.id !== l.id) }));
    } catch (err) { toast.apiError(err); }
  };

  if (modules === null) return <Spinner />;

  return (
    <div className="stack">
      {modules.length === 0 && (
        <div className="alert alert-warn">
          This course has no modules. Students who enroll will hit the saga's failure path (progress-service publishes
          <code> EnrollmentFailed</code> and the enrollment is cancelled). Add at least one module.
        </div>
      )}
      {modules.map((m) => (
        <div key={m.id} className="card module-admin">
          <div className="module-admin-head">
            <span className="module-seq">{m.sequenceNumber}</span>
            <div className="grow">
              <strong>{m.title}</strong>
              {m.description && <div className="muted small">{m.description}</div>}
            </div>
            <button className="btn btn-ghost btn-sm" onClick={() => setOpenForm(openForm === m.id ? null : m.id)}>{openForm === m.id ? 'Close' : '+ Lesson'}</button>
            <button className="btn btn-danger-ghost btn-sm" onClick={() => removeModule(m)}>Delete</button>
          </div>
          <ul className="lesson-list">
            {(lessons[m.id] || []).map((l) => (
              <li key={l.id}>
                <span>📄 {l.title}</span>
                <span className="muted small">
                  {l.durationMinutes != null && `${l.durationMinutes} min`} {l.videoUrl && '· 🎬 video'} {l.documentUrl && '· 📎 doc'}
                </span>
                <button className="link-btn danger small" onClick={() => removeLesson(l)}>remove</button>
              </li>
            ))}
            {(lessons[m.id] || []).length === 0 && <li className="muted small">No lessons yet.</li>}
          </ul>
          {openForm === m.id && (
            <LessonForm moduleId={m.id} onAdded={(l) => setLessons((prev) => ({ ...prev, [m.id]: [...(prev[m.id] || []), l] }))} />
          )}
        </div>
      ))}

      <form className="card form-card" onSubmit={addModule}>
        <h3>Add module</h3>
        <div className="form-grid">
          <label className="field"><span>Title *</span><input required maxLength={200} value={mf.title} onChange={(e) => setMf({ ...mf, title: e.target.value })} /></label>
          <label className="field"><span>Sequence #</span><input type="number" min={1} value={mf.sequenceNumber} placeholder={String(nextSeq)} onChange={(e) => setMf({ ...mf, sequenceNumber: e.target.value })} /></label>
        </div>
        <label className="field"><span>Description</span><textarea rows={2} maxLength={5000} value={mf.description} onChange={(e) => setMf({ ...mf, description: e.target.value })} /></label>
        <div className="row end"><button className="btn btn-primary" disabled={busy}>{busy ? 'Adding…' : '+ Add module'}</button></div>
      </form>
    </div>
  );
}

/* ---------------- Assessment tab ---------------- */

const EMPTY_Q = { questionText: '', optionA: '', optionB: '', optionC: '', optionD: '', correctOption: 'A', marks: 20 };

function QuestionForm({ assessmentId, onAdded }) {
  const toast = useToast();
  const [q, setQ] = useState(EMPTY_Q);
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setQ({ ...q, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      const created = await assessmentsApi.addQuestion(assessmentId, { ...q, marks: Number(q.marks) });
      toast.success('Question added.');
      setQ(EMPTY_Q);
      onAdded(created);
    } catch (err) {
      toast.apiError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="inline-form" onSubmit={submit}>
      <label className="field"><span>Question *</span><textarea rows={2} required maxLength={2000} value={q.questionText} onChange={set('questionText')} /></label>
      <div className="form-grid">
        {['A', 'B', 'C', 'D'].map((o) => (
          <label key={o} className="field"><span>Option {o} *</span><input required maxLength={500} value={q[`option${o}`]} onChange={set(`option${o}`)} /></label>
        ))}
        <label className="field"><span>Correct option *</span>
          <select value={q.correctOption} onChange={set('correctOption')}>{['A', 'B', 'C', 'D'].map((o) => <option key={o}>{o}</option>)}</select>
        </label>
        <label className="field"><span>Marks *</span><input type="number" required min={1} value={q.marks} onChange={set('marks')} /></label>
      </div>
      <div className="row end"><button className="btn btn-primary btn-sm" disabled={busy}>{busy ? 'Adding…' : '+ Add question'}</button></div>
    </form>
  );
}

function AssessmentTab({ courseId }) {
  const toast = useToast();
  const [assessments, setAssessments] = useState(null);
  const [questions, setQuestions] = useState({});
  const [form, setForm] = useState({ title: 'Final Assessment', passMark: 60 });
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const list = await assessmentsApi.byCourse(courseId);
      setAssessments(list);
      const qs = await Promise.all(list.map((a) => assessmentsApi.questions(a.id).catch(() => [])));
      setQuestions(Object.fromEntries(list.map((a, i) => [a.id, qs[i]])));
    } catch (err) {
      setAssessments([]);
      toast.apiError(err);
    }
  }, [courseId, toast]);

  useEffect(() => { load(); }, [load]);

  const create = async (e) => {
    e.preventDefault();
    setBusy(true);
    try {
      await assessmentsApi.create({ courseId, title: form.title.trim(), passMark: Number(form.passMark) });
      toast.success('Assessment created — now add questions.');
      load();
    } catch (err) {
      toast.apiError(err);
    } finally {
      setBusy(false);
    }
  };

  if (assessments === null) return <Spinner />;

  return (
    <div className="stack">
      {assessments.map((a) => (
        <div key={a.id} className="card">
          <div className="card-head">
            <div>
              <h3>{a.title}</h3>
              <p className="muted small">Pass mark {Number(a.passMark)}% · {(questions[a.id] || []).length} questions · {Number(a.totalMarks ?? 0)} marks total</p>
            </div>
          </div>
          <ol className="q-list">
            {(questions[a.id] || []).map((q) => (
              <li key={q.id}>
                <div>{q.questionText} <span className="muted small">({q.marks} marks)</span></div>
                <div className="q-opts">
                  {['A', 'B', 'C', 'D'].map((o) => (
                    <span key={o} className={q.correctOption === o ? 'correct' : ''}>{o}. {q[`option${o}`]}</span>
                  ))}
                </div>
              </li>
            ))}
          </ol>
          <details className="add-details">
            <summary>+ Add a question</summary>
            <QuestionForm assessmentId={a.id} onAdded={() => load()} />
          </details>
        </div>
      ))}

      <form className="card form-card" onSubmit={create}>
        <h3>{assessments.length ? 'Create another assessment' : 'Create assessment'}</h3>
        <div className="form-grid">
          <label className="field"><span>Title *</span><input required maxLength={200} value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label>
          <label className="field"><span>Pass mark (%) *</span><input type="number" required min={0} max={100} step="0.01" value={form.passMark} onChange={(e) => setForm({ ...form, passMark: e.target.value })} /></label>
        </div>
        <div className="row end"><button className="btn btn-primary" disabled={busy}>{busy ? 'Creating…' : 'Create assessment'}</button></div>
      </form>
    </div>
  );
}

/* ---------------- Students tab ---------------- */

function StudentsTab({ courseId }) {
  const toast = useToast();
  const [rows, setRows] = useState(null);
  const [progressDown, setProgressDown] = useState(false);
  const [selected, setSelected] = useState(null);
  const [events, setEvents] = useState([]);
  const [evLoading, setEvLoading] = useState(false);

  const load = useCallback(async () => {
    try {
      const [enrollments, progress] = await Promise.all([
        enrollmentsApi.byCourse(courseId),
        progressApi.byCourse(courseId).then((p) => { setProgressDown(false); return p; }).catch(() => { setProgressDown(true); return []; }),
      ]);
      const names = await Promise.all(enrollments.map((e) => {
        const p = progress.find((x) => x.studentId === e.studentId);
        return p?.studentName ? Promise.resolve(p.studentName) : usersApi.get(e.studentId).then((u) => u.name).catch(() => `Student #${e.studentId}`);
      }));
      setRows(enrollments.map((e, i) => ({
        enrollment: e,
        name: names[i],
        progress: progress.find((p) => p.enrollmentId === e.id) || progress.find((p) => p.studentId === e.studentId) || null,
      })));
    } catch (err) {
      setRows([]);
      toast.apiError(err);
    }
  }, [courseId, toast]);

  useEffect(() => { load(); }, [load]);

  const showEvents = async (studentId) => {
    const agg = aggregateId(studentId, courseId);
    if (selected === agg) { setSelected(null); return; }
    setSelected(agg);
    setEvLoading(true);
    setEvents(await progressApi.events(agg).catch(() => []));
    setEvLoading(false);
  };

  if (rows === null) return <Spinner />;
  if (rows.length === 0) return <EmptyState icon="👥" title="No enrollments yet" />;

  return (
    <div className="stack">
      {progressDown && <div className="alert alert-warn">Progress service unreachable — showing enrollments only.</div>}
      <div className="card table-wrap">
        <table className="table">
          <thead><tr><th>Student</th><th>Enrolled</th><th>Enrollment</th><th>Progress</th><th>Modules</th><th>Best score</th><th>Status</th><th /></tr></thead>
          <tbody>
            {rows.map(({ enrollment: e, name, progress: p }) => (
              <tr key={e.id}>
                <td><strong>{name}</strong><div className="muted small">id {e.studentId}</div></td>
                <td>{fmtDate(e.enrollmentDate)}</td>
                <td><StatusChip status={e.status} /></td>
                <td className="w-progress">{p ? <ProgressBar value={p.percentage} size="sm" /> : <span className="muted small">—</span>}</td>
                <td>{p ? `${p.completedModules}/${p.totalModules}` : '—'}</td>
                <td>{p?.bestScore != null ? `${Number(p.bestScore).toFixed(0)}%` : '—'}</td>
                <td>{p ? <StatusChip status={p.status} /> : '—'}</td>
                <td><button className="link-btn small" onClick={() => showEvents(e.studentId)}>{selected === aggregateId(e.studentId, courseId) ? 'Hide' : 'Events'}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {selected && <EventTimeline events={events} loading={evLoading} aggregateId={selected} onRefresh={async () => { setEvLoading(true); setEvents(await progressApi.events(selected).catch(() => [])); setEvLoading(false); }} />}
    </div>
  );
}

/* ---------------- Page ---------------- */

export default function ManageCourse() {
  const { courseId: cid } = useParams();
  const courseId = Number(cid);
  const { user } = useAuth();
  const toast = useToast();
  const [course, setCourse] = useState(null);
  const [tab, setTab] = useState('content');

  useEffect(() => {
    coursesApi.get(courseId).then(setCourse).catch((err) => { toast.apiError(err); setCourse(false); });
  }, [courseId, toast]);

  if (course === null) return <Spinner />;
  if (course === false) return <EmptyState icon="🔍" title="Course not found"><Link to="/instructor">Back</Link></EmptyState>;

  const owner = user.role === 'ADMIN' || course.instructorId === user.id;

  return (
    <div>
      <Link to={user.role === 'ADMIN' ? '/courses' : '/instructor'} className="back-link">← Back</Link>
      <div className="page-head">
        <div>
          <div className="row gap"><h1>{course.title}</h1><StatusChip status={course.status} /></div>
          <p className="muted">{course.category} · {course.durationHours} h · {fmtPrice(course.price)} · instructor #{course.instructorId}</p>
        </div>
        {owner && <Link to={`/instructor/courses/${course.id}/edit`} className="btn btn-outline">Edit details</Link>}
      </div>
      {!owner && <div className="alert alert-warn">You are not the owner of this course — changes will be rejected by the server.</div>}
      <div className="tabs">
        {[['content', '📚 Modules & lessons'], ['assessment', '📝 Assessment'], ['students', '👥 Students & progress']].map(([k, l]) => (
          <button key={k} className={tab === k ? 'active' : ''} onClick={() => setTab(k)}>{l}</button>
        ))}
      </div>
      {tab === 'content' && <ContentTab courseId={courseId} />}
      {tab === 'assessment' && <AssessmentTab courseId={courseId} />}
      {tab === 'students' && <StudentsTab courseId={courseId} />}
    </div>
  );
}
