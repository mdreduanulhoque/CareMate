import {
  CalendarClock,
  Check,
  Clock,
  FileText,
  MapPin,
  MessageSquare,
  Phone,
  ShieldCheck,
  Star,
  StopCircle,
  ThumbsUp,
  User,
  X,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { CaretakerAvatar } from '../../components/ui/CaretakerAvatar';
import { StatusBadge } from '../../components/ui/StatusBadge';
import {
  formatDhakaDateTime,
  formatDhakaShiftRange,
  formatDurationLabel,
} from '../../utils/dhakaTime';

function formatContractRate(rate, rateType) {
  const formatted = Number(rate).toLocaleString('en-BD');
  if (rateType === 'HOURLY') return `৳${formatted} / hour`;
  if (rateType === 'DAILY') return `৳${formatted} / day`;
  return `৳${formatted} fixed`;
}

export function ContractCard({
  contract,
  viewerRole,
  busyAction,
  onAccept,
  onDecline,
  onCancel,
  onEndEarly,
}) {
  const isCustomer = viewerRole === 'CUSTOMER';
  const isCaretaker = viewerRole === 'CARETAKER';

  const counterpartName = isCustomer ? contract.caretakerFullName : contract.customerFullName;
  const counterpartPhone = isCustomer
    ? contract.caretakerMobileNumber
    : contract.customerMobileNumber;
  const counterpartLocation = isCustomer
    ? contract.caretakerLocationArea
    : contract.customerAddress;

  const chatBasePath = isCustomer ? '/customer/chats' : '/caretaker/chats';
  const durationLabel = formatDurationLabel(
    contract.scheduledStartTime,
    contract.scheduledEndTime
  );

  return (
    <article className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-xs sm:p-5">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex min-w-0 items-start gap-3">
          {isCustomer ? (
            <CaretakerAvatar
              src={contract.caretakerProfileImageUrl}
              name={contract.caretakerFullName}
              size="sm"
            />
          ) : (
            <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-teal-700 text-base font-bold text-white">
              {contract.customerFullName?.slice(0, 1)?.toUpperCase() || (
                <User className="h-5 w-5" />
              )}
            </div>
          )}

          <div className="min-w-0 space-y-0.5">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="truncate text-base font-bold text-slate-900">{counterpartName}</h2>
              <span className="rounded-md bg-slate-100 px-2 py-0.5 text-[11px] font-bold text-slate-600">
                #{contract.id}
              </span>
            </div>

            {isCustomer && contract.caretakerPrimaryQualification && (
              <p className="truncate text-xs font-semibold text-teal-800">
                {contract.caretakerPrimaryQualification}
              </p>
            )}

            <p className="inline-flex items-center gap-1 text-xs text-slate-600">
              <MapPin className="h-3.5 w-3.5 shrink-0 text-slate-400" aria-hidden="true" />
              <span className="truncate">{counterpartLocation}</span>
            </p>
          </div>
        </div>

        <StatusBadge status={contract.status} />
      </div>

      {/* Customer Trust Preview shown to Caretakers evaluating incoming hire requests (Section 5.2 step 7) */}
      {isCaretaker && contract.status === 'REQUESTED' && (
        <div className="rounded-xl border border-sky-200 bg-sky-50/70 p-3 text-xs space-y-1.5">
          <div className="flex items-center gap-1.5 font-bold text-sky-950">
            <ShieldCheck className="h-4 w-4 text-sky-700" aria-hidden="true" />
            <span>Relative Trust Preview</span>
          </div>
          <div className="flex flex-wrap items-center gap-2 text-sky-900">
            <span className="inline-flex items-center gap-1 rounded-lg bg-white px-2 py-1 font-semibold border border-sky-100">
              <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" />
              {contract.customerReviewCount > 0
                ? `${Number(contract.customerAvgRating).toFixed(1)} (${contract.customerReviewCount} reviews)`
                : 'New Relative'}
            </span>
            <span className="inline-flex items-center gap-1 rounded-lg bg-white px-2 py-1 font-semibold border border-sky-100">
              <ThumbsUp className="h-3 w-3 text-teal-700" />
              {contract.customerReviewCount > 0
                ? `${Math.round(Number(contract.customerWorkAgainPercentage))}% Would Work Again`
                : `${contract.customerCompletedHiresCount} completed hire${
                    contract.customerCompletedHiresCount === 1 ? '' : 's'
                  }`}
            </span>
          </div>
        </div>
      )}

      {/* Agreed terms & Dhaka shift window */}
      <div className="space-y-2 rounded-xl bg-slate-50 p-3.5 text-sm">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
            Agreed Rate
          </span>
          <span className="text-base font-extrabold text-slate-900">
            {formatContractRate(contract.agreedRate, contract.rateType)}
          </span>
        </div>

        <div className="flex items-start gap-2 border-t border-slate-200/70 pt-2 text-xs text-slate-700">
          <CalendarClock className="mt-0.5 h-4 w-4 shrink-0 text-teal-700" aria-hidden="true" />
          <div className="space-y-0.5">
            <div className="font-semibold text-slate-900">
              {formatDhakaShiftRange(contract.scheduledStartTime, contract.scheduledEndTime)}
            </div>
            <div className="text-[11px] text-slate-500">
              Dhaka Time (BST){durationLabel ? ` · ${durationLabel}` : ''}
            </div>
          </div>
        </div>

        {contract.actualEndTime && (
          <div className="flex items-center gap-2 text-xs font-semibold text-indigo-800">
            <Clock className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span>Actual stop recorded: {formatDhakaDateTime(contract.actualEndTime)}</span>
          </div>
        )}

        {contract.notes && (
          <div className="flex items-start gap-2 border-t border-slate-200/70 pt-2 text-xs text-slate-700">
            <FileText className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
            <p className="whitespace-pre-wrap break-words">{contract.notes}</p>
          </div>
        )}
      </div>

      {contract.cancellationOrEarlyEndReason && (
        <div className="rounded-xl border border-slate-200 bg-slate-50/70 px-3.5 py-2.5 text-xs text-slate-700">
          <span className="font-bold text-slate-900">
            {contract.status === 'DECLINED'
              ? 'Decline note'
              : contract.status === 'CANCELLED'
                ? 'Cancellation reason'
                : 'Early termination reason'}
            {contract.initiatedByName ? ` (${contract.initiatedByName})` : ''}:{' '}
          </span>
          <span>{contract.cancellationOrEarlyEndReason}</span>
        </div>
      )}

      {contract.status === 'COMPLETED_PENDING_SETTLEMENT' && (
        <div className="rounded-xl border border-indigo-200 bg-indigo-50/70 px-3.5 py-2.5 text-xs text-indigo-950">
          <strong>Settlement Stage:</strong> Shift ended and schedule freed. Bidirectional 7-day
          reviews and dual payment confirmation / dispute mediation activate in Phases 6–7.
        </div>
      )}

      {/* Action buttons (minimum 44px touch targets) */}
      <div className="flex flex-wrap items-center gap-2 pt-0.5">
        {isCaretaker && contract.status === 'REQUESTED' && (
          <div className="grid w-full grid-cols-2 gap-2.5">
            <Button
              variant="danger"
              onClick={() => onDecline?.(contract)}
              disabled={busyAction === contract.id}
            >
              <X className="h-4 w-4" aria-hidden="true" />
              <span>Decline</span>
            </Button>
            <Button
              variant="primary"
              onClick={() => onAccept?.(contract)}
              loading={busyAction === contract.id}
            >
              <Check className="h-4 w-4" aria-hidden="true" />
              <span>Accept Shift</span>
            </Button>
          </div>
        )}

        {contract.canCancel && !(isCaretaker && contract.status === 'REQUESTED') && (
          <Button
            variant="outline"
            onClick={() => onCancel?.(contract)}
            disabled={busyAction === contract.id}
            className="flex-1 border-rose-200 text-rose-700 hover:bg-rose-50"
          >
            <X className="h-4 w-4" aria-hidden="true" />
            <span>
              {contract.status === 'REQUESTED' ? 'Cancel Request' : 'Cancel Before Start'}
            </span>
          </Button>
        )}

        {contract.canEndEarly && (
          <Button
            variant="primary"
            onClick={() => onEndEarly?.(contract)}
            disabled={busyAction === contract.id}
            className="flex-1 bg-indigo-700 hover:bg-indigo-800"
          >
            <StopCircle className="h-4 w-4" aria-hidden="true" />
            <span>End Service Early</span>
          </Button>
        )}

        {contract.negotiationRoomId && (
          <Link
            to={`${chatBasePath}/${encodeURIComponent(contract.negotiationRoomId)}`}
            className="inline-flex min-h-[44px] items-center justify-center gap-1.5 rounded-xl border border-slate-300 bg-white px-3.5 text-xs font-semibold text-slate-700 transition-colors hover:bg-slate-50"
          >
            <MessageSquare className="h-4 w-4 text-teal-700" aria-hidden="true" />
            <span>Chat</span>
          </Link>
        )}

        {counterpartPhone && (
          <a
            href={`tel:${counterpartPhone}`}
            className="inline-flex min-h-[44px] items-center justify-center gap-1.5 rounded-xl border border-teal-200 bg-teal-50 px-3.5 text-xs font-bold text-teal-800 transition-colors hover:bg-teal-100"
          >
            <Phone className="h-4 w-4" aria-hidden="true" />
            <span>Call</span>
          </a>
        )}
      </div>
    </article>
  );
}
