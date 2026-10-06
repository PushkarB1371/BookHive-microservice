import { cn } from '@/lib/utils';

export function Progress({ value = 0, className }) {
  const safe = Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : 0;
  return (
    <div
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(safe)}
      className={cn('h-2 w-full overflow-hidden rounded-full bg-muted', className)}
    >
      <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${safe}%` }} />
    </div>
  );
}
