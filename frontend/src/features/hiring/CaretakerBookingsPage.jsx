import { CalendarCheck2, RefreshCw, ShieldAlert } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as hireApi from '../../api/hireApi';
import * as paymentDisputeApi from '../../api/paymentDisputeApi';
import * as reviewApi from '../../api/reviewApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { useAuth } from '../../context/useAuth';
import { useWebSocket } from '../../context/useWebSocket';
import { getTodayDhakaDateString } from '../../utils/dhakaTime';
import { PaymentActionModal } from '../disputes/PaymentActionModal';
import { PendingReviewsBanner } from '../feedback/PendingReviewsBanner';
import { ReviewFormModal } from '../feedback/ReviewFormModal';
import { ContractCard } from './ContractCard';
import { ContractReasonModal } from './ContractReasonModal';

const CONTRACT_EVENTS = new Set([
  'HIRE_REQUESTED',
  'HIRE_ACCEPTED',
  'HIRE_DECLINED',
  'CONTRACT_CANCELLED',
  'CONTRACT_ENDED_EARLY',
  'CONTRACT_ACTIVATED',
  'CONTRACT_COMPLETED_FEEDBACK_READY',
  'REVIEW_RECEIVED',
  'PAYMENT_MARKED_DONE',
  'PAYMENT_CONFIRMED_CLOSED',
  'PAYMENT_DISPUTED',
  'DISPUTE_ADMIN_JOINED',
  'DISPUTE_RESOLVED',
  'DISPUTE_NEW_MESSAGE',
]);

const BOOKING_TABS = [
  { key: 'ACTIVE_SCHEDULED', label: 'Active & Upcoming', statuses: ['ACTIVE', 'SCHEDULED'] },
  {
    key: 'SETTLEMENT',
    label: 'Settlement & Completed',
    statuses: ['COMPLETED_PENDING_SETTLEMENT', 'CLOSED_PAID', 'PAYMENT_DISPUTED', 'DISPUTE_RESOLVED'],
  },
  { key: 'HISTORY', label: 'Declined & Cancelled', statuses: ['DECLINED', 'CANCELLED'] },
];

