/**
 * Turns whatever the backend (or the network) gave us into a string that is safe to render.
 * The backend answers errors as { message: "..." }; older/plain responses can be a bare string.
 */
export function getErrorMessage(err, fallback) {
  const data = err?.response?.data;
  if (typeof data === 'string' && data.trim()) return data;
  if (data && typeof data.message === 'string' && data.message.trim()) return data.message;
  if (err?.response?.status === 401) return 'Please log in again.';
  if (err && !err.response) return 'Cannot reach the server. Check your connection and try again.';
  return fallback;
}
