import { BriefcaseMedical, CalendarClock, Clock, ShieldCheck, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import * as hireApi from '../../api/hireApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import {
  dhakaInputToUtcIso,
  formatDhakaShiftRange,
  formatDurationLabel,
  toDhakaDateTimeInput,
} from '../../utils/dhakaTime';

const RATE_UNITS = [
  { key: 'HOURLY', label: 'Per Hour' },
  { key: 'DAILY', label: 'Per Day' },
  { key: 'FIXED', label: 'Fixed Total' },
];

function defaultStartAndEnd() {
  const now = new Date();
  // Round up to next 30-minute mark + 1 hour default start, 8-hour shift default duration
  const startMs = Math.ceil((now.getTime() + 60 * 60 * 1000) / (30 * 60 * 1000)) * (30 * 60 * 1000);
  const endMs = startMs + 8 * 60 * 60 * 1000;
  return {
    start: toDhakaDateTimeInput(new Date(startMs)),
    end: toDhakaDateTimeInput(new Date(endMs)),
  };
}

export function HireRequestModal({
  caretakerUserId,
  caretakerName,
  defaultRateAmount,
  defaultRateType,
  onClose,
  onSuccess,
}) {
  const defaults = useMemo(() => defaultStartAndEnd(), []);

  const [agreedRate, setAgreedRate] = useState(
    defaultRateAmount != null ? String(defaultRateAmount) : ''
  );
  const [rateType, setRateType] = useState(defaultRateType || 'DAILY');
  const [startInput, setStartInput] = useState(defaults.start);
  const [endInput, setEndInput] = useState(defaults.end);
  const [notes, setNotes] = useState('');

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  const [daySchedule, setDaySchedule] = useState(null);
  const [loadingSchedule, setLoadingSchedule] = useState(false);

  const selectedDhakaDate = startInput ? startInput.slice(0, 10) : '';

  useEffect(() => {
    if (!caretakerUserId || !selectedDhakaDate) {
      return undefined;
    }
    let cancelled = false;
    setLoadingSchedule(true);
    hireApi
      .fetchCaretakerDaySchedule(caretakerUserId, selectedDhakaDate)
      .then((data) => {
        if (!cancelled) {
          setDaySchedule(data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setDaySchedule(null);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingSchedule(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [caretakerUserId, selectedDhakaDate]);

  const startIso = dhakaInputToUtcIso(startInput);
  const endIso = dhakaInputToUtcIso(endInput);
  const durationLabel = formatDurationLabel(startIso, endIso);

  async function handleSubmit(event) {
    event.preventDefault();
    setError('');
    setFieldErrors({});

    const parsedRate = Number(agreedRate);
    if (!Number.isFinite(parsedRate) || parsedRate < 50) {
      setFieldErrors({ agreedRate: 'Please enter the agreed BDT rate (at least ৳50).' });
      return;
    }
    if (!startIso || !endIso) {
      setError('Please choose both start and end date/time.');
      return;
    }
    if (new Date(endIso).getTime() <= new Date(startIso).getTime()) {
      setError('End date/time must be after the start date/time.');
      return;
    }

    setSubmitting(true);
    try {
      const created = await hireApi.createHireContract({
        caretakerUserId: Number(caretakerUserId),
        agreedRate: parsedRate,
        rateType,
        scheduledStartTime: startIso,
        scheduledEndTime: endIso,
        notes: notes.trim() || null,
      });
      onSuccess(created);
    } catch (err) {
      setError(err.message || 'Could not send hire request. Please try again.');
      setFieldErrors(err.fieldErrors || {});
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="hire-modal-title"
      className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/50 p-0 backdrop-blur-xs sm:items-center sm:p-4"
    >
      <div className="max-h-[92dvh] w-full max-w-lg overflow-y-auto rounded-t-3xl bg-white p-5 shadow-xl sm:rounded-2xl sm:p-6">
        <div className="flex items-start justify-between gap-3 border-b border-slate-100 pb-3.5">
          <div className="flex items-center gap-2.5">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-teal-700 text-white">
              <BriefcaseMedical className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <h2 id="hire-modal-title" className="text-base font-bold text-slate-900">
                Formal Hire Request
              </h2>
              <p className="text-xs text-slate-600">
                Caretaker: <strong className="text-slate-900">{caretakerName}</strong>
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close hire form"
            className="inline-flex h-11 w-11 items-center justify-center rounded-xl text-slate-500 hover:bg-slate-100 hover:text-slate-900"
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-4 space-y-4">
          {error && <AlertBanner variant="error">{error}</AlertBanner>}

          <div className="space-y-1.5">
            <label htmlFor="hire-rate" className="block text-sm font-semibold text-slate-800">
              Agreed Rate (৳ BDT) <span className="text-rose-600">*</span>
            </label>
            <input
              id="hire-rate"
              type="number"
              min="50"
              max="1000000"
              step="10"
              required
              value={agreedRate}
              onChange={(e) => setAgreedRate(e.target.value)}
              placeholder="e.g., 2200"
              className="block min-h-[44px] w-full rounded-xl border border-slate-300 bg-white px-3.5 text-sm font-semibold text-slate-900 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
            />
            {fieldErrors.agreedRate && (
              <p className="text-xs font-medium text-rose-700">{fieldErrors.agreedRate}</p>
            )}
          </div>

          <div className="space-y-1.5">
            <span className="block text-sm font-semibold text-slate-800">
              Rate Unit <span className="text-rose-600">*</span>
            </span>
            <div className="grid grid-cols-3 gap-2">
              {RATE_UNITS.map((unit) => (
                <button
                  key={unit.key}
                  type="button"
                  onClick={() => setRateType(unit.key)}
                  className={`min-h-[44px] rounded-xl border px-3 text-xs font-bold transition-colors ${
                    rateType === unit.key
                      ? 'border-teal-700 bg-teal-700 text-white'
                      : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  {unit.label}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label htmlFor="hire-start" className="block text-xs font-semibold text-slate-700">
                Start Date &amp; Time (Dhaka) <span className="text-rose-600">*</span>
              </label>
              <input
                id="hire-start"
                type="datetime-local"
                required
                value={startInput}
                onChange={(e) => setStartInput(e.target.value)}
                className="block min-h-[44px] w-full rounded-xl border border-slate-300 bg-white px-3 text-sm text-slate-900 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
              />
            </div>

            <div className="space-y-1.5">
              <label htmlFor="hire-end" className="block text-xs font-semibold text-slate-700">
                End Date &amp; Time (Dhaka) <span className="text-rose-600">*</span>
              </label>
              <input
                id="hire-end"
                type="datetime-local"
                required
                value={endInput}
                onChange={(e) => setEndInput(e.target.value)}
                className="block min-h-[44px] w-full rounded-xl border border-slate-300 bg-white px-3 text-sm text-slate-900 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
              />
            </div>
          </div>

          {durationLabel && (
            <div className="inline-flex items-center gap-1.5 rounded-lg bg-slate-100 px-3 py-1.5 text-xs font-semibold text-slate-700">
              <Clock className="h-3.5 w-3.5 text-teal-700" aria-hidden="true" />
              <span>Shift duration: {durationLabel}</span>
            </div>
          )}

          {/* Privacy-safe live daily booking cap indicator */}
          <div
            className={`rounded-xl border p-3 text-xs ${
              daySchedule?.dailyCapReached
                ? 'border-amber-300 bg-amber-50 text-amber-950'
                : 'border-slate-200 bg-slate-50 text-slate-700'
            }`}
          >
            <div className="flex items-center justify-between gap-2 font-semibold">
              <span className="inline-flex items-center gap-1.5">
                <CalendarClock className="h-4 w-4 text-teal-700" aria-hidden="true" />
                <span>Caretaker schedule on {selectedDhakaDate || 'selected day'}</span>
              </span>
              <span>
                {loadingSchedule
                  ? 'Checking...'
                  : daySchedule
                    ? `${daySchedule.bookingsCount}/${daySchedule.maxBookingsPerDay} booked`
                    : 'Max 2/day'}
              </span>
            </div>

            {daySchedule?.dailyCapReached ? (
              <p className="mt-1.5 font-medium text-amber-900">
                This caretaker has already reached the 2-bookings-per-day limit on{' '}
                {daySchedule.dhakaDate}. Choose another date or another available caretaker.
              </p>
            ) : daySchedule?.busySlots?.length > 0 ? (
              <div className="mt-1.5 space-y-1">
                <p className="text-[11px] font-semibold text-slate-600">Already booked slots:</p>
                {daySchedule.busySlots.map((slot, idx) => (
                  <div
                    key={`${slot.scheduledStartTime}-${idx}`}
                    className="rounded-lg bg-white px-2.5 py-1 text-[11px] font-medium text-slate-800 border border-slate-200"
                  >
                    {formatDhakaShiftRange(slot.scheduledStartTime, slot.scheduledEndTime)}
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-1 text-[11px] text-slate-500">
                Open schedule on this date. Caretakers accept up to 2 non-overlapping shifts per
                Dhaka calendar day.
              </p>
            )}
          </div>

          <div className="space-y-1.5">
            <label htmlFor="hire-notes" className="block text-sm font-semibold text-slate-800">
              Hospital &amp; Ward Note <span className="text-xs font-normal text-slate-500">(optional)</span>
            </label>
            <textarea
              id="hire-notes"
              rows={2}
              maxLength={1000}
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="e.g., Evercare Hospital, Ward 5B Cabin 12 — Post-surgery elderly care"
              className="block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
            />
          </div>

          <div className="flex items-start gap-2 rounded-xl bg-teal-50/70 px-3 py-2.5 text-[11px] text-teal-900">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-teal-700" aria-hidden="true" />
            <span>
              Your request stays <strong>Awaiting Acceptance</strong> until {caretakerName} taps
              Accept. Either party can cancel before service starts or end an active shift early if
              hospital plans change.
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2.5 pt-1">
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              loading={submitting}
              disabled={daySchedule?.dailyCapReached}
            >
              Send Hire Request
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