export function CaretakerBookingsPage() {
  const { user } = useAuth();
  const { isConnected, subscribe } = useWebSocket();
  const navigate = useNavigate();

  const isUnverified = !user?.caretakerApproved;

  const [activeTab, setActiveTab] = useState('ACTIVE_SCHEDULED');
  const [contracts, setContracts] = useState([]);
  const [authoredReviews, setAuthoredReviews] = useState([]);
  const [todaySchedule, setTodaySchedule] = useState(null);
  const [loading, setLoading] = useState(!isUnverified);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');

  const [modalState, setModalState] = useState(null);
  const [modalError, setModalError] = useState('');
  const [refuseTarget, setRefuseTarget] = useState(null);
  const [refuseError, setRefuseError] = useState('');
  const [activeReviewModal, setActiveReviewModal] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const loadBookings = useCallback(
    async ({ silent = false } = {}) => {
      if (isUnverified) {
        setLoading(false);
        return;
      }
      if (!silent) {
        setLoading(true);
      }
      setError('');
      try {
        const selected = BOOKING_TABS.find((t) => t.key === activeTab);
        const [data, schedule, authored] = await Promise.all([
          hireApi.fetchMyHires(selected?.statuses || []),
          hireApi.fetchCaretakerDaySchedule(user.id, getTodayDhakaDateString()).catch(() => null),
          reviewApi.fetchAuthoredReviews().catch(() => []),
        ]);
        setContracts(data);
        setTodaySchedule(schedule);
        setAuthoredReviews(authored);
      } catch (err) {
        setError(err.message || 'Could not load your bookings right now.');
      } finally {
        setLoading(false);
      }
    },
    [activeTab, isUnverified, user?.id]
  );

  const pendingReviews = authoredReviews.filter((r) => !r.submitted);

  function handleOpenContractFeedback(contract) {
    const matched = authoredReviews.find((r) => r.id === contract.myReviewId);
    if (matched) {
      setActiveReviewModal(matched);
    }
  }

  useEffect(() => {
    loadBookings();
  }, [loadBookings]);

  useEffect(() => {
    if (isUnverified) return undefined;
    return subscribe('/user/queue/notifications', (event) => {
      if (CONTRACT_EVENTS.has(event?.eventType)) {
        loadBookings({ silent: true });
      }
    });
  }, [isUnverified, subscribe, loadBookings]);

  async function handleConfirmModal(reason) {
    if (!modalState) return;
    const { mode, contract } = modalState;
    setBusyId(contract.id);
    setModalError('');
    setFeedback('');
    try {
      if (mode === 'cancel') {
        await hireApi.cancelHireContract(contract.id, reason);
        setFeedback(`Cancelled booking #${contract.id}. Your daily shift slot is now free.`);
      } else if (mode === 'endEarly') {
        await hireApi.endHireContractEarly(contract.id, reason);
        setFeedback(
          `Ended shift #${contract.id} early. Actual stop time recorded and remaining schedule freed.`
        );
      }
      setModalState(null);
      await loadBookings({ silent: true });
    } catch (err) {
      setModalError(err.message || 'Could not update booking.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleConfirmPaymentReceived(contract) {
    setBusyId(contract.id);
    setError('');
    setFeedback('');
    try {
      await paymentDisputeApi.confirmCaretakerPaymentReceived(contract.id);
      setFeedback(`Confirmed payment receipt for contract #${contract.id}. Contract is now closed.`);
      await loadBookings({ silent: true });
    } catch (err) {
      setError(err.message || 'Could not confirm payment receipt.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleConfirmRefusePayment(statement) {
    if (!refuseTarget) return;
    setBusyId(refuseTarget.id);
    setRefuseError('');
    try {
      await paymentDisputeApi.refuseCaretakerPayment(refuseTarget.id, statement);
      const disputedContractId = refuseTarget.id;
      setRefuseTarget(null);
      navigate(`/caretaker/disputes/${disputedContractId}`);
    } catch (err) {
      setRefuseError(err.message || 'Could not open payment dispute.');
    } finally {
      setBusyId(null);
    }
  }

  if (isUnverified) {
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <div className="rounded-2xl border border-amber-200 bg-amber-50/70 p-6 space-y-3">
          <div className="flex items-start gap-3">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-700" aria-hidden="true" />
            <div className="space-y-1">
              <h1 className="text-lg font-bold text-amber-950">Verification Required for Bookings</h1>
              <p className="text-sm leading-relaxed text-amber-900">
                Complete Admin verification to unlock your public profile and start managing
                scheduled hospital care shifts here.
              </p>
            </div>
          </div>
          <Link to="/caretaker/requests" className="block">
            <Button fullWidth>Go to Verification Status</Button>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-slate-900">My Bookings</h1>
            {isConnected && (
              <span className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-[11px] font-bold text-emerald-800">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                Live
              </span>
            )}
            {todaySchedule && (
              <span
                className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-[11px] font-bold ${
                  todaySchedule.dailyCapReached
                    ? 'border-amber-300 bg-amber-100 text-amber-950'
                    : 'border-sky-200 bg-sky-50 text-sky-900'
                }`}
              >
                Today: {todaySchedule.bookingsCount}/{todaySchedule.maxBookingsPerDay} shifts
              </span>
            )}
          </div>
          <p className="text-sm text-slate-600">
            Manage confirmed shifts (max 2 non-overlapping bookings per Dhaka day), cancellations,
            and early endings.
          </p>
        </div>

        <Button variant="outline" onClick={() => loadBookings()} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          <span>Refresh</span>
        </Button>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {BOOKING_TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setActiveTab(tab.key)}
            className={`min-h-[44px] shrink-0 rounded-full border px-4 text-xs font-semibold transition-colors ${
              activeTab === tab.key
                ? 'border-teal-700 bg-teal-700 text-white'
                : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-100'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {feedback && <AlertBanner variant="success">{feedback}</AlertBanner>}
      <PendingReviewsBanner
        pendingReviews={pendingReviews}
        onOpenReview={(review) => setActiveReviewModal(review)}
      />
      {error && (
        <AlertBanner variant="error" title="Could not load bookings">
          <div className="space-y-2">
            <p>{error}</p>
            <Button variant="outline" onClick={() => loadBookings()}>
              Try Again
            </Button>
          </div>
        </AlertBanner>
      )}

      {loading && contracts.length === 0 ? (
        <div className="space-y-3">
          {[1, 2].map((s) => (
            <div
              key={s}
              className="h-44 animate-pulse rounded-2xl border border-slate-200 bg-white p-5"
            />
          ))}
        </div>
      ) : contracts.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center space-y-3">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-sky-50 text-sky-700">
            <CalendarCheck2 className="h-6 w-6" aria-hidden="true" />
          </div>
          <h2 className="text-base font-bold text-slate-900">No bookings in this view</h2>
          <p className="mx-auto max-w-md text-sm text-slate-600">
            Accepted hospital care shifts appear here with live status controls. Check your{' '}
            <Link to="/caretaker/requests" className="font-semibold text-teal-700 underline">
              Incoming Requests
            </Link>{' '}
            tab for new offers.
          </p>
        </div>
      ) : (
        <div className="space-y-3.5">
          {contracts.map((contract) => (
            <ContractCard
              key={contract.id}
              contract={contract}
              viewerRole="CARETAKER"
              busyAction={busyId}
              onCancel={(target) => {
                setModalError('');
                setModalState({ mode: 'cancel', contract: target });
              }}
              onEndEarly={(target) => {
                setModalError('');
                setModalState({ mode: 'endEarly', contract: target });
              }}
              onOpenFeedback={handleOpenContractFeedback}
              onConfirmPayment={handleConfirmPaymentReceived}
              onRefusePayment={(target) => {
                setRefuseError('');
                setRefuseTarget(target);
              }}
            />
          ))}
        </div>
      )}

      {modalState && (
        <ContractReasonModal
          mode={modalState.mode}
          contract={modalState.contract}
          submitting={busyId === modalState.contract.id}
          error={modalError}
          onClose={() => setModalState(null)}
          onConfirm={handleConfirmModal}
        />
      )}

      {refuseTarget && (
        <PaymentActionModal
          mode="caretakerRefuse"
          contract={refuseTarget}
          submitting={busyId === refuseTarget.id}
          error={refuseError}
          onClose={() => setRefuseTarget(null)}
          onConfirm={handleConfirmRefusePayment}
        />
      )}

      {activeReviewModal && (
        <ReviewFormModal
          review={activeReviewModal}
          onClose={() => setActiveReviewModal(null)}
          onSaved={(saved) => {
            setActiveReviewModal(null);
            setFeedback(
              saved.edited
                ? `Updated your feedback for ${saved.revieweeName} (marked as Edited).`
                : `Submitted your ${saved.rating}-star feedback for ${saved.revieweeName}.`
            );
            loadBookings({ silent: true });
          }}
        />
      )}
    </div>
  );
}
