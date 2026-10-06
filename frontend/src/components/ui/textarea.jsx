import { cn } from '@/lib/utils';

export function Textarea({ className, ...props }) {
  return (
    <textarea
      className={cn(
        'w-full rounded-md border border-input bg-card px-3 py-2 text-sm',
        'placeholder:text-muted-foreground focus-visible:outline-2 focus-visible:outline-ring',
        'disabled:opacity-50',
        className,
      )}
      {...props}
    />
  );
}
