import { Award, CalendarCheck2, ClipboardList, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as hireApi from '../../api/hireApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { getButtonClasses } from '../../components/ui/buttonStyles';
import { useAuth } from '../../context/useAuth';
import { useWebSocket } from '../../context/useWebSocket';
import { CustomerReviewsModal } from '../feedback/CustomerReviewsModal';
import { ContractCard } from '../hiring/ContractCard';
import { ContractReasonModal } from '../hiring/ContractReasonModal';
import { CaretakerVerificationPanel } from './CaretakerVerificationPanel';

const CONTRACT_EVENTS = new Set([
  'HIRE_REQUESTED',
  'HIRE_ACCEPTED',
  'HIRE_DECLINED',
  'CONTRACT_CANCELLED',
  'CONTRACT_ENDED_EARLY',
]);

export function CaretakerRequestsGatePage() {
  const { user } = useAuth();
  const { isConnected, subscribe } = useWebSocket();

  const isApproved = Boolean(user?.caretakerApproved);

  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(isApproved);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');
  const [busyId, setBusyId] = useState(null);

  const [declineTarget, setDeclineTarget] = useState(null);
  const [declineError, setDeclineError] = useState('');
  const [reviewsTargetCustomer, setReviewsTargetCustomer] = useState(null);

  const loadIncomingRequests = useCallback(
    async ({ silent = false } = {}) => {
      if (!isApproved) {
        setLoading(false);
        return;
      }
      if (!silent) {
        setLoading(true);
      }
      setError('');
      try {
        const data = await hireApi.fetchMyHires(['REQUESTED']);
        setRequests(data);
      } catch (err) {
        setError(err.message || 'Could not load incoming hire requests.');
      } finally {
        setLoading(false);
      }
    },
    [isApproved]
  );

  useEffect(() => {
    loadIncomingRequests();
  }, [loadIncomingRequests]);

  useEffect(() => {
    if (!isApproved) return undefined;
    return subscribe('/user/queue/notifications', (event) => {
      if (CONTRACT_EVENTS.has(event?.eventType)) {
        loadIncomingRequests({ silent: true });
      }
    });
  }, [isApproved, subscribe, loadIncomingRequests]);

  async function handleAccept(contract) {
    setBusyId(contract.id);
    setError('');
    setFeedback('');
    try {
      const updated = await hireApi.acceptHireRequest(contract.id);
      setFeedback(
        `Accepted booking #${updated.id} (${
          updated.status === 'ACTIVE' ? 'Active now' : 'Scheduled'
        }). View it in My Bookings.`
      );
      await loadIncomingRequests({ silent: true });
    } catch (err) {
      setError(err.message || 'Could not accept this hire request.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleConfirmDecline(reason) {
    if (!declineTarget) return;
    setBusyId(declineTarget.id);
    setDeclineError('');
    setFeedback('');
    try {
      await hireApi.declineHireRequest(declineTarget.id, reason);
      setFeedback(`Declined hire request #${declineTarget.id}.`);
      setDeclineTarget(null);
      await loadIncomingRequests({ silent: true });
    } catch (err) {
      setDeclineError(err.message || 'Could not decline this hire request.');
    } finally {
      setBusyId(null);
    }
  }

  if (!isApproved) {
    return (
      <div className="mx-auto max-w-2xl space-y-4">
        <div className="space-y-1">
          <h1 className="text-xl font-bold tracking-tight text-slate-900">
            Complete Your Verification
          </h1>
          <p className="text-sm text-slate-600">
            Once an Admin verifies your NID and credentials, you will unlock your public profile and
            begin receiving hospital shift requests here.
          </p>
        </div>
        <CaretakerVerificationPanel showCertificationsWhenApproved={false} />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-slate-900">
              Incoming Hire Requests
            </h1>
            {isConnected && (
              <span className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-[11px] font-bold text-emerald-800">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                Live
              </span>
            )}
          </div>
          <p className="text-sm text-slate-600">
            Review relative trust details, shift timing, and agreed BDT rate before accepting (max 2
            non-overlapping bookings per Dhaka day).
          </p>
        </div>

        <Button variant="outline" onClick={() => loadIncomingRequests()} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          <span>Refresh</span>
        </Button>
      </div>

      {feedback && (
        <AlertBanner variant="success">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span>{feedback}</span>
            <Link to="/caretaker/bookings" className="font-bold underline">
              Open My Bookings
            </Link>
          </div>
        </AlertBanner>
      )}
      {error && <AlertBanner variant="error">{error}</AlertBanner>}

      {loading && requests.length === 0 ? (
        <div className="space-y-3">
          {[1, 2].map((s) => (
            <div
              key={s}
              className="h-44 animate-pulse rounded-2xl border border-slate-200 bg-white p-5"
            />
          ))}
        </div>
      ) : requests.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center space-y-3">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-teal-50 text-teal-700">
            <ClipboardList className="h-6 w-6" aria-hidden="true" />
          </div>
          <h2 className="text-base font-bold text-slate-900">No pending hire requests right now</h2>
          <p className="mx-auto max-w-md text-sm text-slate-600">
            New requests from patients&apos; relatives will appear here in real time as soon as they
            tap Hire.
          </p>
          <div className="flex flex-wrap justify-center gap-2 pt-1">
            <Link to="/caretaker/bookings" className={getButtonClasses({ variant: 'outline' })}>
              <CalendarCheck2 className="h-4 w-4" aria-hidden="true" />
              <span>View My Bookings</span>
            </Link>
            <Link to="/caretaker/profile" className={getButtonClasses({ variant: 'outline' })}>
              <Award className="h-4 w-4" aria-hidden="true" />
              <span>Profile &amp; Certifications</span>
            </Link>
          </div>
        </div>
      ) : (
        <div className="space-y-3.5">
          {requests.map((contract) => (
            <ContractCard
              key={contract.id}
              contract={contract}
              viewerRole="CARETAKER"
              busyAction={busyId}
              onAccept={handleAccept}
              onDecline={(target) => {
                setDeclineError('');
                setDeclineTarget(target);
              }}
              onViewCustomerReviews={(target) =>
                setReviewsTargetCustomer({
                  id: target.customerId,
                  name: target.customerFullName,
                })
              }
            />
          ))}
        </div>
      )}

      {declineTarget && (
        <ContractReasonModal
          mode="decline"
          contract={declineTarget}
          submitting={busyId === declineTarget.id}
          error={declineError}
          onClose={() => setDeclineTarget(null)}
          onConfirm={handleConfirmDecline}
        />
      )}

      {reviewsTargetCustomer && (
        <CustomerReviewsModal
          customerUserId={reviewsTargetCustomer.id}
          customerName={reviewsTargetCustomer.name}
          onClose={() => setReviewsTargetCustomer(null)}
        />
      )}
    </div>
  );
}
