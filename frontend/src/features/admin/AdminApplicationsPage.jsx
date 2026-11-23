import {
  Calendar,
  Check,
  ChevronDown,
  ChevronUp,
  History,
  MapPin,
  Phone,
  RefreshCw,
  X,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import * as verificationApi from '../../api/verificationApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { DriveLinkBadge } from '../../components/ui/DriveLinkBadge';
import { RejectReasonModal } from '../../components/ui/RejectReasonModal';
import { StatusBadge } from '../../components/ui/StatusBadge';

const FILTERS = [
  { key: 'PENDING', label: 'Pending' },
  { key: 'APPROVED', label: 'Approved' },
  { key: 'REJECTED', label: 'Rejected' },
  { key: 'ALL', label: 'All' },
];

function formatDateTime(isoString) {
  if (!isoString) return '—';
  return new Date(isoString).toLocaleString('en-BD', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

export function AdminApplicationsPage() {
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');

  const [busyId, setBusyId] = useState(null);
  const [recheckingId, setRecheckingId] = useState(null);
  const [rejectTarget, setRejectTarget] = useState(null);
  const [rejectError, setRejectError] = useState('');

  const [historyByUser, setHistoryByUser] = useState({});
  const [expandedHistoryAppId, setExpandedHistoryAppId] = useState(null);

  const loadApplications = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await verificationApi.fetchAdminApplications(statusFilter);
      setApplications(data);
    } catch (err) {
      setError(err.message || 'Could not load applications queue.');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadApplications();
  }, [loadApplications]);

  async function handleApprove(app) {
    setBusyId(app.id);
    setFeedback('');
    setError('');
    try {
      await verificationApi.approveApplication(app.id);
      setFeedback(
        `Approved ${app.applicantFullName} (Application #${app.applicationNumber}). Caretaker profile is now unlocked.`
      );
      await loadApplications();
    } catch (err) {
      setError(err.message || 'Could not approve application.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleConfirmReject(reason) {
    if (!rejectTarget) return;
    setBusyId(rejectTarget.id);
    setRejectError('');
    try {
      await verificationApi.rejectApplication(rejectTarget.id, reason);
      setFeedback(
        `Rejected ${rejectTarget.applicantFullName}'s Application #${rejectTarget.applicationNumber}. They can now fix the issue and re-apply.`
      );
      setRejectTarget(null);
      await loadApplications();
    } catch (err) {
      setRejectError(err.message || 'Could not reject application.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleRecheckDrive(appId) {
    setRecheckingId(appId);
    try {
      await verificationApi.recheckApplicationDrive(appId);
      setTimeout(() => {
        loadApplications();
      }, 1200);
    } catch (err) {
      setError(err.message || 'Could not trigger Drive link re-check.');
    } finally {
      setRecheckingId(null);
    }
  }

  async function toggleHistory(app) {
    if (expandedHistoryAppId === app.id) {
      setExpandedHistoryAppId(null);
      return;
    }
    setExpandedHistoryAppId(app.id);
    if (!historyByUser[app.userId]) {
      try {
        const list = await verificationApi.fetchApplicantHistory(app.userId);
        setHistoryByUser((prev) => ({ ...prev, [app.userId]: list }));
      } catch (err) {
        setError(err.message || 'Could not load applicant history.');
      }
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-900">
            Caretaker Applications Queue
          </h1>
          <p className="text-sm text-slate-600">
            Inspect NID, qualifications, and Google Drive links before approving caretakers.
          </p>
        </div>

        <Button variant="outline" onClick={loadApplications} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
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

      {feedback && <AlertBanner variant="success">{feedback}</AlertBanner>}
      {error && <AlertBanner variant="error">{error}</AlertBanner>}

      {loading && applications.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
          Loading applications...
        </div>
      ) : applications.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
          No {statusFilter === 'ALL' ? '' : statusFilter.toLowerCase()} caretaker applications right
          now.
        </div>
      ) : (
        <div className="space-y-4">
          {applications.map((app) => {
            const history = historyByUser[app.userId];
            const isExpanded = expandedHistoryAppId === app.id;

            return (
              <article
                key={app.id}
                className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs"
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="text-base font-bold text-slate-900">
                        {app.applicantFullName}
                      </h2>
                      <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-bold text-slate-700">
                        Application #{app.applicationNumber}
                      </span>
                    </div>
                    <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-600">
                      <span className="inline-flex items-center gap-1">
                        <Phone className="h-3.5 w-3.5" /> {app.applicantMobileNumber}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <MapPin className="h-3.5 w-3.5" /> {app.applicantAddress}
                      </span>
                      <span className="inline-flex items-center gap-1">
                        <Calendar className="h-3.5 w-3.5" /> DOB: {app.dob}
                      </span>
                    </div>
                  </div>
                  <StatusBadge status={app.status} />
                </div>

                <div className="space-y-1.5 rounded-xl bg-slate-50 p-3.5 text-sm">
                  <p className="text-slate-900">
                    <strong>Qualification:</strong> {app.qualification}
                  </p>
                  <p className="text-slate-700">
                    <strong>Experience:</strong> {app.experience || 'Not provided'}
                  </p>
                  <p className="text-xs text-slate-500">
                    Submitted {formatDateTime(app.submittedAt)}
                    {app.reviewedByAdminName
                      ? ` · Reviewed by ${app.reviewedByAdminName} on ${formatDateTime(app.reviewedAt)}`
                      : ''}
                  </p>
                </div>

                <DriveLinkBadge
                  url={app.documentsDriveLink}
                  reachable={app.driveLinkReachable}
                  note={app.driveCheckNote}
                  onRecheck={() => handleRecheckDrive(app.id)}
                  rechecking={recheckingId === app.id}
                />

                {app.rejectionReason && (
                  <AlertBanner variant="error" title="Recorded Rejection Reason">
                    {app.rejectionReason}
                  </AlertBanner>
                )}

                {app.priorAttemptsCount > 0 && (
                  <div className="rounded-xl border border-slate-200 bg-slate-50/70">
                    <button
                      type="button"
                      onClick={() => toggleHistory(app)}
                      className="flex min-h-[44px] w-full items-center justify-between px-3.5 text-xs font-semibold text-slate-700 hover:text-slate-900"
                    >
                      <span className="inline-flex items-center gap-1.5">
                        <History className="h-4 w-4 text-teal-700" />
                        <span>
                          View Prior Application Audit Trail ({app.priorAttemptsCount} previous)
                        </span>
                      </span>
                      {isExpanded ? (
                        <ChevronUp className="h-4 w-4" />
                      ) : (
                        <ChevronDown className="h-4 w-4" />
                      )}
                    </button>

                    {isExpanded && (
                      <div className="space-y-2 border-t border-slate-200 p-3 text-xs">
                        {!history ? (
                          <p className="text-slate-500">Loading history...</p>
                        ) : (
                          history.map((item) => (
                            <div
                              key={item.id}
                              className="rounded-lg border border-slate-200 bg-white p-2.5"
                            >
                              <div className="flex items-center justify-between gap-2">
                                <span className="font-bold text-slate-900">
                                  Attempt #{item.applicationNumber}
                                </span>
                                <StatusBadge status={item.status} />
                              </div>
                              <p className="mt-1 text-slate-700">{item.qualification}</p>
                              {item.rejectionReason && (
                                <p className="mt-1 text-rose-700">
                                  <strong>Rejection:</strong> {item.rejectionReason}
                                </p>
                              )}
                            </div>
                          ))
                        )}
                      </div>
                    )}
                  </div>
                )}

                {app.status === 'PENDING' && (
                  <div className="grid grid-cols-2 gap-2.5 pt-1">
                    <Button
                      variant="danger"
                      onClick={() => {
                        setRejectError('');
                        setRejectTarget(app);
                      }}
                      disabled={busyId === app.id}
                    >
                      <X className="h-4 w-4" />
                      <span>Reject</span>
                    </Button>
                    <Button
                      variant="primary"
                      onClick={() => handleApprove(app)}
                      loading={busyId === app.id}
                    >
                      <Check className="h-4 w-4" />
                      <span>Approve</span>
                    </Button>
                  </div>
                )}
              </article>
            );
          })}
        </div>
      )}

      {rejectTarget && (
        <RejectReasonModal
          title={`Reject Application #${rejectTarget.applicationNumber}`}
          subtitle={`Applicant: ${rejectTarget.applicantFullName} (${rejectTarget.applicantMobileNumber})`}
          submitting={busyId === rejectTarget.id}
          error={rejectError}
          onClose={() => setRejectTarget(null)}
          onConfirm={handleConfirmReject}
        />
      )}
    </div>
  );
}
