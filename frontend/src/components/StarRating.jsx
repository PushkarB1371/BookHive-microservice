import { cn } from '@/lib/utils';

/** Five clickable stars. */
export default function StarRating({ value, onChange }) {
  return (
    <div className="flex gap-1" role="radiogroup" aria-label="Rating">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          role="radio"
          aria-checked={n === value}
          aria-label={`${n} star${n === 1 ? '' : 's'}`}
          onClick={() => onChange(n)}
          className={cn('text-2xl leading-none', n <= value ? 'text-accent' : 'text-rule')}
        >
          ★
        </button>
      ))}
    </div>
  );
}
