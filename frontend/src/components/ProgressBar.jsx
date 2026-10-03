import { pct } from '../utils';

export default function ProgressBar({ value, label, size = 'md' }) {
  const v = pct(value);
  return (
    <div className={`progress progress-${size}`}>
      <div className="progress-track" role="progressbar" aria-valuenow={v} aria-valuemin={0} aria-valuemax={100}>
        <div className={`progress-fill ${v >= 100 ? 'done' : ''}`} style={{ width: `${v}%` }} />
      </div>
      <span className="progress-label">{label ?? `${v.toFixed(0)}%`}</span>
    </div>
  );
}
