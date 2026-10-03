export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function fmtDate(value, withTime = false) {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return String(value);
  return withTime
    ? d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
    : d.toLocaleDateString(undefined, { dateStyle: 'medium' });
}

export function fmtPrice(p) {
  const n = Number(p);
  if (!Number.isFinite(n) || n === 0) return 'Free';
  return `₹${n.toLocaleString(undefined, { maximumFractionDigits: 2 })}`;
}

export function pct(v) {
  const n = Number(v);
  return Number.isFinite(n) ? Math.max(0, Math.min(100, n)) : 0;
}

export const aggregateId = (studentId, courseId) => `${studentId}-${courseId}`;

export function verifyUrl(code) {
  return `${window.location.origin}/verify/${encodeURIComponent(code)}`;
}

export const is404 = (err) => err?.response?.status === 404;
