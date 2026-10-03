import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { onUnauthorized, storage } from '../api/client';
import { usersApi } from '../api/users';

const AuthContext = createContext(null);

/** Decode the JWT payload (no verification – just to read exp). */
function tokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return payload.exp ? payload.exp * 1000 < Date.now() : false;
  } catch {
    return false;
  }
}

export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const [token, setToken] = useState(() => {
    const t = storage.getToken();
    if (t && tokenExpired(t)) { storage.clear(); return null; }
    return t;
  });
  const [user, setUser] = useState(() => (token ? storage.getUser() : null));

  const logout = useCallback((redirect = true) => {
    storage.clear();
    setToken(null);
    setUser(null);
    if (redirect) navigate('/login', { replace: true });
  }, [navigate]);

  useEffect(() => {
    onUnauthorized(() => logout(true));
  }, [logout]);

  const login = useCallback(async (email, password) => {
    const res = await usersApi.login(email, password);
    storage.save(res.token, res.user);
    setToken(res.token);
    setUser(res.user);
    return res.user;
  }, []);

  const value = useMemo(() => ({
    token,
    user,
    isAuthenticated: !!token && !!user,
    role: user?.role,
    login,
    logout,
  }), [token, user, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}

export function homePathFor(role) {
  if (role === 'STUDENT') return '/my-learning';
  if (role === 'INSTRUCTOR') return '/instructor';
  if (role === 'ADMIN') return '/admin';
  return '/';
}
