import { cn } from '@/lib/utils';

export function Card({ className, ...props }) {
  return <div className={cn('rounded-lg border border-border bg-card text-card-foreground', className)} {...props} />;
}

export function CardContent({ className, ...props }) {
  return <div className={cn('p-5', className)} {...props} />;
}
