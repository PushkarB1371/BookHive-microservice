import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { AuthProvider, useAuth } from './AuthContext';
import api from '@/api/axios';

vi.mock('@/api/axios', () => ({ default: { post: vi.fn() } }));

function Probe() {
  const { user, isAdmin, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="name">{user ? user.name : 'anonymous'}</span>
      <span data-testid="admin">{isAdmin ? 'admin' : 'member'}</span>
      <button onClick={() => login('ann@example.com', 'secret1')}>login</button>
      <button onClick={logout}>logout</button>
    </div>
  );
}

describe('AuthContext', () => {
  beforeEach(() => {
    api.post.mockReset();
  });

  it('starts anonymous', () => {
    render(<AuthProvider><Probe /></AuthProvider>);
    expect(screen.getByTestId('name')).toHaveTextContent('anonymous');
  });

  it('logging in stores the token and user', async () => {
    api.post.mockResolvedValue({
      data: { token: 'jwt-123', userId: 'u1', name: 'Ann', email: 'ann@example.com', role: 'ADMIN' },
    });
    render(<AuthProvider><Probe /></AuthProvider>);

    fireEvent.click(screen.getByText('login'));

    await waitFor(() => expect(screen.getByTestId('name')).toHaveTextContent('Ann'));
    expect(screen.getByTestId('admin')).toHaveTextContent('admin');
    expect(localStorage.getItem('token')).toBe('jwt-123');
    expect(api.post).toHaveBeenCalledWith('/auth/login', { email: 'ann@example.com', password: 'secret1' });
  });

  it('logging out clears everything', async () => {
    localStorage.setItem('token', 'jwt-123');
    localStorage.setItem('user', JSON.stringify({ userId: 'u1', name: 'Ann', role: 'MEMBER' }));
    render(<AuthProvider><Probe /></AuthProvider>);
    expect(screen.getByTestId('name')).toHaveTextContent('Ann');

    fireEvent.click(screen.getByText('logout'));

    expect(screen.getByTestId('name')).toHaveTextContent('anonymous');
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('an expired session event logs the user out', async () => {
    localStorage.setItem('user', JSON.stringify({ userId: 'u1', name: 'Ann', role: 'MEMBER' }));
    render(<AuthProvider><Probe /></AuthProvider>);

    window.dispatchEvent(new Event('auth:expired'));

    await waitFor(() => expect(screen.getByTestId('name')).toHaveTextContent('anonymous'));
  });

  it('ignores a corrupted stored user instead of crashing', () => {
    localStorage.setItem('user', '{not json');
    render(<AuthProvider><Probe /></AuthProvider>);
    expect(screen.getByTestId('name')).toHaveTextContent('anonymous');
  });
});
