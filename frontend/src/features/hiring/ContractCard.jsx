import {
  AlertTriangle,
  Banknote,
  CalendarClock,
  Check,
  CheckCircle2,
  Clock,
  Edit3,
  FileText,
  Lock,
  MapPin,
  MessageSquare,
  Phone,
  Scale,
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
  onOpenFeedback,
  onViewCustomerReviews,
  onMarkPaymentDone,
  onConfirmPayment,
  onRefusePayment,
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
  const disputeBasePath = isCustomer ? '/customer/disputes' : '/caretaker/disputes';
  const isSettlementOrFinal = [
    'COMPLETED_PENDING_SETTLEMENT',
    'CLOSED_PAID',
    'PAYMENT_DISPUTED',
    'DISPUTE_RESOLVED',
  ].includes(contract.status);
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
        <div className="rounded-xl border border-sky-200 bg-sky-50/70 p-3 text-xs space-y-2">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-1.5 font-bold text-sky-950">
              <ShieldCheck className="h-4 w-4 text-sky-700" aria-hidden="true" />
              <span>Relative Trust Preview</span>
            </div>
            {onViewCustomerReviews && (
              <button
                type="button"
                onClick={() => onViewCustomerReviews(contract)}
                className="inline-flex min-h-[44px] items-center rounded-lg border border-sky-300 bg-white px-3 text-xs font-bold text-sky-900 hover:bg-sky-100"
              >
                Read Caretaker Reviews ({contract.customerReviewCount})
              </button>
            )}
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

      {isSettlementOrFinal && (
        <div
          className={`space-y-1.5 rounded-xl border px-3.5 py-2.5 text-xs ${
            contract.status === 'PAYMENT_DISPUTED'
              ? 'border-rose-200 bg-rose-50/80 text-rose-950'
              : contract.status === 'CLOSED_PAID' || contract.status === 'DISPUTE_RESOLVED'
                ? 'border-emerald-200 bg-emerald-50/70 text-emerald-950'
                : 'border-indigo-200 bg-indigo-50/70 text-indigo-950'
          }`}
        >
          <div className="flex flex-wrap items-center justify-between gap-2 font-bold">
            <span className="inline-flex items-center gap-1.5">
              {contract.status === 'PAYMENT_DISPUTED' ? (
                <Scale className="h-3.5 w-3.5 text-rose-700" aria-hidden="true" />
              ) : contract.status === 'CLOSED_PAID' || contract.status === 'DISPUTE_RESOLVED' ? (
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-700" aria-hidden="true" />
              ) : (
                <Banknote className="h-3.5 w-3.5 text-indigo-700" aria-hidden="true" />
              )}
              <span>
                {contract.status === 'CLOSED_PAID'
                  ? 'Dual Payment Confirmed · Contract Closed'
                  : contract.status === 'PAYMENT_DISPUTED'
                    ? 'Payment Disputed · Admin Mediation Active'
                    : contract.status === 'DISPUTE_RESOLVED'
                      ? 'Payment Dispute Resolved by Admin'
                      : 'Dual Payment Settlement'}
              </span>
            </span>
          </div>

          <div className="flex flex-wrap gap-x-4 gap-y-1 text-[11px]">
            <span>
              <strong>Relative:</strong>{' '}
              {contract.customerPaymentConfirmed ? 'Marked Payment Done' : 'Pending payment'}
            </span>
            <span>
              <strong>Caretaker:</strong>{' '}
              {contract.caretakerPaymentConfirmed
                ? 'Confirmed Receipt'
                : contract.status === 'PAYMENT_DISPUTED'
                  ? 'Refused / Not Received'
                  : 'Awaiting confirmation'}
            </span>
          </div>

          {contract.paymentReferenceNote && (
            <p className="text-[11px]">
              <strong>Payment Ref:</strong> {contract.paymentReferenceNote}
            </p>
          )}
        </div>
      )}

      {/* Action buttons (minimum 44px touch targets) */}
      <div className="flex flex-wrap items-center gap-2 pt-0.5">
        {/* Dual Payment Actions */}
        {isCustomer &&
          contract.status === 'COMPLETED_PENDING_SETTLEMENT' &&
          !contract.customerPaymentConfirmed &&
          onMarkPaymentDone && (
            <Button
              variant="primary"
              onClick={() => onMarkPaymentDone(contract)}
              disabled={busyAction === contract.id}
              className="flex-1 bg-emerald-700 hover:bg-emerald-800"
            >
              <Banknote className="h-4 w-4" aria-hidden="true" />
              <span>Mark Payment Done</span>
            </Button>
          )}

        {isCaretaker &&
          contract.status === 'COMPLETED_PENDING_SETTLEMENT' &&
          contract.customerPaymentConfirmed && (
            <div className="grid w-full grid-cols-1 gap-2 sm:grid-cols-2">
              {onRefusePayment && (
                <Button
                  variant="danger"
                  onClick={() => onRefusePayment(contract)}
                  disabled={busyAction === contract.id}
                >
                  <AlertTriangle className="h-4 w-4" aria-hidden="true" />
                  <span>Refuse / Not Received</span>
                </Button>
              )}
              {onConfirmPayment && (
                <Button
                  variant="primary"
                  onClick={() => onConfirmPayment(contract)}
                  loading={busyAction === contract.id}
                  className="bg-emerald-700 hover:bg-emerald-800"
                >
                  <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                  <span>Confirm Payment Received</span>
                </Button>
              )}
            </div>
          )}

        {(contract.status === 'PAYMENT_DISPUTED' || contract.status === 'DISPUTE_RESOLVED') && (
          <Link
            to={`${disputeBasePath}/${contract.id}`}
            className={`inline-flex min-h-[44px] flex-1 items-center justify-center gap-2 rounded-xl px-4 text-xs font-bold shadow-xs transition-colors ${
              contract.status === 'PAYMENT_DISPUTED'
                ? 'bg-rose-700 text-white hover:bg-rose-800'
                : 'border border-indigo-200 bg-indigo-50 text-indigo-900 hover:bg-indigo-100'
            }`}
          >
            <Scale className="h-4 w-4" aria-hidden="true" />
            <span>
              {contract.status === 'PAYMENT_DISPUTED'
                ? 'Open 3-Way Dispute Room'
                : 'View Dispute Resolution'}
            </span>
          </Link>
        )}
        {contract.myReviewId && !contract.myReviewSubmitted && onOpenFeedback && (
          <Button
            variant="primary"
            onClick={() => onOpenFeedback(contract)}
            className="flex-1 bg-amber-600 hover:bg-amber-700"
          >
            <Star className="h-4 w-4 fill-white" aria-hidden="true" />
            <span>Leave Feedback</span>
          </Button>
        )}

        {contract.myReviewId && contract.myReviewSubmitted && contract.myReviewCanEdit && onOpenFeedback && (
          <Button
            variant="outline"
            onClick={() => onOpenFeedback(contract)}
            className="flex-1 border-teal-300 text-teal-800 hover:bg-teal-50"
          >
            <Edit3 className="h-4 w-4" aria-hidden="true" />
            <span>Edit Feedback (7-Day Window)</span>
          </Button>
        )}

        {contract.myReviewId && contract.myReviewSubmitted && contract.myReviewLocked && (
          <span className="inline-flex min-h-[44px] items-center gap-1.5 rounded-xl border border-slate-200 bg-slate-50 px-3 text-xs font-semibold text-slate-600">
            <Lock className="h-3.5 w-3.5" aria-hidden="true" />
            <span>Review Locked</span>
          </span>
        )}
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
