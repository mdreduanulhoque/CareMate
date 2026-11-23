import { AlertTriangle, Banknote, X } from 'lucide-react';
import { useState } from 'react';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';

export function PaymentActionModal({ mode, contract, submitting, error, onClose, onConfirm }) {
  const isRefuse = mode === 'caretakerRefuse';
  const [text, setText] = useState('');
  const [localError, setLocalError] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    const trimmed = text.trim();
    if (isRefuse && trimmed.length < 5) {
      setLocalError('Please provide a clear statement (at least 5 characters) explaining the issue.');
      return;
    }
    setLocalError('');
    onConfirm(trimmed);
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="payment-modal-title"
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-0 backdrop-blur-xs sm:items-center sm:p-4"
    >
      <div className="w-full max-w-md rounded-t-3xl bg-white p-5 shadow-xl sm:rounded-2xl sm:p-6">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-start gap-2.5">
            <div
              className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${
                isRefuse ? 'bg-rose-100 text-rose-700' : 'bg-emerald-100 text-emerald-800'
              }`}
            >
              {isRefuse ? (
                <AlertTriangle className="h-5 w-5" aria-hidden="true" />
              ) : (
                <Banknote className="h-5 w-5" aria-hidden="true" />
              )}
            </div>
            <div>
              <h2 id="payment-modal-title" className="text-base font-bold text-slate-900">
                {isRefuse ? 'Refuse Payment & Open Admin Dispute' : 'Mark Payment Done'}
              </h2>
              <p className="text-xs text-slate-600">
                Contract #{contract.id} · ৳{Number(contract.agreedRate).toLocaleString('en-BD')}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close payment modal"
            className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-500 hover:bg-slate-100"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <p className="mt-3 text-xs leading-relaxed text-slate-600">
          {isRefuse
            ? 'Opening a dispute immediately alerts an Administrator and launches a 3-Way WebSocket Dispute Mediation Room with you, the relative, and an Admin Mediator.'
            : 'Since payments happen directly via Cash, bKash, Nagad, or Bank Transfer, share an optional transaction ID or note so the caretaker can verify and close this contract cleanly.'}
        </p>

        <form onSubmit={handleSubmit} className="mt-4 space-y-3.5">
          {(error || localError) && <AlertBanner variant="error">{error || localError}</AlertBanner>}

          <div className="space-y-1.5">
            <label htmlFor="payment-note-input" className="block text-sm font-semibold text-slate-800">
              {isRefuse ? (
                <>
                  Explanation / Statement <span className="text-rose-600">*</span>
                </>
              ) : (
                <>
                  Payment Method / TrxID Note{' '}
                  <span className="text-xs font-normal text-slate-500">(optional)</span>
                </>
              )}
            </label>
            <textarea
              id="payment-note-input"
              rows={3}
              maxLength={isRefuse ? 2000 : 500}
              required={isRefuse}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder={
                isRefuse
                  ? 'e.g., Relative marked bKash paid, but no transfer arrived on my number after checking statement.'
                  : 'e.g., Paid 2,200 BDT via bKash TrxID 9X2A8B4K (or Paid cash at hospital ward)'
              }
              className="block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
            />
          </div>

          <div className="grid grid-cols-2 gap-2.5 pt-1">
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant={isRefuse ? 'danger' : 'primary'}
              loading={submitting}
            >
              {isRefuse ? 'Open Dispute Room' : 'Confirm Payment Done'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
