import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '@/api/axios';
import { useAuth } from '@/context/AuthContext';
import { getErrorMessage } from '@/utils/errors';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Progress } from '@/components/ui/progress';
import { Textarea } from '@/components/ui/textarea';
import Pagination from '@/components/Pagination';

const emptyComments = { content: [], page: 0, totalPages: 0 };

export default function ClubDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [club, setClub] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [message, setMessage] = useState('');

  const [books, setBooks] = useState([]);
  const [myProgress, setMyProgress] = useState(null);
  const [clubProgress, setClubProgress] = useState([]);
  const [chapterInput, setChapterInput] = useState(0);

  const [selectedChapter, setSelectedChapter] = useState(1);
  const [comments, setComments] = useState(emptyComments);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [newComment, setNewComment] = useState('');

  const isMember = Boolean(club?.isCurrentUserMember);
  const isClubAdmin = Boolean(club?.isCurrentUserAdmin);
  const isCreator = club?.createdBy === user?.userId;
  const currentBook = club?.currentBook ?? null;
  const totalChapters = currentBook?.totalChapters ?? 0;

  const loadClub = useCallback(async () => {
    try {
      const res = await api.get(`/clubs/${id}`);
      setClub(res.data);
      setLoadError('');
    } catch (err) {
      setLoadError(getErrorMessage(err, 'Could not load this club.'));
    } finally {
      setLoading(false);
    }
  }, [id]);

  const loadProgress = useCallback(async () => {
    try {
      const mine = await api.get(`/clubs/${id}/progress/me`);
      const data = mine.data || null; // 204 No Content arrives as an empty string
      setMyProgress(data);
      setChapterInput(data?.currentChapter ?? 0);
    } catch {
      setMyProgress(null);
    }
    try {
      const all = await api.get(`/clubs/${id}/progress`);
      setClubProgress(Array.isArray(all.data) ? all.data : []);
    } catch {
      setClubProgress([]);
    }
  }, [id]);

  const loadComments = useCallback(async (chapter, page = 0) => {
    setCommentsLoading(true);
    try {
      const res = await api.get(`/clubs/${id}/comments`, { params: { chapter, page, size: 10 } });
      setComments(res.data);
    } catch {
      setComments(emptyComments);
    } finally {
      setCommentsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    setLoading(true);
    loadClub();
  }, [loadClub]);

  useEffect(() => {
    if (isClubAdmin) {
      api.get('/books').then((res) => setBooks(res.data)).catch(() => setBooks([]));
    }
  }, [isClubAdmin]);

  useEffect(() => {
    if (isMember && currentBook) loadProgress();
  }, [isMember, currentBook?.id, loadProgress]);

  useEffect(() => {
    if (isMember && currentBook) loadComments(selectedChapter, 0);
  }, [isMember, currentBook?.id, selectedChapter, loadComments]);

  const handleJoin = async () => {
    setMessage('');
    try {
      await api.post(`/clubs/${id}/join`);
      setMessage('You joined the club.');
      await loadClub();
    } catch (err) {
      setMessage(getErrorMessage(err, 'Could not join the club.'));
    }
  };

  const handleSetBook = async (bookId) => {
    setMessage('');
    try {
      const res = await api.put(`/clubs/${id}/current-book/${bookId}`);
      setClub(res.data);
      setSelectedChapter(1);
      setMessage('Current book updated.');
    } catch (err) {
      setMessage(getErrorMessage(err, 'Could not set the book.'));
    }
  };

  const handleUpdateProgress = async (e) => {
    e.preventDefault();
    setMessage('');
    try {
      await api.put(`/clubs/${id}/progress/me`, { currentChapter: Number(chapterInput) });
      await loadProgress();
      setMessage('Progress updated.');
    } catch (err) {
      setMessage(getErrorMessage(err, 'Could not update progress.'));
    }
  };

  const handlePostComment = async (e) => {
    e.preventDefault();
    if (!newComment.trim()) return;
    try {
      await api.post(`/clubs/${id}/comments`, { chapterNumber: selectedChapter, content: newComment });
      setNewComment('');
      await loadComments(selectedChapter, 0);
    } catch (err) {
      setMessage(getErrorMessage(err, 'Could not post your comment.'));
    }
  };

  const handleDeleteClub = async () => {
    if (!window.confirm('Delete this club? Its progress and discussion are removed too. This cannot be undone.')) return;
    try {
      await api.delete(`/clubs/${id}`);
      navigate('/clubs');
    } catch (err) {
      setMessage(getErrorMessage(err, 'Could not delete the club.'));
    }
  };

  if (loading) return <p className="text-muted-foreground">Loading…</p>;
  if (loadError || !club) return <p role="alert" className="text-destructive">{loadError || 'Club not found.'}</p>;

  const percent = (chapter) => (totalChapters > 0 ? (chapter / totalChapters) * 100 : 0);

  return (
    <div className="max-w-2xl">
      {club.coverImageUrl && (
        <img
          src={club.coverImageUrl}
          alt=""
          className="mb-6 h-48 w-full rounded-lg object-cover"
          onError={(e) => { e.currentTarget.style.display = 'none'; }}
        />
      )}

      <h1 className="font-display text-3xl font-semibold">{club.name}</h1>
      {club.description && <p className="mt-2 text-muted-foreground">{club.description}</p>}
      <p className="mt-2 text-xs text-muted-foreground">Started by {club.createdByName}</p>

      <div className="mt-5 flex gap-2">
        {!isMember && <Button onClick={handleJoin}>Join this club</Button>}
        {isCreator && <Button variant="destructive" onClick={handleDeleteClub}>Delete club</Button>}
      </div>

      {message && <p role="status" className="mt-3 text-sm">{message}</p>}

      <Card className="mt-8">
        <CardContent>
          <h2 className="mb-2 font-display font-semibold">Current book</h2>
          {currentBook ? (
            <p>{currentBook.title} <span className="text-muted-foreground">by {currentBook.author}</span></p>
          ) : (
            <p className="text-muted-foreground">No book chosen yet.</p>
          )}
        </CardContent>
      </Card>

      {currentBook && isMember && (
        <Card className="mt-4">
          <CardContent>
            <h2 className="mb-3 font-display font-semibold">Your progress</h2>
            <form onSubmit={handleUpdateProgress} className="mb-3 flex items-center gap-3">
              <Input
                type="number"
                min="0"
                max={totalChapters}
                value={chapterInput}
                onChange={(e) => setChapterInput(e.target.value)}
                className="w-24"
                aria-label="Chapter you have reached"
              />
              <span className="text-sm text-muted-foreground">of {totalChapters} chapters</span>
              <Button type="submit" size="sm">Update</Button>
            </form>
            <Progress value={percent(myProgress?.currentChapter ?? 0)} />

            <h3 className="mb-3 mt-6 font-display text-sm font-semibold">Everyone's progress</h3>
            <div className="space-y-3">
              {clubProgress.length === 0 ? (
                <p className="text-sm text-muted-foreground">No one has logged progress yet.</p>
              ) : (
                clubProgress.map((p) => (
                  <div key={p.id}>
                    <div className="mb-1 flex justify-between text-sm">
                      <span>{p.userName}</span>
                      <span className="text-muted-foreground">{p.currentChapter}/{p.totalChapters}</span>
                    </div>
                    <Progress value={percent(p.currentChapter)} />
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>
      )}

      {currentBook && isMember && (
        <Card className="mt-4">
          <CardContent>
            <div className="mb-4 flex items-center justify-between">
              <h2 className="font-display font-semibold">Chapter discussion</h2>
              <div className="flex items-center gap-2">
                <label htmlFor="chapter" className="text-sm text-muted-foreground">Chapter</label>
                <Input
                  id="chapter"
                  type="number"
                  min="1"
                  max={totalChapters}
                  value={selectedChapter}
                  onChange={(e) => setSelectedChapter(Math.max(1, Number(e.target.value) || 1))}
                  className="w-20"
                />
              </div>
            </div>

            <form onSubmit={handlePostComment} className="mb-5 space-y-2">
              <Textarea
                placeholder={`Share your thoughts on chapter ${selectedChapter}…`}
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
                rows={2}
              />
              <Button type="submit" size="sm" disabled={!newComment.trim()}>Post comment</Button>
            </form>

            {commentsLoading ? (
              <p className="text-sm text-muted-foreground">Loading comments…</p>
            ) : comments.content.length === 0 ? (
              <p className="text-sm text-muted-foreground">No comments on this chapter yet.</p>
            ) : (
              <>
                <div className="space-y-4">
                  {comments.content.map((c) => (
                    <div key={c.id} className="border-b border-border pb-3 last:border-0">
                      <div className="flex items-baseline justify-between">
                        <span className="text-sm font-medium">{c.userName}</span>
                        <span className="text-xs text-muted-foreground">{new Date(c.createdAt).toLocaleDateString()}</span>
                      </div>
                      <p className="mt-1 whitespace-pre-wrap text-sm">{c.content}</p>
                    </div>
                  ))}
                </div>
                <Pagination
                  page={comments.page}
                  totalPages={comments.totalPages}
                  onPageChange={(p) => loadComments(selectedChapter, p)}
                />
              </>
            )}
          </CardContent>
        </Card>
      )}

      {isClubAdmin && (
        <Card className="mt-4">
          <CardContent>
            <h2 className="mb-3 font-display font-semibold">Set the current book</h2>
            {books.length === 0 ? (
              <p className="text-sm text-muted-foreground">No books in the catalog yet.</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {books.map((book) => (
                  <Button key={book.id} variant="outline" size="sm" onClick={() => handleSetBook(book.id)}>
                    {book.title}
                  </Button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
