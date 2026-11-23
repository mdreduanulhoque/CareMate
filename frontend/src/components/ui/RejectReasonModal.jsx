import { X } from 'lucide-react';
import { useState } from 'react';
import { AlertBanner } from './AlertBanner';
import { Button } from './Button';

const QUICK_REASONS = [
  "Google Drive link is private. Please enable 'Anyone with the link can view'.",
  'NID image is blurry or unreadable. Please upload a clearer scan.',
  'Required nursing/training certificate is missing from the Drive folder.',
];

export function RejectReasonModal({ title, subtitle, submitting, error, onClose, onConfirm }) {
  const [reason, setReason] = useState('');
  const [localError, setLocalError] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    const trimmed = reason.trim();
    if (trimmed.length < 5) {
      setLocalError('Please enter a clear rejection reason (at least 5 characters).');
      return;
    }
    setLocalError('');
    onConfirm(trimmed);
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="reject-modal-title"
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-3 sm:items-center"
    >
      <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-5 shadow-xl">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 id="reject-modal-title" className="text-lg font-bold text-slate-900">
              {title}
            </h2>
            {subtitle && <p className="mt-0.5 text-xs text-slate-600">{subtitle}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close modal"
            className="inline-flex h-10 w-10 items-center justify-center rounded-xl text-slate-500 hover:bg-slate-100"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {(error || localError) && (
            <AlertBanner variant="error">{error || localError}</AlertBanner>
          )}

          <div className="space-y-1.5">
            <label htmlFor="rejection-reason" className="block text-sm font-medium text-slate-800">
              Mandatory Rejection Reason
            </label>
            <textarea
              id="rejection-reason"
              rows={4}
              value={reason}
              onChange={(e) => {
                setReason(e.target.value);
                setLocalError('');
              }}
              placeholder="Explain clearly what needs to be fixed so the caretaker can re-apply accurately..."
              className="block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
              required
            />
          </div>

          <div className="space-y-1.5">
            <p className="text-xs font-semibold text-slate-500">Quick reason templates</p>
            <div className="flex flex-wrap gap-1.5">
              {QUICK_REASONS.map((preset) => (
                <button
                  key={preset}
                  type="button"
                  onClick={() => {
                    setReason(preset);
                    setLocalError('');
                  }}
                  className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1.5 text-left text-xs text-slate-700 hover:border-teal-300 hover:bg-teal-50"
                >
                  {preset}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2.5 pt-1">
            <Button variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" variant="danger" loading={submitting}>
              Confirm Reject
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
