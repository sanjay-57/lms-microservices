import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { assessmentsApi } from '../api/assessments';
import { contentApi } from '../api/content';
import { coursesApi } from '../api/courses';
import { enrollmentsApi } from '../api/enrollments';
import { progressApi } from '../api/progress';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import AssessmentPanel from '../components/AssessmentPanel';
import EmptyState from '../components/EmptyState';
import EventTimeline from '../components/EventTimeline';
import LessonView from '../components/LessonView';
import ProgressBar from '../components/ProgressBar';
import Spinner from '../components/Spinner';
import StatusChip from '../components/StatusChip';
import { aggregateId, fmtDate, fmtPrice, is404 } from '../utils';

function pickEnrollment(list, courseId) {
  const mine = list.filter((e) => e.courseId === courseId);
  return mine.find((e) => e.status === 'ACTIVE') || mine.find((e) => e.status === 'COMPLETED') ||
    mine.sort((a, b) => new Date(b.enrollmentDate) - new Date(a.enrollmentDate))[0] || null;
}

export default function CoursePage() {
  const { courseId: cid } = useParams();
  const courseId = Number(cid);
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const aggId = aggregateId(user.id, courseId);

  const [course, setCourse] = useState(null);
  const [notFound, setNotFound] = useState(false);
  const [enrollment, setEnrollment] = useState(undefined);
  const [modules, setModules] = useState([]);
  const [completed, setCompleted] = useState(new Set());
  const [lessons, setLessons] = useState({});
  const [expanded, setExpanded] = useState(new Set());
  const [progress, setProgress] = useState(null);
  const [events, setEvents] = useState([]);
  const [eventsLoading, setEventsLoading] = useState(false);
  const [assessments, setAssessments] = useState([]);
  const [busyModule, setBusyModule] = useState(null);
  const [busy, setBusy] = useState(false);
  const timers = useRef([]);

  const later = (fn, ms) => { timers.current.push(setTimeout(fn, ms)); };
  useEffect(() => () => timers.current.forEach(clearTimeout), []);

  const loadProgress = useCallback(async () => {
    setEventsLoading(true);
    const [p, ev] = await Promise.all([
      progressApi.one(user.id, courseId).catch((err) => { if (!is404(err)) console.warn(err); return null; }),
      progressApi.events(aggId).catch(() => []),
    ]);
    setProgress(p);
    setEvents(Array.isArray(ev) ? ev : []);
    setEventsLoading(false);
    return p;
  }, [user.id, courseId, aggId]);

  const loadCompleted = useCallback(async () => {
    const ids = await contentApi.completedModules(courseId).catch(() => []);
    setCompleted(new Set((ids || []).map(Number)));
  }, [courseId]);

  const loadEnrollment = useCallback(async () => {
    const list = await enrollmentsApi.byStudent(user.id).catch(() => []);
    const e = pickEnrollment(list, courseId);
    setEnrollment(e);
    return e;
  }, [user.id, courseId]);

  // Events are processed asynchronously, so refresh a couple of times after each action.
  const refreshSoon = useCallback((alsoEnrollment = false) => {
    [1500, 4000].forEach((ms) => later(() => { loadProgress(); if (alsoEnrollment) loadEnrollment(); }, ms));
  }, [loadProgress, loadEnrollment]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const c = await coursesApi.get(courseId);
        if (cancelled) return;
        setCourse(c);
      } catch (err) {
        if (is404(err)) setNotFound(true); else toast.apiError(err);
        return;
      }
      const [mods, ass] = await Promise.all([
        contentApi.modulesByCourse(courseId).catch(() => []),
        assessmentsApi.byCourse(courseId).catch(() => []),
      ]);
      if (cancelled) return;
      setModules([...mods].sort((a, b) => a.sequenceNumber - b.sequenceNumber));
      setAssessments(ass);
      const e = await loadEnrollment();
      if (e && e.status !== 'CANCELLED') {
        loadCompleted();
        // Poll until the progress read model has been initialised (after enroll it lags a little).
        let attempts = 0;
        const poll = async () => {
          if (cancelled) return;
          const p = await loadProgress();
          if (!p && e.status === 'ACTIVE' && attempts < 8) { attempts += 1; later(poll, 2000); }
          else if (p && attempts > 0) loadEnrollment(); // saga compensation may have cancelled it
        };
        poll();
      } else {
        loadProgress();
      }
    })();
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId]);

  const toggleModule = async (m) => {
    const next = new Set(expanded);
    if (next.has(m.id)) next.delete(m.id); else next.add(m.id);
    setExpanded(next);
    if (!lessons[m.id]) {
      try {
        const ls = await contentApi.lessonsByModule(m.id);
        setLessons((prev) => ({ ...prev, [m.id]: ls }));
      } catch (err) {
        toast.apiError(err);
      }
    }
  };

  const completeModule = async (m) => {
    setBusyModule(m.id);
    try {
      const res = await contentApi.completeModule(m.id);
      setCompleted((prev) => new Set([...prev, m.id]));
      toast.success(`“${m.title}” completed (${res.completedModules}/${res.totalModules}). Updating progress…`);
      refreshSoon(true);
    } catch (err) {
      toast.apiError(err);
      loadCompleted();
    } finally {
      setBusyModule(null);
    }
  };

  const enroll = async () => {
    setBusy(true);
    try {
      await enrollmentsApi.enroll(courseId);
      toast.success('Enrolled! Initialising progress…');
      const e = await loadEnrollment();
      loadCompleted();
      let attempts = 0;
      const poll = async () => {
        const p = await loadProgress();
        if (!p && attempts < 8) { attempts += 1; later(poll, 2000); } else loadEnrollment();
      };
      if (e) later(poll, 1000);
    } catch (err) {
      toast.apiError(err);
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    if (!enrollment || !window.confirm('Cancel your enrollment in this course?')) return;
    setBusy(true);
    try {
      await enrollmentsApi.cancel(enrollment.id);
      toast.info('Enrollment cancelled.');
      navigate('/my-learning');
    } catch (err) {
      toast.apiError(err);
    } finally {
      setBusy(false);
    }
  };

  if (notFound) return <EmptyState icon="🔍" title="Course not found">It may be unpublished. <Link to="/courses">Back to catalog</Link></EmptyState>;
  if (!course || enrollment === undefined) return <Spinner label="Loading course…" />;

  const enrolled = enrollment && enrollment.status !== 'CANCELLED';
  const active = enrollment?.status === 'ACTIVE';
  const courseDone = progress?.status === 'COMPLETED' || enrollment?.status === 'COMPLETED';
  const failed = progress?.status === 'FAILED';
  const allModulesDone = modules.length > 0 && modules.every((m) => completed.has(m.id));
  const initialising = active && !progress;
  const pctValue = progress ? progress.percentage : modules.length ? (completed.size / modules.length) * 100 : 0;

  return (
    <div className="course-page">
      <Link to="/my-learning" className="back-link">← My Learning</Link>
      <div className={`course-hero cover-${(course.id % 6) + 1}`}>
        <div className="course-hero-text">
          <span className="course-cat light">{course.category}</span>
          <h1>{course.title}</h1>
          <p>{course.description}</p>
          <div className="course-hero-meta">⏱ {course.durationHours} h · {fmtPrice(course.price)} · {modules.length} modules</div>
        </div>
      </div>

      {courseDone && (
        <div className="banner banner-success">
          <span className="banner-icon">🎓</span>
          <div>
            <strong>Congratulations — you completed this course!</strong>
            <div className="small">Your certificate is generated automatically by certificate-service from the CourseCompleted event.</div>
          </div>
          <Link to="/certificates" className="btn btn-success">View certificates</Link>
        </div>
      )}
      {failed && (
        <div className="banner banner-error">
          <span className="banner-icon">⛔</span>
          <div><strong>Enrollment failed</strong><div className="small">Progress could not be initialised (e.g. no modules), so the saga compensated by cancelling the enrollment.</div></div>
        </div>
      )}

      {!enrolled ? (
        <div className="card enroll-cta">
          <div>
            <h3>{enrollment?.status === 'CANCELLED' ? 'Your enrollment was cancelled' : 'You are not enrolled yet'}</h3>
            <p className="muted">Enroll to unlock module completion, the assessment and certificates.</p>
          </div>
          <button className="btn btn-primary" onClick={enroll} disabled={busy || course.status !== 'PUBLISHED'}>{busy ? 'Enrolling…' : 'Enroll now'}</button>
        </div>
      ) : (
        <div className="card progress-card">
          <div className="progress-card-head">
            <div>
              <h3>Your progress</h3>
              <div className="chips">
                <StatusChip status={enrollment.status} />
                {progress ? <StatusChip status={progress.status} /> : initialising && <StatusChip status="INITIALISING" />}
              </div>
            </div>
            <div className="progress-card-stats">
              <div><span className="big">{progress ? `${progress.completedModules}/${progress.totalModules}` : `${completed.size}/${modules.length}`}</span><span className="muted small">modules</span></div>
              <div><span className="big">{progress?.bestScore != null ? `${Number(progress.bestScore).toFixed(0)}%` : '—'}</span><span className="muted small">best score</span></div>
              <div><span className="big">{progress?.assessmentPassed ? '✓' : '—'}</span><span className="muted small">passed</span></div>
            </div>
          </div>
          <ProgressBar value={pctValue} size="lg" />
          <div className="muted small progress-foot">
            {initialising ? <><span className="spinner sm" /> Initialising progress from the StudentEnrolled event…</> :
              `Enrolled ${fmtDate(enrollment.enrollmentDate)}${progress?.updatedAt ? ` · read model updated ${fmtDate(progress.updatedAt, true)}` : ''}`}
            <span className="spacer" />
            <button className="link-btn" onClick={() => { loadProgress(); loadEnrollment(); }}>↻ Refresh</button>
            {active && <button className="link-btn danger" onClick={cancel} disabled={busy}>Cancel enrollment</button>}
          </div>
        </div>
      )}

      <div className="course-layout">
        <div className="course-main">
          <h2 className="section-title">Modules</h2>
          {modules.length === 0 ? <EmptyState icon="🧱" title="No modules yet" /> : (
            <div className="modules">
              {modules.map((m) => {
                const done = completed.has(m.id);
                const open = expanded.has(m.id);
                return (
                  <div key={m.id} className={`module card ${done ? 'done' : ''}`}>
                    <button className="module-head" onClick={() => toggleModule(m)} aria-expanded={open}>
                      <span className={`module-seq ${done ? 'done' : ''}`}>{done ? '✓' : m.sequenceNumber}</span>
                      <span className="module-title">
                        <strong>{m.title}</strong>
                        {m.description && <span className="muted small">{m.description}</span>}
                      </span>
                      <span className="chev">{open ? '▾' : '▸'}</span>
                    </button>
                    {open && (
                      <div className="module-body">
                        {!lessons[m.id] ? <Spinner inline label="Loading lessons…" /> :
                          lessons[m.id].length === 0 ? <p className="muted small">No lessons in this module.</p> :
                            lessons[m.id].map((l, i) => <LessonView key={l.id} lesson={l} index={i} />)}
                      </div>
                    )}
                    {enrolled && (
                      <div className="module-foot">
                        <button className={`btn btn-sm ${done ? 'btn-success-soft' : 'btn-outline'}`}
                          disabled={done || !active || busyModule === m.id} onClick={() => completeModule(m)}>
                          {done ? '✓ Completed' : busyModule === m.id ? 'Saving…' : 'Mark module complete'}
                        </button>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}

          <h2 className="section-title">Assessment</h2>
          {assessments.length === 0 ? <p className="muted">This course has no assessment.</p> :
            assessments.map((a) => (
              <AssessmentPanel key={a.id} assessment={a}
                canTake={active}
                lockedReason={!enrolled ? 'Enroll to take the assessment.' : !active ? 'Assessment is closed for this enrollment.' : null}
                bestScore={progress?.bestScore} passed={progress?.assessmentPassed}
                onSubmitted={() => refreshSoon(true)} />
            ))}
          {active && !allModulesDone && assessments.length > 0 && (
            <p className="muted small">Tip: the course completes when all modules are done <em>and</em> the assessment is passed (in any order).</p>
          )}
        </div>

        <aside className="course-side">
          <EventTimeline events={events} loading={eventsLoading} onRefresh={loadProgress} aggregateId={aggId} />
        </aside>
      </div>
    </div>
  );
}
