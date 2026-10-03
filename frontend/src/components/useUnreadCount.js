import { useCallback, useEffect, useState } from 'react';
import { notificationsApi } from '../api/notifications';

const EVENT = 'lms:notifications-changed';

/** Tell the navbar badge to refresh immediately (e.g. after marking a notification read). */
export const notifyNotificationsChanged = () => window.dispatchEvent(new Event(EVENT));

/** Polls the unread-count endpoint every `intervalMs`. Failures are silent (service may be down). */
export default function useUnreadCount(userId, intervalMs = 10000) {
  const [count, setCount] = useState(0);

  const load = useCallback(async () => {
    if (!userId) return;
    try {
      const res = await notificationsApi.unreadCount(userId);
      setCount(Number(res?.count ?? 0));
    } catch { /* ignore – badge is best-effort */ }
  }, [userId]);

  useEffect(() => {
    if (!userId) { setCount(0); return undefined; }
    load();
    const t = setInterval(load, intervalMs);
    window.addEventListener(EVENT, load);
    return () => { clearInterval(t); window.removeEventListener(EVENT, load); };
  }, [userId, intervalMs, load]);

  return count;
}
