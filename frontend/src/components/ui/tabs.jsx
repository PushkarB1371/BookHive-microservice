import { createContext, useContext } from 'react';
import { cn } from '@/lib/utils';

const TabsContext = createContext({ value: undefined, onValueChange: () => {} });

export function Tabs({ value, onValueChange, className, children }) {
  return (
    <TabsContext.Provider value={{ value, onValueChange }}>
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  );
}

export function TabsList({ className, children }) {
  return (
    <div role="tablist" className={cn('flex w-fit items-center gap-1 rounded-lg bg-muted p-1', className)}>
      {children}
    </div>
  );
}

export function TabsTrigger({ value, className, children }) {
  const ctx = useContext(TabsContext);
  const active = ctx.value === value;
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={() => ctx.onValueChange(value)}
      className={cn(
        'rounded-md px-4 py-1.5 text-sm transition-colors',
        active ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
        className,
      )}
    >
      {children}
    </button>
  );
}

export function TabsContent({ value, className, children }) {
  const ctx = useContext(TabsContext);
  if (ctx.value !== value) return null;
  return <div role="tabpanel" className={className}>{children}</div>;
}
