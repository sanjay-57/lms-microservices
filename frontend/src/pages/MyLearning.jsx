import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { coursesApi } from '../api/courses';
import { enrollmentsApi } from '../api/enrollments';
import { progressApi } from '../api/progress';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import EmptyState from '../components/EmptyState';
import ProgressBar from '../components/ProgressBar';
import Spinner from '../components/Spinner';
import StatusChip from '../components/StatusChip';
import { fmtDate } from '../utils';

const MAX_POLLS = 8;

export default function MyLearning() {
  const { user } = useAuth();
  const toast = useToast();
  const [rows, setRows] = useState(null);
  const [filter, setFilter] = useState('current');
  const [progressDown, setProgressDown] = useState(false);
  const polls = useRef(0);
  const courseCache = useRef(new Map());

  const courseFor = useCallback(async (id) => {
    if (courseCache.current.has(id)) return courseCache.current.get(id);
    const c = await coursesApi.get(id).catch(() => null);
    courseCache.current.set(id, c);
    return c;
  }, []);

  const load = useCallback(async () => {
    try {
      const [enrollments, progress] = await Promise.all([
        enrollmentsApi.byStudent(user.id),
        progressApi.byStudent(user.id).then((p) => { setProgressDown(false); return p; })
          .catch(() => { setProgressDown(true); return []; }),
      ]);
      const courses = await Promise.all(enrollments.map((e) => courseFor(e.courseId)));
      const joined = enrollments.map((e, i) => ({
        enrollment: e,
        course: courses[i],
        progress: progress.find((p) => p.enrollmentId === e.id) ||
          progress.find((p) => p.courseId === e.courseId) || null,
      })).sort((a, b) => new Date(b.enrollment.enrollmentDate) - new Date(a.enrollment.enrollmentDate));
      setRows(joined);
      return joined;
    } catch (err) {
      toast.apiError(err);
      setRows([]);
      return [];
    }
  }, [user.id, courseFor, toast]);

  // Progress is initialised asynchronously (StudentEnrolled → progress-service). Poll briefly while missing.
  useEffect(() => {
    let timer;
    let cancelled = false;
    const tick = async () => {
      const r = await load();
      if (cancelled) return;
      const waiting = r.some((x) => x.enrollment.status === 'ACTIVE' && !x.progress);
      if (waiting && polls.current < MAX_POLLS) {
        polls.current += 1;
        timer = setTimeout(tick, 2000);
      }
    };
    tick();
    return () => { cancelled = true; clearTimeout(timer); };
  }, [load]);

  if (rows === null) return <Spinner label="Loading your courses…" />;

  const visible = rows.filter((r) =>
    filter === 'all' ? true :
      filter === 'completed' ? r.enrollment.status === 'COMPLETED' :
        filter === 'cancelled' ? r.enrollment.status === 'CANCELLED' :
          r.enrollment.status === 'ACTIVE');

  const stats = {
    active: rows.filter((r) => r.enrollment.status === 'ACTIVE').length,
    completed: rows.filter((r) => r.enrollment.status === 'COMPLETED').length,
    avg: (() => {
      const ps = rows.filter((r) => r.enrollment.status === 'ACTIVE' && r.progress).map((r) => Number(r.progress.percentage) || 0);
      return ps.length ? Math.round(ps.reduce((a, b) => a + b, 0) / ps.length) : 0;
    })(),
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>My Learning</h1>
          <p className="muted">Hi {user.name.split(' ')[0]} — pick up where you left off.</p>
        </div>
        <Link to="/courses" className="btn btn-primary">Browse catalog</Link>
      </div>

      <div className="stats">
        <div className="stat card"><span className="stat-value">{stats.active}</span><span className="stat-label">Active courses</span></div>
        <div className="stat card"><span className="stat-value">{stats.completed}</span><span className="stat-label">Completed</span></div>
        <div className="stat card"><span className="stat-value">{stats.avg}%</span><span className="stat-label">Avg. progress (active)</span></div>
      </div>

      {progressDown && <div className="alert alert-warn">Progress service is not reachable right now — showing enrollments only.</div>}

      <div className="tabs">
        {[['current', 'In progress'], ['completed', 'Completed'], ['cancelled', 'Cancelled'], ['all', 'All']].map(([k, l]) => (
          <button key={k} className={filter === k ? 'active' : ''} onClick={() => setFilter(k)}>{l}</button>
        ))}
      </div>

      {visible.length === 0 ? (
        <EmptyState icon="🎒" title="Nothing here yet">
          {filter === 'current' ? <>Enroll in a course from the <Link to="/courses">catalog</Link>.</> : 'No courses in this list.'}
        </EmptyState>
      ) : (
        <div className="list">
          {visible.map(({ enrollment: e, course: c, progress: p }) => {
            const initialising = e.status === 'ACTIVE' && !p && !progressDown;
            return (
              <div key={e.id} className="card learning-row">
                <div className={`learning-thumb cover-${(e.courseId % 6) + 1}`}>{(c?.title || p?.courseTitle || '?').charAt(0)}</div>
                <div className="learning-main">
                  <div className="learning-title">
                    <h3>{c?.title || p?.courseTitle || `Course #${e.courseId}`}</h3>
                    <div className="chips">
                      <StatusChip status={e.status} />
                      {p ? <StatusChip status={p.status} /> : initialising && <StatusChip status="INITIALISING" />}
                    </div>
                  </div>
                  <div className="muted small">
                    Enrolled {fmtDate(e.enrollmentDate)}{c?.category ? ` · ${c.category}` : ''}
                    {p && ` · ${p.completedModules}/${p.totalModules} modules`}
                    {p?.bestScore != null && ` · best score ${Number(p.bestScore).toFixed(0)}%`}
                  </div>
                  {p ? <ProgressBar value={p.percentage} /> :
                    initialising ? <div className="muted small initialising"><span className="spinner sm" /> Initialising progress… (processed asynchronously from the StudentEnrolled event)</div> : null}
                </div>
                <div className="learning-actions">
                  {e.status !== 'CANCELLED' && <Link className="btn btn-primary btn-sm" to={`/learn/${e.courseId}`}>{e.status === 'COMPLETED' ? 'Review' : 'Continue'}</Link>}
                  {p?.status === 'COMPLETED' && <Link className="btn btn-ghost btn-sm" to="/certificates">Certificate</Link>}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
