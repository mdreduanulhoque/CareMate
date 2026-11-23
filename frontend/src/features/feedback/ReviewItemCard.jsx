import { Edit3, Lock, Star, ThumbsDown, ThumbsUp } from 'lucide-react';
import { formatDhakaDateTime } from '../../utils/dhakaTime';

export function ReviewItemCard({ review, onEdit }) {
  const isCustomerReviewer = review.reviewerRole === 'CUSTOMER';
  const repeatLabel = isCustomerReviewer
    ? review.wouldEngageAgain
      ? 'Would Hire Again'
      : 'Would Not Hire Again'
    : review.wouldEngageAgain
      ? 'Would Work Again'
      : 'Would Not Work Again';

  return (
    <article className="space-y-2.5 rounded-xl border border-slate-200 bg-slate-50/70 p-3.5 text-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-bold text-slate-900">{review.reviewerName}</span>
            <span className="inline-flex items-center gap-0.5" aria-label={`${review.rating} out of 5 stars`}>
              {[1, 2, 3, 4, 5].map((i) => (
                <Star
                  key={i}
                  className={`h-3.5 w-3.5 ${
                    i <= (review.rating || 0)
                      ? 'fill-amber-400 text-amber-400'
                      : 'text-slate-300'
                  }`}
                />
              ))}
            </span>
            {review.edited && (
              <span className="inline-flex items-center rounded-full border border-amber-200 bg-amber-50 px-2 py-0.5 text-[10px] font-bold text-amber-900">
                Edited
              </span>
            )}
          </div>

          <div className="flex flex-wrap items-center gap-2 text-[11px] text-slate-500">
            <span>{formatDhakaDateTime(review.submittedAt)}</span>
            {review.edited && review.updatedAt && (
              <span>· Updated {formatDhakaDateTime(review.updatedAt)}</span>
            )}
          </div>
        </div>

        <span
          className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-bold ${
            review.wouldEngageAgain
              ? 'bg-emerald-100 text-emerald-800'
              : 'bg-rose-100 text-rose-800'
          }`}
        >
          {review.wouldEngageAgain ? (
            <ThumbsUp className="h-3 w-3" aria-hidden="true" />
          ) : (
            <ThumbsDown className="h-3 w-3" aria-hidden="true" />
          )}
          <span>{repeatLabel}</span>
        </span>
      </div>

      {review.comment && (
        <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-slate-700">
          {review.comment}
        </p>
      )}

      {(review.canEdit || review.locked) && onEdit && (
        <div className="flex items-center justify-between gap-2 border-t border-slate-200/70 pt-2">
          {review.canEdit ? (
            <>
              <span className="text-[11px] text-slate-500">
                Editable until {formatDhakaDateTime(review.editableUntil)}
              </span>
              <button
                type="button"
                onClick={() => onEdit(review)}
                className="inline-flex min-h-[44px] items-center gap-1.5 rounded-lg border border-teal-200 bg-white px-3.5 text-xs font-bold text-teal-800 hover:bg-teal-50"
              >
                <Edit3 className="h-3.5 w-3.5" aria-hidden="true" />
                <span>Edit Review</span>
              </button>
            </>
          ) : (
            <span className="inline-flex items-center gap-1 text-[11px] font-medium text-slate-500">
              <Lock className="h-3 w-3" aria-hidden="true" />
              <span>Locked (7-day editing window ended)</span>
            </span>
          )}
        </div>
      )}
    </article>
  );
}
