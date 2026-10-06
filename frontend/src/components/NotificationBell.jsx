import { useCallback, useEffect, useRef, useState } from 'react';
import api from '@/api/axios';
import { cn } from '@/lib/utils';

const POLL_MS = 30000;

/** Bell with an unread badge. The unread count is polled; the full list loads only when opened. */
export default function NotificationBell() {
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const containerRef = useRef(null);

  const refreshCount = useCallback(async () => {
    try {
      const res = await api.get('/notifications/me/unread-count');
      setCount(res.data?.count ?? 0);
    } catch {
      // keep the last known count; the next poll will try again
    }
  }, []);

  const loadItems = useCallback(async () => {
    setLoading(true);
    try {
      const res = await api.get('/notifications/me');
      setItems(Array.isArray(res.data) ? res.data : []);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshCount();
    const timer = setInterval(refreshCount, POLL_MS);
    return () => clearInterval(timer);
  }, [refreshCount]);

  useEffect(() => {
    if (!open) return undefined;
    const closeOnOutsideClick = (event) => {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', closeOnOutsideClick);
    return () => document.removeEventListener('mousedown', closeOnOutsideClick);
  }, [open]);

  const toggle = async () => {
    const next = !open;
    setOpen(next);
    if (next) await loadItems();
  };

  const markRead = async (item) => {
    if (item.isRead) return;
    try {
      await api.put(`/notifications/${item.id}/read`);
      setItems((prev) => prev.map((n) => (n.id === item.id ? { ...n, isRead: true } : n)));
      setCount((c) => Math.max(0, c - 1));
    } catch {
      // ignore; the user can click again
    }
  };

  const markAllRead = async () => {
    try {
      await api.put('/notifications/read-all');
      setItems((prev) => prev.map((n) => ({ ...n, isRead: true })));
      setCount(0);
    } catch {
      // ignore
    }
  };

  const dismiss = async (item) => {
    try {
      await api.delete(`/notifications/${item.id}`);
      setItems((prev) => prev.filter((n) => n.id !== item.id));
      if (!item.isRead) setCount((c) => Math.max(0, c - 1));
    } catch {
      // ignore
    }
  };

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={toggle}
        aria-label={count > 0 ? `Notifications (${count} unread)` : 'Notifications'}
        aria-expanded={open}
        className="relative rounded-md p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"
             strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {count > 0 && (
          <span className="absolute -right-0.5 -top-0.5 min-w-[18px] rounded-full bg-accent px-1 text-center text-[11px] leading-[18px] text-accent-foreground">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 rounded-lg border border-border bg-card shadow-lg">
          <div className="flex items-center justify-between border-b border-rule px-4 py-3">
            <span className="font-display font-semibold">Notifications</span>
            {items.some((n) => !n.isRead) && (
              <button type="button" onClick={markAllRead} className="text-xs text-primary hover:underline">
                Mark all read
              </button>
            )}
          </div>
          <div className="max-h-96 overflow-y-auto">
            {loading ? (
              <p className="px-4 py-6 text-center text-sm text-muted-foreground">Loading…</p>
            ) : items.length === 0 ? (
              <p className="px-4 py-6 text-center text-sm text-muted-foreground">You're all caught up.</p>
            ) : (
              items.map((item) => (
                <div
                  key={item.id}
                  className={cn('flex items-start gap-2 border-b border-rule px-4 py-3 last:border-0', !item.isRead && 'bg-secondary/40')}
                >
                  <button type="button" onClick={() => markRead(item)} className="flex-1 text-left">
                    <p className="text-sm text-foreground">{item.message}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">{new Date(item.createdAt).toLocaleString()}</p>
                  </button>
                  <button
                    type="button"
                    onClick={() => dismiss(item)}
                    aria-label="Dismiss notification"
                    className="text-muted-foreground hover:text-destructive"
                  >
                    ×
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
