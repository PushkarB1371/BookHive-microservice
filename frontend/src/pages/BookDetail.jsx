import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import api from '@/api/axios';
import { useAuth } from '@/context/AuthContext';
import { getErrorMessage } from '@/utils/errors';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import Pagination from '@/components/Pagination';
import StarRating from '@/components/StarRating';

const emptyReviews = { content: [], page: 0, totalPages: 0, totalElements: 0 };

export default function BookDetail() {
  const { id } = useParams();
  const { isAdmin } = useAuth();
  const navigate = useNavigate();

  const [book, setBook] = useState(null);
  const [clubs, setClubs] = useState([]);
  const [reviews, setReviews] = useState(emptyReviews);
  const [averageRating, setAverageRating] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [rating, setRating] = useState(0);
  const [reviewText, setReviewText] = useState('');
  const [reviewMessage, setReviewMessage] = useState('');

  const loadReviews = useCallback(async (page = 0) => {
    const res = await api.get(`/books/${id}/reviews`, { params: { page, size: 5 } });
    setReviews(res.data.reviews);
    setAverageRating(res.data.averageRating);
  }, [id]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [bookRes, clubsRes] = await Promise.all([
        api.get(`/books/${id}`),
        api.get(`/clubs/reading/${id}`),
      ]);
      setBook(bookRes.data);
      setClubs(clubsRes.data);
      await loadReviews(0);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load this book.'));
    } finally {
      setLoading(false);
    }
  }, [id, loadReviews]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const handleSubmitReview = async (e) => {
    e.preventDefault();
    setReviewMessage('');
    if (rating === 0) {
      setReviewMessage('Pick a star rating first.');
      return;
    }
    try {
      await api.post(`/books/${id}/reviews`, { rating, reviewText });
      setRating(0);
      setReviewText('');
      await loadReviews(0);
    } catch (err) {
      setReviewMessage(getErrorMessage(err, 'Could not submit your review.'));
    }
  };

  const handleDeleteBook = async () => {
    if (!window.confirm('Delete this book? This cannot be undone.')) return;
    try {
      await api.delete(`/books/${id}`);
      navigate('/books');
    } catch (err) {
      setError(getErrorMessage(err, 'Could not delete the book.'));
    }
  };

  if (loading) return <p className="text-muted-foreground">Loading…</p>;
  if (!book) return <p role="alert" className="text-destructive">{error || 'Book not found.'}</p>;

  return (
    <div className="max-w-3xl">
      {error && <p role="alert" className="mb-4 text-sm text-destructive">{error}</p>}

      <div className="grid grid-cols-1 gap-8 sm:grid-cols-[180px_1fr]">
        {book.coverImageUrl ? (
          <img
            src={book.coverImageUrl}
            alt={`Cover of ${book.title}`}
            className="h-64 w-44 rounded-sm object-cover shadow-md"
            onError={(e) => { e.currentTarget.style.display = 'none'; }}
          />
        ) : (
          <div className="flex h-64 w-44 items-center justify-center rounded-sm bg-secondary px-3 text-center">
            <span className="font-display text-sm text-secondary-foreground/60">{book.title}</span>
          </div>
        )}

        <div className="flex flex-col justify-end">
          <h1 className="font-display text-4xl leading-tight text-foreground">{book.title}</h1>
          <p className="mt-2 text-muted-foreground">by {book.author}</p>

          <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
            {book.category && <span>{book.category}</span>}
            {book.publishedDate && <span>{book.publishedDate}</span>}
            <span>{book.totalChapters} chapters</span>
          </div>

          {averageRating != null && (
            <p className="mt-3 text-sm font-medium text-accent">
              ★ {Number(averageRating).toFixed(1)} from {reviews.totalElements} review{reviews.totalElements === 1 ? '' : 's'}
            </p>
          )}

          {isAdmin && (
            <button type="button" onClick={handleDeleteBook} className="mt-4 w-fit text-xs text-destructive hover:underline">
              Delete this book
            </button>
          )}
        </div>
      </div>

      {book.description && (
        <div className="mt-10 border-t border-rule pt-8">
          <p className="drop-cap max-w-xl text-[15px] leading-relaxed text-foreground">{book.description}</p>
        </div>
      )}

      <div className="mt-10 border-t border-rule pt-8">
        <h2 className="mb-4 font-display text-xl text-foreground">Clubs reading this</h2>
        {clubs.length === 0 ? (
          <p className="text-sm text-muted-foreground">No club is reading this book right now.</p>
        ) : (
          <div className="space-y-2">
            {clubs.map((club) => (
              <Link key={club.id} to={`/clubs/${club.id}`} className="block text-sm transition-colors hover:text-primary">
                <span className="font-medium">{club.name}</span>
                {club.description && <span className="text-muted-foreground"> — {club.description}</span>}
              </Link>
            ))}
          </div>
        )}
      </div>

      <div className="mt-10 border-t border-rule pt-8">
        <h2 className="mb-4 font-display text-xl text-foreground">Leave a review</h2>
        <form onSubmit={handleSubmitReview} className="max-w-md space-y-3">
          <StarRating value={rating} onChange={setRating} />
          <Textarea
            placeholder="What did you think of this book?"
            value={reviewText}
            onChange={(e) => setReviewText(e.target.value)}
            rows={3}
          />
          {reviewMessage && <p role="alert" className="text-sm text-destructive">{reviewMessage}</p>}
          <Button type="submit" size="sm">Submit review</Button>
        </form>
      </div>

      <div className="mt-10 border-t border-rule pb-16 pt-8">
        <h2 className="mb-4 font-display text-xl text-foreground">Reviews</h2>
        {reviews.content.length === 0 ? (
          <p className="text-sm text-muted-foreground">No reviews yet. Be the first.</p>
        ) : (
          <>
            <div className="space-y-5">
              {reviews.content.map((r) => (
                <div key={r.id} className="border-b border-rule pb-5 last:border-0">
                  <div className="flex items-baseline justify-between">
                    <span className="text-sm font-medium">{r.userName}</span>
                    <span className="text-sm text-accent" aria-label={`${r.rating} out of 5 stars`}>
                      {'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}
                    </span>
                  </div>
                  {r.reviewText && <p className="mt-1 whitespace-pre-wrap text-sm text-foreground">{r.reviewText}</p>}
                </div>
              ))}
            </div>
            <Pagination page={reviews.page} totalPages={reviews.totalPages} onPageChange={loadReviews} />
          </>
        )}
      </div>
    </div>
  );
}
