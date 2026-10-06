import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import Pagination from './Pagination';

describe('Pagination', () => {
  it('renders nothing when everything fits on one page', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onPageChange={() => {}} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows where you are', () => {
    render(<Pagination page={1} totalPages={4} onPageChange={() => {}} />);
    expect(screen.getByText('Page 2 of 4')).toBeInTheDocument();
  });

  it('cannot go before the first or after the last page', () => {
    const { rerender } = render(<Pagination page={0} totalPages={3} onPageChange={() => {}} />);
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled();

    rerender(<Pagination page={2} totalPages={3} onPageChange={() => {}} />);
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Previous' })).toBeEnabled();
  });

  it('asks for the neighbouring page', () => {
    const onPageChange = vi.fn();
    render(<Pagination page={1} totalPages={3} onPageChange={onPageChange} />);

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    fireEvent.click(screen.getByRole('button', { name: 'Previous' }));

    expect(onPageChange).toHaveBeenNthCalledWith(1, 2);
    expect(onPageChange).toHaveBeenNthCalledWith(2, 0);
  });
});
