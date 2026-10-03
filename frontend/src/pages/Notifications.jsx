import { useCallback, useEffect, useState } from 'react';
import { notificationsApi } from '../api/notifications';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import EmptyState from '../components/EmptyState';
import Spinner from '../components/Spinner';
import { notifyNotificationsChanged } from '../components/useUnreadCount';
import { fmtDate } from '../utils';

const ICONS = {
  ENROLLMENT: '📝', ENROLLMENT_CONFIRMATION: '📝', STUDENT_ENROLLED: '📝',
  COURSE_COMPLETED: '🎓', COURSE_COMPLETION: '🎓',
  CERTIFICATE: '🏆', CERTIFICATE_GENERATED: '🏆', CERTIFICATE_ISSUED: '🏆',
};

export default function Notifications() {
  const { user } = useAuth();
  const toast = useToast();
  const [items, setItems] = useState(null);
  const [onlyUnread, setOnlyUnread] = useState(false);

  const load = useCallback(() => {
    notificationsApi.byUser(user.id)
      .then((list) => setItems([...list].sort((a, b) => new Date(b.sentAt) - new Date(a.sentAt))))
      .catch((err) => { setItems((prev) => prev || []); toast.apiError(err); });
  }, [user.id, toast]);

  useEffect(() => {
    load();
    const t = setInterval(load, 10000);
    return () => clearInterval(t);
  }, [load]);

  const markRead = async (n) => {
    try {
      await notificationsApi.markRead(n.id);
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      notifyNotificationsChanged();
    } catch (err) {
      toast.apiError(err);
    }
  };

  const markAll = async () => {
    const unread = items.filter((n) => !n.read);
    try {
      await Promise.all(unread.map((n) => notificationsApi.markRead(n.id)));
      setItems((prev) => prev.map((x) => ({ ...x, read: true })));
      notifyNotificationsChanged();
    } catch (err) {
      toast.apiError(err);
      load();
    }
  };

  if (items === null) return <Spinner label="Loading notifications…" />;
  const visible = onlyUnread ? items.filter((n) => !n.read) : items;
  const unreadCount = items.filter((n) => !n.read).length;

  return (
    <div className="narrow">
      <div className="page-head">
        <div>
          <h1>Notifications</h1>
          <p className="muted">Produced by notification-service from StudentEnrolled, CourseCompleted and CertificateGenerated events.</p>
        </div>
        <div className="toolbar">
          <label className="check"><input type="checkbox" checked={onlyUnread} onChange={(e) => setOnlyUnread(e.target.checked)} /> Unread only</label>
          <button className="btn btn-outline btn-sm" onClick={markAll} disabled={unreadCount === 0}>Mark all read</button>
        </div>
      </div>
      {visible.length === 0 ? <EmptyState icon="🔔" title="You're all caught up" /> : (
        <ul className="notif-list">
          {visible.map((n) => (
            <li key={n.id} className={`card notif ${n.read ? '' : 'unread'}`}>
              <span className="notif-icon">{ICONS[String(n.type).toUpperCase()] || '🔔'}</span>
              <div className="notif-body">
                <div className="notif-head">
                  <strong>{n.subject || n.type}</strong>
                  <span className="muted small">{fmtDate(n.sentAt, true)}</span>
                </div>
                <p>{n.message}</p>
                <div className="muted small">
                  {n.type}{n.recipientEmail ? ` · ${n.recipientEmail}` : ''}{n.status ? ` · ${n.status}` : ''}
                </div>
              </div>
              {!n.read && <button className="btn btn-ghost btn-sm" onClick={() => markRead(n)}>Mark read</button>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
