import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { certificatesApi } from '../api/certificates';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import EmptyState from '../components/EmptyState';
import Spinner from '../components/Spinner';
import { fmtDate, verifyUrl } from '../utils';

export default function Certificates() {
  const { user } = useAuth();
  const toast = useToast();
  const [certs, setCerts] = useState(null);
  const [downloading, setDownloading] = useState(null);

  useEffect(() => {
    certificatesApi.byStudent(user.id)
      .then(setCerts)
      .catch((err) => { setCerts([]); toast.apiError(err); });
  }, [user.id, toast]);

  const download = async (c) => {
    setDownloading(c.id);
    try {
      await certificatesApi.download(c);
    } catch (err) {
      toast.apiError(err);
    } finally {
      setDownloading(null);
    }
  };

  const copy = async (c) => {
    const url = verifyUrl(c.verificationCode);
    try {
      await navigator.clipboard.writeText(url);
      toast.success('Verification link copied to clipboard.');
    } catch {
      window.prompt('Copy this verification link:', url);
    }
  };

  if (certs === null) return <Spinner label="Loading certificates…" />;

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>My certificates</h1>
          <p className="muted">Issued automatically when you complete a course (CourseCompleted → certificate-service).</p>
        </div>
      </div>
      {certs.length === 0 ? (
        <EmptyState icon="🏆" title="No certificates yet">
          Complete all modules and pass the assessment of a course. <Link to="/my-learning">Go to My Learning</Link>
        </EmptyState>
      ) : (
        <div className="grid certs">
          {certs.map((c) => (
            <div key={c.id} className="card cert">
              <div className="cert-ribbon">🎓</div>
              <div className="cert-label">Certificate of Completion</div>
              <h3>{c.courseName}</h3>
              <p className="muted small">Awarded to <strong>{c.studentName}</strong></p>
              <dl className="kv small">
                <dt>Number</dt><dd><code>{c.certificateNumber}</code></dd>
                <dt>Completed</dt><dd>{fmtDate(c.completionDate)}</dd>
                <dt>Issued</dt><dd>{fmtDate(c.issueDate)}</dd>
                {c.finalScore != null && (<><dt>Final score</dt><dd>{Number(c.finalScore).toFixed(1)}%</dd></>)}
                <dt>Verify code</dt><dd><code>{c.verificationCode}</code></dd>
              </dl>
              <div className="cert-actions">
                <button className="btn btn-primary btn-sm" onClick={() => download(c)} disabled={downloading === c.id}>
                  {downloading === c.id ? 'Downloading…' : '⬇ Download PDF'}
                </button>
                <button className="btn btn-ghost btn-sm" onClick={() => copy(c)}>🔗 Copy verify link</button>
                <Link className="btn btn-ghost btn-sm" to={`/verify/${encodeURIComponent(c.verificationCode)}`} target="_blank">Open verify page ↗</Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
