import { useState } from 'react';
import { fmtDate } from '../utils';

const META = {
  ProgressInitialized: { icon: '🚀', tone: 'blue', title: 'Progress initialised' },
  StudentEnrolled: { icon: '📝', tone: 'blue', title: 'Student enrolled' },
  ModuleCompleted: { icon: '📘', tone: 'indigo', title: 'Module completed' },
  AssessmentCompleted: { icon: '🧠', tone: 'amber', title: 'Assessment submitted' },
  AssessmentRecorded: { icon: '🧠', tone: 'amber', title: 'Assessment recorded' },
  AssessmentPassed: { icon: '🏅', tone: 'green', title: 'Assessment passed' },
  AssessmentFailed: { icon: '⚠️', tone: 'red', title: 'Assessment failed' },
  CourseCompleted: { icon: '🎓', tone: 'green', title: 'Course completed' },
  EnrollmentFailed: { icon: '⛔', tone: 'red', title: 'Enrollment failed (saga compensation)' },
  ProgressFailed: { icon: '⛔', tone: 'red', title: 'Progress failed' },
};

function humanise(type) {
  return String(type || 'Event').replace(/([a-z])([A-Z])/g, '$1 $2');
}

/** A one-line summary built from whatever the event data contains. */
function summary(type, d = {}) {
  if (!d || typeof d !== 'object') return null;
  const parts = [];
  if (d.moduleTitle) parts.push(`“${d.moduleTitle}”`);
  if (d.completedModules != null && d.totalModules != null) parts.push(`${d.completedModules}/${d.totalModules} modules`);
  else if (d.totalModules != null) parts.push(`${d.totalModules} modules in course`);
  if (d.percentage != null && type !== 'ModuleCompleted') parts.push(`${Number(d.percentage).toFixed(0)}%`);
  if (d.score != null && d.totalMarks != null) parts.push(`score ${d.score}/${d.totalMarks}`);
  if (d.passed != null) parts.push(d.passed ? 'passed' : 'not passed');
  if (d.finalScore != null) parts.push(`final score ${Number(d.finalScore).toFixed(0)}%`);
  if (d.reason) parts.push(`reason: ${d.reason}`);
  return parts.join(' · ') || null;
}

export default function EventTimeline({ events, loading, onRefresh, aggregateId }) {
  const [openRaw, setOpenRaw] = useState(null);
  const sorted = [...(events || [])].sort((a, b) => (a.version ?? 0) - (b.version ?? 0));

  return (
    <div className="card timeline-card">
      <div className="card-head">
        <div>
          <h3>Event timeline</h3>
          <p className="muted small">
            Event-sourced history of aggregate <code>{aggregateId}</code>. The progress read model is a
            projection of these immutable events.
          </p>
        </div>
        {onRefresh && (
          <button className="btn btn-ghost btn-sm" onClick={onRefresh} disabled={loading}>
            {loading ? 'Refreshing…' : '↻ Refresh'}
          </button>
        )}
      </div>

      {sorted.length === 0 ? (
        <p className="muted small">{loading ? 'Loading events…' : 'No events yet. Events appear here as they are processed asynchronously.'}</p>
      ) : (
        <ol className="timeline">
          {sorted.map((e, i) => {
            const m = META[e.eventType] || { icon: '•', tone: 'gray', title: humanise(e.eventType) };
            const key = `${e.version}-${i}`;
            const s = summary(e.eventType, e.data);
            return (
              <li key={key} className={`tl-item tl-${m.tone}`}>
                <span className="tl-dot" aria-hidden="true">{m.icon}</span>
                <div className="tl-body">
                  <div className="tl-row">
                    <strong>{m.title}</strong>
                    <span className="tl-version">v{e.version}</span>
                  </div>
                  <div className="tl-meta">
                    <code>{e.eventType}</code> · {fmtDate(e.createdAt, true)}
                  </div>
                  {s && <div className="tl-summary">{s}</div>}
                  {e.data && (
                    <button className="link-btn small" onClick={() => setOpenRaw(openRaw === key ? null : key)}>
                      {openRaw === key ? 'Hide payload' : 'Show payload'}
                    </button>
                  )}
                  {openRaw === key && <pre className="tl-raw">{JSON.stringify(e.data, null, 2)}</pre>}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}
