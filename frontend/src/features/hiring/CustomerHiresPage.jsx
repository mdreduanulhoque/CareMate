import { BriefcaseMedical, RefreshCw, Search } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as hireApi from '../../api/hireApi';
import * as reviewApi from '../../api/reviewApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { useWebSocket } from '../../context/useWebSocket';
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
]);

const FILTER_TABS = [
  { key: 'ALL', label: 'All Hires', statuses: [] },
  { key: 'ACTIVE_UPCOMING', label: 'Active & Upcoming', statuses: ['ACTIVE', 'SCHEDULED'] },
  { key: 'PENDING', label: 'Awaiting Reply', statuses: ['REQUESTED'] },
  {
    key: 'SETTLEMENT_PAST',
    label: 'Settlement & Past',
    statuses: ['COMPLETED_PENDING_SETTLEMENT', 'CLOSED_PAID', 'PAYMENT_DISPUTED', 'DISPUTE_RESOLVED', 'DECLINED', 'CANCELLED'],
  },
];

export function CustomerHiresPage() {
  const { isConnected, subscribe } = useWebSocket();

  const [activeTab, setActiveTab] = useState('ALL');
  const [contracts, setContracts] = useState([]);
  const [authoredReviews, setAuthoredReviews] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');

  const [modalState, setModalState] = useState(null); // { mode: 'cancel' | 'endEarly', contract }
  const [modalError, setModalError] = useState('');
  const [activeReviewModal, setActiveReviewModal] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const loadContracts = useCallback(
    async ({ silent = false } = {}) => {
      if (!silent) {
        setLoading(true);
      }
      setError('');
      try {
        const selected = FILTER_TABS.find((t) => t.key === activeTab);
        const [data, authored] = await Promise.all([
          hireApi.fetchMyHires(selected?.statuses || []),
          reviewApi.fetchAuthoredReviews().catch(() => []),
        ]);
        setContracts(data);
        setAuthoredReviews(authored);
      } catch (err) {
        setError(err.message || 'Could not load your hires right now.');
      } finally {
        setLoading(false);
      }
    },
    [activeTab]
  );

  const pendingReviews = authoredReviews.filter((r) => !r.submitted);

  function handleOpenContractFeedback(contract) {
    const matched = authoredReviews.find((r) => r.id === contract.myReviewId);
    if (matched) {
      setActiveReviewModal(matched);
    }
  }

  useEffect(() => {
    loadContracts();
  }, [loadContracts]);

  // Live WebSocket auto-refresh when caretaker accepts, declines, cancels, or ends shift early
  useEffect(() => {
    return subscribe('/user/queue/notifications', (event) => {
      if (CONTRACT_EVENTS.has(event?.eventType)) {
        loadContracts({ silent: true });
      }
    });
  }, [subscribe, loadContracts]);

  async function handleConfirmModal(reason) {
    if (!modalState) return;
    const { mode, contract } = modalState;
    setBusyId(contract.id);
    setModalError('');
    setFeedback('');
    try {
      if (mode === 'cancel') {
        await hireApi.cancelHireContract(contract.id, reason);
        setFeedback(`Cancelled booking #${contract.id}. The caretaker's schedule slot is now free.`);
      } else if (mode === 'endEarly') {
        await hireApi.endHireContractEarly(contract.id, reason);
        setFeedback(
          `Ended shift #${contract.id} early. Actual stop time recorded and contract moved to settlement.`
        );
      }
      setModalState(null);
      await loadContracts({ silent: true });
    } catch (err) {
      setModalError(err.message || 'Could not update contract status.');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-slate-900">My Hires</h1>
            {isConnected && (
              <span className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-[11px] font-bold text-emerald-800">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                Live
              </span>
            )}
          </div>
          <p className="text-sm text-slate-600">
            Track pending hire requests, active hospital shifts, cancellations, and early endings.
          </p>
        </div>

        <Button variant="outline" onClick={() => loadContracts()} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          <span>Refresh</span>
        </Button>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {FILTER_TABS.map((tab) => (
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
        <AlertBanner variant="error" title="Could not load hires">
          <div className="space-y-2">
            <p>{error}</p>
            <Button variant="outline" onClick={() => loadContracts()}>
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
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-teal-50 text-teal-700">
            <BriefcaseMedical className="h-6 w-6" aria-hidden="true" />
          </div>
          <h2 className="text-base font-bold text-slate-900">No hire contracts here yet</h2>
          <p className="mx-auto max-w-md text-sm text-slate-600">
            Browse verified caretakers on the Hunters Feed, discuss shift terms over phone or live
            chat, and submit a formal Hire request when ready.
          </p>
          <div className="pt-1">
            <Link to="/customer/feed" className="inline-block">
              <Button>
                <Search className="h-4 w-4" aria-hidden="true" />
                <span>Find a Verified Caretaker</span>
              </Button>
            </Link>
          </div>
        </div>
      ) : (
        <div className="space-y-3.5">
          {contracts.map((contract) => (
            <ContractCard
              key={contract.id}
              contract={contract}
              viewerRole="CUSTOMER"
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
            loadContracts({ silent: true });
          }}
        />
      )}
    </div>
  );
}
