import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';

export default function Home() {
  const { user } = useAuth();

  if (user) {
    return <Navigate to="/clubs" replace />;
  }

  return (
    <div className="mx-auto mt-10 max-w-5xl">
      <div className="grid grid-cols-1 items-center gap-16 lg:grid-cols-[1.1fr_0.9fr]">
        <div>
          <h1 className="font-display text-5xl leading-[1.05] text-foreground">
            A club for every book you haven't started yet.
          </h1>
          <p className="drop-cap mt-6 max-w-md text-lg leading-relaxed text-muted-foreground">
            BookHive is where a few people agree on a book, read it at their own pace, and talk about it
            chapter by chapter, without spoiling the ending for anyone still catching up.
          </p>
          <div className="mt-8 flex items-center gap-4">
            <Button asChild size="lg">
              <Link to="/signup">Start reading</Link>
            </Button>
            <Link to="/login" className="text-sm text-foreground underline underline-offset-4 hover:text-primary">
              I already have an account
            </Link>
          </div>
        </div>

        <div className="relative hidden h-80 lg:block" aria-hidden="true">
          <div className="absolute right-0 top-0 h-64 w-40 rounded-sm bg-primary shadow-sm" style={{ transform: 'rotate(-4deg)' }} />
          <div className="absolute right-16 top-8 h-64 w-40 rounded-sm bg-accent shadow-sm" style={{ transform: 'rotate(3deg)' }} />
          <div className="absolute right-6 top-20 h-64 w-40 rounded-sm bg-foreground shadow-md" style={{ transform: 'rotate(-1deg)' }} />
        </div>
      </div>

      <div className="mt-24 grid grid-cols-1 gap-x-10 gap-y-8 border-t border-rule pt-10 sm:grid-cols-3">
        <div>
          <h2 className="mb-2 font-display text-lg text-foreground">Pick a book together</h2>
          <p className="text-sm leading-relaxed text-muted-foreground">
            Start a club, choose a book from the catalog, and invite the people you want reading it with you.
          </p>
        </div>
        <div>
          <h2 className="mb-2 font-display text-lg text-foreground">Read at your own pace</h2>
          <p className="text-sm leading-relaxed text-muted-foreground">
            Log your chapter as you go. Everyone in the club sees where the others are, with no pressure to keep up.
          </p>
        </div>
        <div>
          <h2 className="mb-2 font-display text-lg text-foreground">Talk chapter by chapter</h2>
          <p className="text-sm leading-relaxed text-muted-foreground">
            Discussion is scoped to each chapter, so someone three chapters behind never sees what happens next.
          </p>
        </div>
      </div>
    </div>
  );
}
