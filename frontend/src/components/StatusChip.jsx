const LABELS = {
  NOT_STARTED: 'Not started',
  IN_PROGRESS: 'In progress',
  AWAITING_ASSESSMENT: 'Awaiting assessment',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
  ACTIVE: 'Active',
  CANCELLED: 'Cancelled',
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  ARCHIVED: 'Archived',
  INITIALISING: 'Initialising…',
  STUDENT: 'Student',
  INSTRUCTOR: 'Instructor',
  ADMIN: 'Admin',
  SENT: 'Sent',
  PENDING: 'Pending',
};

export default function StatusChip({ status }) {
  if (!status) return null;
  const key = String(status).toUpperCase();
  return <span className={`chip chip-${key.toLowerCase()}`}>{LABELS[key] || status}</span>;
}
