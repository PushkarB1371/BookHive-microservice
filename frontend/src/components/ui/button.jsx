import { cloneElement, isValidElement } from 'react';
import { cn } from '@/lib/utils';

const variants = {
  default: 'bg-primary text-primary-foreground hover:bg-primary/90',
  secondary: 'bg-secondary text-secondary-foreground hover:bg-secondary/80',
  outline: 'border border-border bg-card text-foreground hover:bg-muted',
  ghost: 'text-foreground hover:bg-muted',
  destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive/90',
};

const sizes = {
  default: 'h-9 px-4 text-sm',
  sm: 'h-8 px-3 text-sm',
  lg: 'h-11 px-6 text-base',
};

export function Button({ className, variant = 'default', size = 'default', asChild = false, children, ...props }) {
  const classes = cn(
    'inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors',
    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
    'disabled:pointer-events-none disabled:opacity-50',
    variants[variant],
    sizes[size],
    className,
  );

  // <Button asChild><Link to="/x">..</Link></Button> renders the link with button styling
  if (asChild && isValidElement(children)) {
    return cloneElement(children, { ...props, className: cn(classes, children.props.className) });
  }

  return (
    <button type="button" className={classes} {...props}>
      {children}
    </button>
  );
}
