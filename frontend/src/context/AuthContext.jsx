import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import api from '@/api/axios';

const AuthContext = createContext(null);

function readStoredUser() {
  try {
    const raw = localStorage.getItem('user');
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readStoredUser);

  const persist = useCallback((data) => {
    const next = { userId: data.userId, name: data.name, email: data.email, role: data.role };
    localStorage.setItem('token', data.token);
    localStorage.setItem('user', JSON.stringify(next));
    setUser(next);
    return next;
  }, []);

  const login = useCallback(async (email, password) => {
    const res = await api.post('/auth/login', { email, password });
    return persist(res.data);
  }, [persist]);

  const signup = useCallback(async (name, email, password) => {
    const res = await api.post('/auth/signup', { name, email, password });
    return persist(res.data);
  }, [persist]);

  const logout = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setUser(null);
  }, []);

  useEffect(() => {
    window.addEventListener('auth:expired', logout);
    return () => window.removeEventListener('auth:expired', logout);
  }, [logout]);

  const value = useMemo(
    () => ({ user, isAdmin: user?.role === 'ADMIN', login, signup, logout }),
    [user, login, signup, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
