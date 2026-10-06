import { Button } from '@/components/ui/button';

export default function Pagination({ page, totalPages, onPageChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <nav aria-label="Pagination" className="mt-6 flex items-center justify-center gap-3">
      <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
        Previous
      </Button>
      <span className="text-sm text-muted-foreground">Page {page + 1} of {totalPages}</span>
      <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
        Next
      </Button>
    </nav>
  );
}
