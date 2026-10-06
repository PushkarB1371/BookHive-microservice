import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute';
import { useAuth } from '@/context/AuthContext';

vi.mock('@/context/AuthContext', () => ({ useAuth: vi.fn() }));

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<div>login page</div>} />
        <Route element={<ProtectedRoute />}>
          <Route path="/clubs" element={<div>secret clubs</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('ProtectedRoute', () => {
  it('sends anonymous visitors to the login page', () => {
    useAuth.mockReturnValue({ user: null });
    renderAt('/clubs');
    expect(screen.getByText('login page')).toBeInTheDocument();
    expect(screen.queryByText('secret clubs')).not.toBeInTheDocument();
  });

  it('lets logged-in users through', () => {
    useAuth.mockReturnValue({ user: { userId: 'u1', name: 'Ann' } });
    renderAt('/clubs');
    expect(screen.getByText('secret clubs')).toBeInTheDocument();
  });
});
