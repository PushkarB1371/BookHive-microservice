/** Joins class names, skipping anything falsy. */
export function cn(...inputs) {
  return inputs.flat().filter(Boolean).join(' ');
}
