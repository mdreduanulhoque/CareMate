import { Clock, Star, ThumbsDown, ThumbsUp, X } from 'lucide-react';
import { useState } from 'react';
import * as reviewApi from '../../api/reviewApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { formatDhakaDateTime } from '../../utils/dhakaTime';

export function ReviewFormModal({ review, onClose, onSaved }) {
  const isEditing = Boolean(review.submitted);
  const isCustomerReviewer = review.reviewerRole === 'CUSTOMER';

  const [rating, setRating] = useState(review.rating || 5);
  const [wouldEngageAgain, setWouldEngageAgain] = useState(
    review.wouldEngageAgain != null ? review.wouldEngageAgain : true
  );
  const [comment, setComment] = useState(review.comment || '');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const questionText = isCustomerReviewer
    ? 'Will you hire this caretaker again?'
    : 'Will you work for this customer again?';

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const payload = {
        rating,
        comment: comment.trim() || null,
        wouldEngageAgain,
      };
      const saved = isEditing
        ? await reviewApi.editReview(review.id, payload)
        : await reviewApi.submitReview(review.id, payload);
      onSaved(saved);
    } catch (err) {
      setError(err.message || 'Could not save your feedback right now. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="review-modal-title"
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-0 backdrop-blur-xs sm:items-center sm:p-4"
    >
      <div className="max-h-[92dvh] w-full max-w-md overflow-y-auto rounded-t-3xl bg-white p-5 shadow-xl sm:rounded-2xl sm:p-6">
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 pb-3.5">
          <div>
            <h2 id="review-modal-title" className="text-base font-bold text-slate-900">
              {isEditing ? 'Edit Your Feedback' : 'Leave Post-Service Feedback'}
            </h2>
            <p className="text-xs text-slate-600">
              For <strong className="text-slate-900">{review.revieweeName}</strong> · Contract #
              {review.contractId}
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close feedback modal"
            className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-500 hover:bg-slate-100"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {error && <AlertBanner variant="error">{error}</AlertBanner>}

          <div className="space-y-1.5">
            <span className="block text-sm font-semibold text-slate-800">
              Star Rating <span className="text-rose-600">*</span>
            </span>
            <div className="flex items-center gap-1.5" role="radiogroup" aria-label="Star rating">
              {[1, 2, 3, 4, 5].map((starValue) => {
                const active = starValue <= rating;
                return (
                  <button
                    key={starValue}
                    type="button"
                    role="radio"
                    aria-checked={rating === starValue}
                    aria-label={`${starValue} star${starValue > 1 ? 's' : ''}`}
                    onClick={() => setRating(starValue)}
                    className="inline-flex h-11 w-11 items-center justify-center rounded-xl border border-slate-200 bg-slate-50 transition-colors hover:border-amber-400 hover:bg-amber-50"
                  >
                    <Star
                      className={`h-6 w-6 ${
                        active ? 'fill-amber-400 text-amber-400' : 'text-slate-300'
                      }`}
                    />
                  </button>
                );
              })}
              <span className="ml-2 text-sm font-bold text-slate-800">{rating} / 5</span>
            </div>
          </div>

          <div className="space-y-1.5">
            <span className="block text-sm font-semibold text-slate-800">
              {questionText} <span className="text-rose-600">*</span>
            </span>
            <div className="grid grid-cols-2 gap-2.5">
              <button
                type="button"
                onClick={() => setWouldEngageAgain(true)}
                className={`inline-flex min-h-[44px] items-center justify-center gap-2 rounded-xl border px-3 text-xs font-bold transition-colors ${
                  wouldEngageAgain === true
                    ? 'border-emerald-700 bg-emerald-700 text-white'
                    : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                }`}
              >
                <ThumbsUp className="h-4 w-4" aria-hidden="true" />
                <span>Yes</span>
              </button>
              <button
                type="button"
                onClick={() => setWouldEngageAgain(false)}
                className={`inline-flex min-h-[44px] items-center justify-center gap-2 rounded-xl border px-3 text-xs font-bold transition-colors ${
                  wouldEngageAgain === false
                    ? 'border-rose-700 bg-rose-700 text-white'
                    : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                }`}
              >
                <ThumbsDown className="h-4 w-4" aria-hidden="true" />
                <span>No</span>
              </button>
            </div>
          </div>

          <div className="space-y-1.5">
            <label htmlFor="review-comment" className="block text-sm font-semibold text-slate-800">
              Written Review <span className="text-xs font-normal text-slate-500">(optional)</span>
            </label>
            <textarea
              id="review-comment"
              rows={3}
              maxLength={2000}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder={
                isCustomerReviewer
                  ? 'Share punctuality, bedside care quality, and communication...'
                  : 'Share hospital readiness, respectfulness, and payment clarity...'
              }
              className="block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
            />
          </div>

          <div className="flex items-start gap-2 rounded-xl bg-slate-50 px-3 py-2.5 text-[11px] text-slate-600 border border-slate-200">
            <Clock className="mt-0.5 h-4 w-4 shrink-0 text-teal-700" aria-hidden="true" />
            <span>
              {isEditing ? (
                <>
                  Editable until <strong>{formatDhakaDateTime(review.editableUntil)}</strong>{' '}
                  (Dhaka). Edited reviews display an <strong>Edited</strong> badge and lock
                  permanently after 7 days.
                </>
              ) : (
                <>
                  After submitting, you may edit your review for up to <strong>7 days</strong>{' '}
                  (shown with an <strong>Edited</strong> badge) before it locks permanently.
                </>
              )}
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2.5 pt-1">
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" loading={submitting}>
              {isEditing ? 'Save Changes' : 'Submit Feedback'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
