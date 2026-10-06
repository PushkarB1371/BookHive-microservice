import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import api from '@/api/axios';
import { getErrorMessage } from '@/utils/errors';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import Pagination from '@/components/Pagination';

const TABS = [
  { key: 'mine', label: 'My clubs', empty: "You haven't started a club yet." },
  { key: 'joined', label: 'Joined', empty: "You haven't joined any clubs yet." },
  { key: 'discover', label: 'Discover', empty: 'No new clubs to discover right now.' },
];
const emptyPage = { content: [], page: 0, totalPages: 0, totalElements: 0 };

function ClubCard({ club }) {
  return (
    <Link to={`/clubs/${club.id}`} className="block h-full">
      <Card className="h-full overflow-hidden transition-colors hover:border-primary">
        {club.coverImageUrl ? (
          <img
            src={club.coverImageUrl}
            alt=""
            className="h-32 w-full object-cover"
            onError={(e) => { e.currentTarget.style.display = 'none'; }}
          />
        ) : (
          <div className="flex h-32 w-full items-center justify-center bg-secondary">
            <span className="font-display text-sm text-secondary-foreground/50">No cover</span>
          </div>
        )}
        <CardContent className="p-4">
          <h2 className="font-display font-semibold">{club.name}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{club.description || 'No description yet'}</p>
          <p className="mt-3 text-xs font-medium text-primary">
            {club.currentBook ? `Reading ${club.currentBook.title}` : 'No current book'}
          </p>
        </CardContent>
      </Card>
    </Link>
  );
}

export default function Clubs() {
  const [pages, setPages] = useState({ mine: emptyPage, joined: emptyPage, discover: emptyPage });
  const [active, setActive] = useState('mine');
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ name: '', description: '', coverImageUrl: '' });
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  const fetchTab = useCallback(async (type, page = 0) => {
    const res = await api.get('/clubs/grouped/paged', { params: { type, page, size: 6 } });
    setPages((prev) => ({ ...prev, [type]: res.data }));
  }, []);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      await Promise.all(TABS.map((t) => fetchTab(t.key, 0)));
    } catch (err) {
      setLoadError(getErrorMessage(err, 'Could not load your clubs.'));
    } finally {
      setLoading(false);
    }
  }, [fetchTab]);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const changePage = async (type, page) => {
    try {
      await fetchTab(type, page);
    } catch (err) {
      setLoadError(getErrorMessage(err, 'Could not load that page.'));
    }
  };

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFormError('');
    setSaving(true);
    try {
      await api.post('/clubs', form);
      setForm({ name: '', description: '', coverImageUrl: '' });
      setShowForm(false);
      setActive('mine');
      await fetchAll();
    } catch (err) {
      setFormError(getErrorMessage(err, 'Could not create the club.'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="font-display text-3xl font-semibold">Clubs</h1>
          <p className="mt-1 text-sm text-muted-foreground">Clubs you run, clubs you've joined, and clubs to discover.</p>
        </div>
        <Button onClick={() => setShowForm(!showForm)}>{showForm ? 'Cancel' : 'Start a club'}</Button>
      </div>

      {showForm && (
        <Card className="mb-8">
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-3">
              <div className="space-y-1.5">
                <Label htmlFor="name">Club name</Label>
                <Input id="name" name="name" value={form.name} onChange={handleChange} required />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="description">Description</Label>
                <Textarea id="description" name="description" value={form.description} onChange={handleChange} rows={2} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="coverImageUrl">Cover image URL (optional)</Label>
                <Input id="coverImageUrl" name="coverImageUrl" value={form.coverImageUrl} onChange={handleChange} />
              </div>
              {formError && <p role="alert" className="text-sm text-destructive">{formError}</p>}
              <Button type="submit" disabled={saving}>{saving ? 'Creating…' : 'Create club'}</Button>
            </form>
          </CardContent>
        </Card>
      )}

      {loadError && <p role="alert" className="mb-4 text-sm text-destructive">{loadError}</p>}

      {loading ? (
        <p className="text-muted-foreground">Loading clubs…</p>
      ) : (
        <Tabs value={active} onValueChange={setActive} className="flex w-full flex-col gap-6">
          <TabsList>
            {TABS.map((t) => (
              <TabsTrigger key={t.key} value={t.key}>
                {t.label} ({pages[t.key].totalElements})
              </TabsTrigger>
            ))}
          </TabsList>

          {TABS.map((t) => (
            <TabsContent key={t.key} value={t.key}>
              {pages[t.key].content.length === 0 ? (
                <div className="rounded-lg border border-dashed border-border p-10 text-center">
                  <p className="text-sm text-muted-foreground">{t.empty}</p>
                </div>
              ) : (
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {pages[t.key].content.map((club) => <ClubCard key={club.id} club={club} />)}
                </div>
              )}
              <Pagination
                page={pages[t.key].page}
                totalPages={pages[t.key].totalPages}
                onPageChange={(p) => changePage(t.key, p)}
              />
            </TabsContent>
          ))}
        </Tabs>
      )}
    </div>
  );
}
