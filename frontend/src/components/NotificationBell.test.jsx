import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import NotificationBell from './NotificationBell';
import api from '@/api/axios';

vi.mock('@/api/axios', () => ({ default: { get: vi.fn(), put: vi.fn(), delete: vi.fn() } }));

const notifications = [
  { id: 'n1', type: 'NEW_REPLY', message: 'Bob commented on Chapter 1', isRead: false, createdAt: '2026-01-01T10:00:00' },
  { id: 'n2', type: 'CHAPTER_UNLOCK', message: 'Club is now reading Dune', isRead: true, createdAt: '2026-01-01T09:00:00' },
];

beforeEach(() => {
  api.get.mockReset();
  api.put.mockReset();
  api.delete.mockReset();
  api.get.mockImplementation((url) => {
    if (url === '/notifications/me/unread-count') return Promise.resolve({ data: { count: 3 } });
    if (url === '/notifications/me') return Promise.resolve({ data: notifications });
    return Promise.reject(new Error(`unexpected ${url}`));
  });
  api.put.mockResolvedValue({ data: {} });
  api.delete.mockResolvedValue({ data: {} });
});

describe('NotificationBell', () => {
  it('shows the unread count on the bell', async () => {
    render(<NotificationBell />);
    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /3 unread/i })).toBeInTheDocument();
  });

  it('opening the bell loads and lists the notifications', async () => {
    render(<NotificationBell />);
    await screen.findByText('3');

    fireEvent.click(screen.getByRole('button', { name: /notifications/i }));

    expect(await screen.findByText('Bob commented on Chapter 1')).toBeInTheDocument();
    expect(screen.getByText('Club is now reading Dune')).toBeInTheDocument();
  });

  it('marking everything read clears the badge', async () => {
    render(<NotificationBell />);
    await screen.findByText('3');
    fireEvent.click(screen.getByRole('button', { name: /notifications/i }));
    await screen.findByText('Bob commented on Chapter 1');

    fireEvent.click(screen.getByText('Mark all read'));

    await waitFor(() => expect(screen.queryByText('3')).not.toBeInTheDocument());
    expect(api.put).toHaveBeenCalledWith('/notifications/read-all');
  });

  it('dismissing removes the notification', async () => {
    render(<NotificationBell />);
    await screen.findByText('3');
    fireEvent.click(screen.getByRole('button', { name: /notifications/i }));
    await screen.findByText('Bob commented on Chapter 1');

    fireEvent.click(screen.getAllByRole('button', { name: /dismiss notification/i })[0]);

    await waitFor(() => expect(screen.queryByText('Bob commented on Chapter 1')).not.toBeInTheDocument());
    expect(api.delete).toHaveBeenCalledWith('/notifications/n1');
  });

  it('keeps working when the server cannot be reached', async () => {
    api.get.mockRejectedValue(new Error('Network Error'));
    render(<NotificationBell />);

    expect(screen.getByRole('button', { name: 'Notifications' })).toBeInTheDocument();
    await waitFor(() => expect(api.get).toHaveBeenCalled());
  });
});
