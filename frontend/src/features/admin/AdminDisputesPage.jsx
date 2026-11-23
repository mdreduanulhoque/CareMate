import { MessageSquare, RefreshCw, Scale, ShieldCheck } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as paymentDisputeApi from '../../api/paymentDisputeApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useWebSocket } from '../../context/useWebSocket';
import { formatDhakaDateTime } from '../../utils/dhakaTime';

const FILTERS = [
  { key: 'OPEN', label: 'Open' },
  { key: 'UNDER_REVIEW', label: 'Under Review' },
  { key: 'RESOLVED', label: 'Resolved' },
  { key: 'ALL', label: 'All' },
];

export function AdminDisputesPage() {
  const { isConnected, subscribe } = useWebSocket();

  const [statusFilter, setStatusFilter] = useState('ALL');
  const [disputes, setDisputes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [liveNotice, setLiveNotice] = useState('');

  const loadDisputes = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await paymentDisputeApi.fetchDisputes(statusFilter);
      setDisputes(data);
    } catch (err) {
      setError(err.message || 'Could not load payment disputes queue.');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadDisputes();
  }, [loadDisputes]);

  useEffect(() => {
    return subscribe('/topic/admin/alerts', (event) => {
      if (event?.eventType === 'PAYMENT_DISPUTE') {
        setLiveNotice(`Urgent dispute alert: ${event.title} — ${event.message}`);
        loadDisputes();
      }
    });
  }, [subscribe, loadDisputes]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-slate-900">
              Payment Dispute Mediation Queue
            </h1>
            {isConnected && (
              <span className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-[11px] font-bold text-emerald-800">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                Live Alerts
              </span>
            )}
          </div>
          <p className="text-sm text-slate-600">
            Join 3-way real-time WebSocket dispute rooms to verify payment statements and record
            fair resolutions.
          </p>
        </div>

        <Button variant="outline" onClick={loadDisputes} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          <span>Refresh</span>
        </Button>
      </div>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {FILTERS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setStatusFilter(item.key)}
            className={`min-h-[44px] shrink-0 rounded-full border px-4 text-sm font-semibold transition-colors ${
              statusFilter === item.key
                ? 'border-teal-700 bg-teal-700 text-white'
                : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-100'
            }`}
          >
            {item.label}
          </button>
        ))}
      </div>

      {liveNotice && <AlertBanner variant="info">{liveNotice}</AlertBanner>}
      {error && <AlertBanner variant="error">{error}</AlertBanner>}

      {loading && disputes.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
          Loading payment disputes...
        </div>
      ) : disputes.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center space-y-2">
          <Scale className="mx-auto h-8 w-8 text-slate-400" aria-hidden="true" />
          <p className="text-sm font-semibold text-slate-700">No payment disputes in this view.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {disputes.map((dispute) => (
            <article
              key={dispute.id}
              className="space-y-3.5 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs"
            >
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <h2 className="text-base font-bold text-slate-900">
                      Contract #{dispute.contractId} · ৳
                      {Number(dispute.agreedRate).toLocaleString('en-BD')}
                    </h2>
                    <StatusBadge status={dispute.contractStatus} />
                  </div>
                  <p className="mt-1 text-xs text-slate-500">
                    Opened {formatDhakaDateTime(dispute.createdAt)} by {dispute.raisedByName}
                    {dispute.assignedAdminName ? ` · Mediator: ${dispute.assignedAdminName}` : ''}
                  </p>
                </div>
                <span className="rounded-full border border-slate-200 bg-slate-100 px-2.5 py-0.5 text-xs font-bold text-slate-700">
                  {dispute.status}
                </span>
              </div>

              <div className="grid gap-2.5 text-xs sm:grid-cols-2">
                <div className="rounded-xl bg-teal-50/60 p-3 border border-teal-100">
                  <div className="font-bold text-teal-950">
                    Relative: {dispute.customerFullName} ({dispute.customerMobileNumber})
                  </div>
                  <p className="mt-1 text-slate-700">
                    {dispute.customerStatement || dispute.paymentReferenceNote || 'No reference note'}
                  </p>
                </div>

                <div className="rounded-xl bg-rose-50/60 p-3 border border-rose-100">
                  <div className="font-bold text-rose-950">
                    Caretaker: {dispute.caretakerFullName} ({dispute.caretakerMobileNumber})
                  </div>
                  <p className="mt-1 text-slate-700">{dispute.caretakerStatement}</p>
                </div>
              </div>

              {dispute.adminResolutionNote && (
                <div className="flex items-start gap-2 rounded-xl border border-emerald-200 bg-emerald-50 p-3 text-xs text-emerald-950">
                  <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700" />
                  <div>
                    <strong>Resolution ({formatDhakaDateTime(dispute.resolvedAt)}):</strong>{' '}
                    {dispute.adminResolutionNote}
                  </div>
                </div>
              )}

              <Link to={`/admin/disputes/${dispute.contractId}`} className="block">
                <Button fullWidth variant={dispute.status === 'RESOLVED' ? 'outline' : 'primary'}>
                  <MessageSquare className="h-4 w-4" aria-hidden="true" />
                  <span>
                    {dispute.status === 'RESOLVED'
                      ? 'View Mediation Audit Trail'
                      : 'Open 3-Way Mediation Room'}
                  </span>
                </Button>
              </Link>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
