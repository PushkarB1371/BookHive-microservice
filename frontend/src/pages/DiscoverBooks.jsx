import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '@/api/axios';
import { useAuth } from '@/context/AuthContext';
import { getErrorMessage } from '@/utils/errors';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

const CATEGORIES = ['', 'Fiction', 'Fantasy', 'Mystery', 'Science Fiction', 'Biography', 'History', 'Philosophy', 'Self-Help', 'Business'];

export default function DiscoverBooks() {
  const { isAdmin } = useAuth();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [author, setAuthor] = useState('');
  const [category, setCategory] = useState('');
  const [results, setResults] = useState([]);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!query.trim() && !author.trim() && !category) {
      setError('Enter a keyword or an author, or pick a category.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      const res = await api.get('/books/search', {
        params: { q: query || undefined, author: author || undefined, category: category || undefined },
      });
      setResults(res.data);
      setSearched(true);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not search right now.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <div className="mb-8 border-b border-rule pb-6">
        <h1 className="font-display text-4xl text-foreground">Discover</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Browse the wider world of books. {isAdmin ? 'Add any of them to the catalog for clubs to read.' : 'Ask an admin to add one to the catalog.'}
        </p>
      </div>

      <form onSubmit={handleSearch} className="mb-8 grid grid-cols-1 items-end gap-3 sm:grid-cols-[1fr_1fr_180px_auto]">
        <div className="space-y-1.5">
          <Label htmlFor="q">Keyword</Label>
          <Input id="q" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="habits, dragons, Rome…" />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="author">Author</Label>
          <Input id="author" value={author} onChange={(e) => setAuthor(e.target.value)} placeholder="Ursula K. Le Guin" />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="category">Category</Label>
          <select
            id="category"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
            className="h-9 w-full rounded-md border border-input bg-card px-3 text-sm"
          >
            {CATEGORIES.map((c) => <option key={c} value={c}>{c || 'Any'}</option>)}
          </select>
        </div>
        <Button type="submit" disabled={loading}>{loading ? 'Searching…' : 'Search'}</Button>
      </form>

      {error && <p role="alert" className="mb-4 text-sm text-destructive">{error}</p>}

      {searched && results.length === 0 && !error && (
        <p className="text-sm text-muted-foreground">Nothing found. Try fewer or different words.</p>
      )}

      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {results.map((book, i) => (
          <div key={`${book.title}-${i}`} className="flex gap-4 border-b border-rule pb-6">
            {book.coverImageUrl ? (
              <img src={book.coverImageUrl} alt="" className="h-36 w-24 flex-shrink-0 rounded-sm object-cover shadow-sm" />
            ) : (
              <div className="h-36 w-24 flex-shrink-0 rounded-sm bg-secondary" />
            )}
            <div className="min-w-0">
              <h2 className="font-display leading-snug text-foreground">{book.title}</h2>
              <p className="text-sm text-muted-foreground">{book.author}</p>
              {book.category && <p className="mt-1 text-xs text-muted-foreground">{book.category}</p>}
              {book.description && (
                <p className="mt-2 line-clamp-3 text-xs leading-relaxed text-muted-foreground">{book.description}</p>
              )}
              {isAdmin && (
                <Button
                  size="sm"
                  variant="outline"
                  className="mt-3"
                  onClick={() => navigate('/books', { state: { prefill: book } })}
                >
                  Add to catalog
                </Button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
