import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import NotificationBell from '@/components/NotificationBell';

const linkClass = 'text-muted-foreground transition-colors hover:text-primary';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="flex items-center justify-between border-b border-rule bg-card px-8 py-5">
      <Link to="/" className="font-display text-2xl font-semibold tracking-tight text-primary">
        BookHive
      </Link>

      <div className="flex items-center gap-6 text-sm">
        {user && (
          <>
            <Link to="/clubs" className={linkClass}>Clubs</Link>
            <Link to="/books" className={linkClass}>Books</Link>
            <Link to="/discover" className={linkClass}>Discover</Link>
            <NotificationBell />
            <span className="hidden text-muted-foreground sm:inline">{user.name}</span>
            <Button variant="ghost" onClick={handleLogout}>Log out</Button>
          </>
        )}
        {!user && (
          <>
            <Link to="/login" className={linkClass}>Log in</Link>
            <Button asChild>
              <Link to="/signup">Join BookHive</Link>
            </Button>
          </>
        )}
      </div>
    </nav>
  );
}
