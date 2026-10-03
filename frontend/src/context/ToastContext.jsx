import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { errorMessage } from '../api/client';

const ToastContext = createContext(null);
let nextId = 1;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), []);

  const push = useCallback((type, message, ttl = 4500) => {
    const id = nextId++;
    setToasts((t) => [...t, { id, type, message }]);
    setTimeout(() => dismiss(id), ttl);
  }, [dismiss]);

  const api = useMemo(() => ({
    success: (m) => push('success', m),
    info: (m) => push('info', m),
    error: (m) => push('error', m, 7000),
    /** Show the backend's `message` from an axios error. */
    apiError: async (err) => push('error', await errorMessage(err), 7000),
  }), [push]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-stack" role="status" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.type}`} onClick={() => dismiss(t.id)}>
            <span className="toast-icon">{t.type === 'success' ? '✓' : t.type === 'error' ? '!' : 'i'}</span>
            <span>{t.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
