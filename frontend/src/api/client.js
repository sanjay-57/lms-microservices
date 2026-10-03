import axios from 'axios';

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const TOKEN_KEY = 'lms.token';
const USER_KEY = 'lms.user';

// localStorage can throw (private mode, blocked storage) – never let that break the app.
export const storage = {
  getToken() {
    try { return localStorage.getItem(TOKEN_KEY); } catch { return null; }
  },
  getUser() {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  },
  save(token, user) {
    try {
      localStorage.setItem(TOKEN_KEY, token);
      localStorage.setItem(USER_KEY, JSON.stringify(user));
    } catch { /* ignore */ }
  },
  clear() {
    try {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    } catch { /* ignore */ }
  },
};

const client = axios.create({ baseURL: API_URL, timeout: 15000 });

client.interceptors.request.use((config) => {
  const token = storage.getToken();
  if (token && !config.skipAuth) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let unauthorizedHandler = null;
export function onUnauthorized(handler) {
  unauthorizedHandler = handler;
}

client.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error.response?.status;
    const url = error.config?.url || '';
    const isAuthCall = url.includes('/api/users/login') || url.includes('/api/users/register');
    if (status === 401 && !isAuthCall && !error.config?.skipAuth && unauthorizedHandler) {
      unauthorizedHandler();
    }
    return Promise.reject(error);
  }
);

/** Extract a human-readable message from the backend error format {timestamp,status,message,path}. */
export async function errorMessage(error) {
  const data = error?.response?.data;
  if (data instanceof Blob) {
    try {
      const parsed = JSON.parse(await data.text());
      if (parsed?.message) return parsed.message;
    } catch { /* not JSON */ }
  }
  if (data && typeof data === 'object' && data.message) return data.message;
  if (typeof data === 'string' && data.trim()) return data;
  if (error?.response?.status === 503) return 'Service temporarily unavailable (503). Is it running and registered in Eureka?';
  if (error?.code === 'ERR_NETWORK') return `Cannot reach the API gateway at ${API_URL}.`;
  return error?.message || 'Something went wrong';
}

export const data = (p) => p.then((r) => r.data);

export default client;
