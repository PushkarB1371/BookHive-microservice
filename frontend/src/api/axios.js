import axios from 'axios';

// Default "/api" works everywhere: nginx proxies it in Docker, Vite proxies it in development.
// Set VITE_API_URL at build time to point at a different host.
const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// An expired or rejected token on a normal request logs the user out (AuthContext listens for this).
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const url = error.config?.url || '';
    if (error.response?.status === 401 && !url.includes('/auth/')) {
      window.dispatchEvent(new Event('auth:expired'));
    }
    return Promise.reject(error);
  },
);

export default api;
