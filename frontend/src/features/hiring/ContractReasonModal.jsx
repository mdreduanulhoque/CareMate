import { AlertTriangle, X } from 'lucide-react';
import { useState } from 'react';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';

const MODE_CONFIG = {
  decline: {
    title: 'Decline Hire Request',
    helper: 'Optionally share a brief note so the patient’s family can plan promptly.',
    placeholder: 'e.g., Already committed to another hospital area at that hour (optional)',
    confirmLabel: 'Confirm Decline',
    required: false,
    variant: 'danger',
  },
  cancel: {
    title: 'Cancel Booking Before Start',
    helper:
      'Cancelling immediately frees the caretaker’s daily booking slot so another family can book.',
    placeholder: 'e.g., Family member will stay overnight instead',
    confirmLabel: 'Confirm Cancellation',
    required: true,
    variant: 'danger',
  },
  endEarly: {
    title: 'End Service Early',
    helper:
      'We will record the exact stop time now, free the caretaker’s remaining schedule, and move this contract directly to settlement (dual payment confirmation & mutual feedback).',
    placeholder: 'e.g., Doctor approved early hospital discharge today',
    confirmLabel: 'End Shift & Move to Settlement',
    required: true,
    variant: 'primary',
  },
};

export function ContractReasonModal({ mode, contract, submitting, error, onClose, onConfirm }) {
  const config = MODE_CONFIG[mode] || MODE_CONFIG.cancel;
  const [reason, setReason] = useState('');
  const [localError, setLocalError] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    const trimmed = reason.trim();
    if (config.required && trimmed.length < 3) {
      setLocalError('Please enter a brief reason (at least 3 characters).');
      return;
    }
    setLocalError('');
    onConfirm(trimmed);
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="contract-reason-title"
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-0 backdrop-blur-xs sm:items-center sm:p-4"
    >
      <div className="w-full max-w-md rounded-t-3xl bg-white p-5 shadow-xl sm:rounded-2xl sm:p-6">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-2.5">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-amber-800">
              <AlertTriangle className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <h2 id="contract-reason-title" className="text-base font-bold text-slate-900">
                {config.title}
              </h2>
              <p className="text-xs text-slate-600">Contract #{contract.id}</p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close modal"
            className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-500 hover:bg-slate-100"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <p className="mt-3 text-xs leading-relaxed text-slate-600">{config.helper}</p>

        <form onSubmit={handleSubmit} className="mt-4 space-y-3.5">
          {(error || localError) && <AlertBanner variant="error">{error || localError}</AlertBanner>}

          <div className="space-y-1.5">
            <label htmlFor="contract-reason-input" className="block text-sm font-semibold text-slate-800">
              Reason {config.required ? <span className="text-rose-600">*</span> : '(optional)'}
            </label>
            <textarea
              id="contract-reason-input"
              rows={3}
              maxLength={1000}
              required={config.required}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder={config.placeholder}
              className="block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
            />
          </div>

          <div className="grid grid-cols-2 gap-2.5 pt-1">
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Back
            </Button>
            <Button type="submit" variant={config.variant} loading={submitting}>
              {config.confirmLabel}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
