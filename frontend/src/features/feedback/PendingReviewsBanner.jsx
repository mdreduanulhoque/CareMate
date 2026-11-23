import { Star } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { formatDhakaDateTime } from '../../utils/dhakaTime';

export function PendingReviewsBanner({ pendingReviews, onOpenReview }) {
  if (!pendingReviews || pendingReviews.length === 0) {
    return null;
  }

  return (
    <section className="space-y-3 rounded-2xl border border-amber-200 bg-amber-50/80 p-4 shadow-xs">
      <div className="flex items-start gap-2.5">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-amber-500 text-white">
          <Star className="h-4 w-4 fill-white" aria-hidden="true" />
        </div>
        <div>
          <h2 className="text-sm font-bold text-amber-950">
            {pendingReviews.length} Pending Feedback Form{pendingReviews.length > 1 ? 's' : ''}
          </h2>
          <p className="text-xs text-amber-900">
            Fill immediately or whenever you have a moment. Reviews stay editable for 7 days.
          </p>
        </div>
      </div>

      <div className="space-y-2">
        {pendingReviews.map((review) => (
          <div
            key={review.id}
            className="flex flex-wrap items-center justify-between gap-2.5 rounded-xl border border-amber-200/80 bg-white p-3"
          >
            <div className="min-w-0">
              <div className="truncate text-sm font-bold text-slate-900">
                Rate {review.revieweeName}
              </div>
              <div className="text-xs text-slate-500">
                Contract #{review.contractId} · Ended{' '}
                {formatDhakaDateTime(review.contractActualEndTime || review.contractEndTime)}
              </div>
            </div>
            <Button onClick={() => onOpenReview(review)} className="shrink-0">
              <Star className="h-4 w-4" aria-hidden="true" />
              <span>Leave Feedback</span>
            </Button>
          </div>
        ))}
      </div>
    </section>
  );
}
