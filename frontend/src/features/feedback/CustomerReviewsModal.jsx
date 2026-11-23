import { ShieldCheck, Star, ThumbsUp, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import * as reviewApi from '../../api/reviewApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { ReviewItemCard } from './ReviewItemCard';

export function CustomerReviewsModal({ customerUserId, customerName, onClose }) {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    reviewApi
      .fetchCustomerReviews(customerUserId)
      .then((data) => {
        if (!cancelled) {
          setSummary(data);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err.message || 'Could not load past caretaker reviews for this relative.');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [customerUserId]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="customer-reviews-title"
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-0 backdrop-blur-xs sm:items-center sm:p-4"
    >
      <div className="max-h-[88dvh] w-full max-w-lg overflow-y-auto rounded-t-3xl bg-white p-5 shadow-xl sm:rounded-2xl sm:p-6">
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 pb-3.5">
          <div className="flex items-center gap-2.5">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-sky-700 text-white">
              <ShieldCheck className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <h2 id="customer-reviews-title" className="text-base font-bold text-slate-900">
                Caretaker Reviews of {customerName}
              </h2>
              <p className="text-xs text-slate-600">
                Two-way accountability: past feedback left by verified caretakers
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close customer reviews"
            className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-500 hover:bg-slate-100"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <div className="mt-4 space-y-4">
          {loading ? (
            <p className="py-6 text-center text-sm text-slate-500">Loading caretaker feedback...</p>
          ) : error ? (
            <AlertBanner variant="error">{error}</AlertBanner>
          ) : summary ? (
            <>
              <div className="grid grid-cols-2 gap-2.5 text-center">
                <div className="rounded-xl bg-amber-50 p-3">
                  <div className="flex items-center justify-center gap-1 text-base font-bold text-amber-950">
                    <Star className="h-4 w-4 fill-amber-400 text-amber-400" />
                    <span>
                      {summary.totalReviews > 0 ? Number(summary.avgRating).toFixed(1) : 'New'}
                    </span>
                  </div>
                  <div className="text-[11px] font-medium text-amber-900">
                    {summary.totalReviews} Caretaker Review{summary.totalReviews === 1 ? '' : 's'}
                  </div>
                </div>

                <div className="rounded-xl bg-teal-50 p-3">
                  <div className="flex items-center justify-center gap-1 text-base font-bold text-teal-950">
                    <ThumbsUp className="h-3.5 w-3.5" />
                    <span>
                      {summary.totalReviews > 0
                        ? `${Math.round(Number(summary.wouldEngageAgainPercentage))}%`
                        : '—'}
                    </span>
                  </div>
                  <div className="text-[11px] font-medium text-teal-900">Would Work Again</div>
                </div>
              </div>

              {summary.reviews.length === 0 ? (
                <div className="rounded-xl border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500">
                  No caretaker reviews recorded for this relative yet.
                </div>
              ) : (
                <div className="space-y-3">
                  {summary.reviews.map((review) => (
                    <ReviewItemCard key={review.id} review={review} />
                  ))}
                </div>
              )}
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}
