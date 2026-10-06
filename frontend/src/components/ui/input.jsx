import { cn } from '@/lib/utils';

export function Input({ className, ...props }) {
  return (
    <input
      className={cn(
        'h-9 w-full rounded-md border border-input bg-card px-3 text-sm',
        'placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-ring',
        'disabled:opacity-50',
        className,
      )}
      {...props}
    />
  );
}
