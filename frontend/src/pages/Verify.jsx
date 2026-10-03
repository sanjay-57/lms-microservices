import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { certificatesApi } from '../api/certificates';
import { errorMessage } from '../api/client';
import Spinner from '../components/Spinner';
import { fmtDate, is404 } from '../utils';

export default function Verify() {
  const { code } = useParams();
  const navigate = useNavigate();
  const [input, setInput] = useState(code || '');
  const [state, setState] = useState({ loading: false, result: null, error: null });

  useEffect(() => {
    setInput(code || '');
    if (!code) { setState({ loading: false, result: null, error: null }); return; }
    let cancelled = false;
    setState({ loading: true, result: null, error: null });
    certificatesApi.verify(code)
      .then((r) => !cancelled && setState({ loading: false, result: r, error: null }))
      .catch(async (err) => {
        if (cancelled) return;
        if (is404(err)) setState({ loading: false, result: { valid: false }, error: null });
        else setState({ loading: false, result: null, error: await errorMessage(err) });
      });
    return () => { cancelled = true; };
  }, [code]);

  const submit = (e) => {
    e.preventDefault();
    const c = input.trim();
    if (c) navigate(`/verify/${encodeURIComponent(c)}`);
  };

  const r = state.result;
  return (
    <div className="verify-page">
      <div className="page-head center">
        <h1>Verify a certificate</h1>
        <p className="muted">Anyone can check whether a certificate is genuine — no login required.</p>
      </div>
      <form className="verify-form card" onSubmit={submit}>
        <input placeholder="Enter verification code" value={input} onChange={(e) => setInput(e.target.value)} />
        <button className="btn btn-primary">Verify</button>
      </form>

      {state.loading && <Spinner label="Checking certificate…" />}
      {state.error && <div className="alert alert-error">{state.error}</div>}
      {r && (r.valid ? (
        <div className="card verify-result valid">
          <div className="verify-seal">✓</div>
          <h2>Valid certificate</h2>
          <p className="muted">This certificate was issued by LearnHub LMS.</p>
          <dl className="kv">
            <dt>Certificate no.</dt><dd><code>{r.certificateNumber}</code></dd>
            <dt>Awarded to</dt><dd><strong>{r.studentName}</strong></dd>
            <dt>Course</dt><dd>{r.courseName}</dd>
            <dt>Completed</dt><dd>{fmtDate(r.completionDate)}</dd>
            <dt>Issued</dt><dd>{fmtDate(r.issueDate)}</dd>
            {r.finalScore != null && (<><dt>Final score</dt><dd>{Number(r.finalScore).toFixed(1)}%</dd></>)}
          </dl>
        </div>
      ) : (
        <div className="card verify-result invalid">
          <div className="verify-seal">✕</div>
          <h2>Not valid</h2>
          <p className="muted">No certificate matches the code <code>{code}</code>.</p>
        </div>
      ))}
    </div>
  );
}
