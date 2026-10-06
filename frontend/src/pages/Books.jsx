import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import api from '@/api/axios';
import { useAuth } from '@/context/AuthContext';
import { getErrorMessage } from '@/utils/errors';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import Pagination from '@/components/Pagination';

const blankForm = {
  title: '', author: '', totalChapters: '', coverImageUrl: '', description: '', category: '', publishedDate: '',
};

function fromSearchResult(result) {
  return {
    title: result.title || '',
    author: result.author || '',
    totalChapters: '',
    coverImageUrl: result.coverImageUrl || '',
    description: result.description || '',
    category: result.category || '',
    publishedDate: result.publishedDate || '',
  };
}

export default function Books() {
  const { isAdmin } = useAuth();
  const location = useLocation();
  const prefill = location.state?.prefill;

  const [pageData, setPageData] = useState({ content: [], page: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [showForm, setShowForm] = useState(Boolean(prefill));
  const [form, setForm] = useState(prefill ? fromSearchResult(prefill) : blankForm);
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState('');

  const fetchBooks = useCallback(async (page = 0) => {
    setLoading(true);
    try {
      const res = await api.get('/books/paginated', { params: { page, size: 12 } });
      setPageData(res.data);
      setLoadError('');
    } catch (err) {
      setLoadError(getErrorMessage(err, 'Could not load the catalog.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchBooks(0);
  }, [fetchBooks]);

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    setSearching(true);
    setSearchError('');
    setSearchResults([]);
    try {
      const res = await api.get('/books/search', { params: { q: searchQuery } });
      setSearchResults(res.data);
      if (res.data.length === 0) setSearchError('No results. Try different words, or enter the details by hand below.');
    } catch (err) {
      setSearchError(getErrorMessage(err, 'Could not search right now.'));
    } finally {
      setSearching(false);
    }
  };

  const chooseResult = (result) => {
    setForm(fromSearchResult(result));
    setSearchResults([]);
    setSearchQuery('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    setSaving(true);
    try {
      await api.post('/books', { ...form, totalChapters: Number(form.totalChapters) });
      setForm(blankForm);
      setShowForm(false);
      await fetchBooks(0);
    } catch (err) {
      setFormError(getErrorMessage(err, 'Could not add the book.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <div className="mb-10 flex items-end justify-between border-b border-rule pb-6">
        <div>
          <h1 className="font-display text-4xl text-foreground">The catalog</h1>
          <p className="mt-2 text-sm text-muted-foreground">Every book a club can pick up next.</p>
        </div>
        {isAdmin && (
          <Button onClick={() => setShowForm(!showForm)}>{showForm ? 'Cancel' : 'Add a book'}</Button>
        )}
      </div>

      {isAdmin && showForm && (
        <Card className="mb-12">
          <CardContent className="space-y-6">
            <div>
              <Label htmlFor="search">Search Google Books</Label>
              <form onSubmit={handleSearch} className="mt-1.5 flex gap-2">
                <Input
                  id="search"
                  placeholder="Search by title or author…"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                />
                <Button type="submit" variant="secondary" disabled={searching}>
                  {searching ? 'Searching…' : 'Search'}
                </Button>
              </form>
              {searchError && <p className="mt-2 text-sm text-muted-foreground">{searchError}</p>}
              {searchResults.length > 0 && (
                <div className="mt-3 grid max-h-72 grid-cols-1 gap-2 overflow-y-auto sm:grid-cols-2">
                  {searchResults.map((r, i) => (
                    <button
                      key={`${r.title}-${i}`}
                      type="button"
                      onClick={() => chooseResult(r)}
                      className="flex gap-3 rounded-md border border-border p-2 text-left transition-colors hover:border-primary"
                    >
                      {r.coverImageUrl ? (
                        <img src={r.coverImageUrl} alt="" className="h-16 w-12 flex-shrink-0 rounded object-cover" />
                      ) : (
                        <div className="h-16 w-12 flex-shrink-0 rounded bg-secondary" />
                      )}
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium">{r.title}</p>
                        <p className="truncate text-xs text-muted-foreground">{r.author}</p>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </div>

            <div className="border-t border-border pt-5">
              <p className="mb-3 text-sm text-muted-foreground">
                Check the details below. The chapter count is always entered by hand.
              </p>
              <form onSubmit={handleSubmit} className="space-y-3">
                <div className="space-y-1.5">
                  <Label htmlFor="title">Title</Label>
                  <Input id="title" name="title" value={form.title} onChange={handleChange} required />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="author">Author</Label>
                  <Input id="author" name="author" value={form.author} onChange={handleChange} required />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="totalChapters">Total chapters</Label>
                  <Input id="totalChapters" name="totalChapters" type="number" min="1" value={form.totalChapters} onChange={handleChange} required />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="coverImageUrl">Cover image URL</Label>
                  <Input id="coverImageUrl" name="coverImageUrl" value={form.coverImageUrl} onChange={handleChange} />
                </div>
                {formError && <p role="alert" className="text-sm text-destructive">{formError}</p>}
                <Button type="submit" disabled={saving}>{saving ? 'Adding…' : 'Add book'}</Button>
              </form>
            </div>
          </CardContent>
        </Card>
      )}

      {loading ? (
        <p className="text-sm text-muted-foreground">Loading…</p>
      ) : loadError ? (
        <p role="alert" className="text-sm text-destructive">{loadError}</p>
      ) : pageData.content.length === 0 ? (
        <div className="rounded-lg border border-dashed border-rule p-12 text-center">
          <p className="text-sm text-muted-foreground">
            No books on the shelf yet. {isAdmin ? 'Add one above.' : 'Check back once an admin adds one.'}
          </p>
        </div>
      ) : (
        <>
          <div>
            <div className="flex flex-wrap items-end gap-x-6 gap-y-10 pb-3">
              {pageData.content.map((book) => (
                <Link key={book.id} to={`/books/${book.id}`} className="group flex-shrink-0">
                  {book.coverImageUrl ? (
                    <img
                      src={book.coverImageUrl}
                      alt=""
                      className="h-48 w-32 rounded-sm object-cover shadow-sm transition-shadow group-hover:shadow-md"
                      onError={(e) => { e.currentTarget.style.display = 'none'; }}
                    />
                  ) : (
                    <div className="flex h-48 w-32 items-center justify-center rounded-sm bg-secondary px-3 text-center">
                      <span className="font-display text-sm leading-snug text-secondary-foreground/60">{book.title}</span>
                    </div>
                  )}
                  <p className="mt-2 w-32 truncate font-display text-sm text-foreground transition-colors group-hover:text-primary">
                    {book.title}
                  </p>
                  <p className="w-32 truncate text-xs text-muted-foreground">{book.author}</p>
                </Link>
              ))}
            </div>
            <div className="h-px bg-rule" />
          </div>
          <Pagination page={pageData.page} totalPages={pageData.totalPages} onPageChange={fetchBooks} />
        </>
      )}
    </div>
  );
}
