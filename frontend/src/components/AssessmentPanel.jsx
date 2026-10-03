import { useState } from 'react';
import { assessmentsApi } from '../api/assessments';
import { useToast } from '../context/ToastContext';
import Spinner from './Spinner';

const OPTIONS = ['A', 'B', 'C', 'D'];

export default function AssessmentPanel({ assessment, canTake, lockedReason, onSubmitted, bestScore, passed }) {
  const toast = useToast();
  const [questions, setQuestions] = useState(null);
  const [answers, setAnswers] = useState({});
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState(null);

  const start = async () => {
    setLoading(true);
    setResult(null);
    setAnswers({});
    try {
      setQuestions(await assessmentsApi.questions(assessment.id));
    } catch (err) {
      toast.apiError(err);
    } finally {
      setLoading(false);
    }
  };

  const submit = async (e) => {
    e.preventDefault();
    const unanswered = questions.filter((q) => !answers[q.id]).length;
    if (unanswered > 0 && !window.confirm(`${unanswered} question(s) unanswered. Submit anyway?`)) return;
    setSubmitting(true);
    try {
      const res = await assessmentsApi.submit(assessment.id, answers);
      setResult(res);
      setQuestions(null);
      if (res.passed) toast.success(`Passed with ${Number(res.percentage).toFixed(0)}%!`);
      else toast.info(`Scored ${Number(res.percentage).toFixed(0)}% — pass mark is ${Number(assessment.passMark)}%.`);
      onSubmitted?.(res);
    } catch (err) {
      toast.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const answered = questions ? questions.filter((q) => answers[q.id]).length : 0;

  return (
    <div className="card assessment">
      <div className="card-head">
        <div>
          <h3>📝 {assessment.title}</h3>
          <p className="muted small">
            Pass mark {Number(assessment.passMark)}% · total {Number(assessment.totalMarks ?? 0)} marks
            {bestScore != null && <> · your best {Number(bestScore).toFixed(0)}% {passed ? '✓' : ''}</>}
          </p>
        </div>
        {!questions && (
          <button className="btn btn-primary" onClick={start} disabled={!canTake || loading}>
            {loading ? 'Loading…' : result || bestScore != null ? 'Retake assessment' : 'Start assessment'}
          </button>
        )}
      </div>
      {!canTake && lockedReason && !questions && <p className="muted small">{lockedReason}</p>}

      {result && (
        <div className={`result ${result.passed ? 'pass' : 'fail'}`}>
          <div className="result-score">{Number(result.percentage).toFixed(0)}%</div>
          <div>
            <strong>{result.passed ? 'Passed 🎉' : 'Not passed yet'}</strong>
            <div className="small">Score {Number(result.score)} / {Number(result.totalMarks)} · pass mark {Number(assessment.passMark)}%</div>
            <div className="small muted">An AssessmentCompleted event was published — watch the timeline update.</div>
          </div>
        </div>
      )}

      {loading && <Spinner inline />}
      {questions && (
        questions.length === 0 ? <p className="muted">This assessment has no questions yet.</p> : (
          <form onSubmit={submit} className="quiz">
            {questions.map((q, i) => (
              <fieldset key={q.id} className="question">
                <legend><span className="q-num">Q{i + 1}</span> {q.questionText} <span className="muted small">({q.marks} marks)</span></legend>
                <div className="options">
                  {OPTIONS.map((o) => (
                    <label key={o} className={`option ${answers[q.id] === o ? 'selected' : ''}`}>
                      <input type="radio" name={`q-${q.id}`} value={o} checked={answers[q.id] === o}
                        onChange={() => setAnswers({ ...answers, [q.id]: o })} />
                      <span className="opt-letter">{o}</span>
                      <span>{q[`option${o}`]}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
            ))}
            <div className="quiz-foot">
              <span className="muted small">{answered}/{questions.length} answered</span>
              <div className="row gap">
                <button type="button" className="btn btn-ghost" onClick={() => setQuestions(null)}>Cancel</button>
                <button className="btn btn-primary" disabled={submitting || answered === 0}>{submitting ? 'Submitting…' : 'Submit answers'}</button>
              </div>
            </div>
          </form>
        )
      )}
    </div>
  );
}
