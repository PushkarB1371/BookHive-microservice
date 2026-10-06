import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="py-24 text-center">
      <h1 className="font-display text-4xl">This page isn't on the shelf</h1>
      <p className="mt-3 text-muted-foreground">The address may be mistyped, or the page has moved.</p>
      <Link to="/" className="mt-6 inline-block text-primary underline underline-offset-4">Back to the start</Link>
    </div>
  );
}
