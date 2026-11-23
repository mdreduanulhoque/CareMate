import { Award, Check, Phone, RefreshCw, X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import * as verificationApi from '../../api/verificationApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { DriveLinkBadge } from '../../components/ui/DriveLinkBadge';
import { RejectReasonModal } from '../../components/ui/RejectReasonModal';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useWebSocket } from '../../context/useWebSocket';

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

export function AdminCertificationsPage() {
  const { isConnected, subscribe } = useWebSocket();
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [certifications, setCertifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');

  const [busyId, setBusyId] = useState(null);
  const [recheckingId, setRecheckingId] = useState(null);
  const [rejectTarget, setRejectTarget] = useState(null);
  const [rejectError, setRejectError] = useState('');

  const loadCertifications = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await verificationApi.fetchAdminCertifications(statusFilter);
      setCertifications(data);
    } catch (err) {
      setError(err.message || 'Could not load certification queue.');
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadCertifications();
  }, [loadCertifications]);

  // Auto-refresh Admin Certifications queue on real-time /topic/admin/alerts
  useEffect(() => {
    return subscribe('/topic/admin/alerts', (event) => {
      if (event?.eventType === 'NEW_CERTIFICATION') {
        setFeedback(`Live alert: ${event.title} — ${event.message}`);
        loadCertifications();
      }
    });
  }, [subscribe, loadCertifications]);

  async function handleApprove(cert) {
    setBusyId(cert.id);
    setFeedback('');
    setError('');
    try {
      await verificationApi.approveCertification(cert.id);
      setFeedback(`Approved "${cert.title}" for ${cert.caretakerFullName}.`);
      await loadCertifications();
    } catch (err) {
      setError(err.message || 'Could not approve certification.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleConfirmReject(reason) {
    if (!rejectTarget) return;
    setBusyId(rejectTarget.id);
    setRejectError('');
    try {
      await verificationApi.rejectCertification(rejectTarget.id, reason);
      setFeedback(
        `Rejected "${rejectTarget.title}" for ${rejectTarget.caretakerFullName}. Their live caretaker profile remains active.`
      );
      setRejectTarget(null);
      await loadCertifications();
    } catch (err) {
      setRejectError(err.message || 'Could not reject certification.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleRecheckDrive(certId) {
    setRecheckingId(certId);
    try {
      await verificationApi.recheckCertificationDrive(certId);
      setTimeout(() => {
        loadCertifications();
      }, 1200);
    } catch (err) {
      setError(err.message || 'Could not re-check Drive link.');
    } finally {
      setRecheckingId(null);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-bold tracking-tight text-slate-900">
              Certification Verification Queue
            </h1>
            {isConnected && (
              <span className="inline-flex items-center gap-1 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-[11px] font-bold text-emerald-800">
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                Live Queue
              </span>
            )}
          </div>
          <p className="text-sm text-slate-600">
            Verify additional certificates added by approved caretakers without disrupting their
            live feed profile.
          </p>
        </div>

        <Button variant="outline" onClick={loadCertifications} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          <span>Refresh</span>
        </Button>
      </div>

      <div className="no-scrollbar flex gap-2 overflow-x-auto pb-1">
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

      {loading && certifications.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
          Loading certifications...
        </div>
      ) : certifications.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
          No {statusFilter === 'ALL' ? '' : statusFilter.toLowerCase()} certification requests right
          now.
        </div>
      ) : (
        <div className="space-y-4">
          {certifications.map((cert) => (
            <article
              key={cert.id}
              className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-xs"
            >
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <Award className="h-4 w-4 text-teal-700" aria-hidden="true" />
                    <h2 className="text-base font-bold text-slate-900">{cert.title}</h2>
                  </div>
                  <p className="text-xs font-medium text-slate-600">
                    Issued by {cert.issuingOrganization}
                  </p>
                </div>
                <StatusBadge status={cert.verificationStatus} />
              </div>

              <div className="rounded-xl bg-slate-50 p-3.5 text-sm space-y-1">
                <p className="text-slate-900">
                  <strong>Caretaker:</strong> {cert.caretakerFullName}{' '}
                  <span className="inline-flex items-center gap-1 text-xs text-slate-600">
                    (<Phone className="h-3 w-3" /> {cert.caretakerMobileNumber})
                  </span>
                </p>
                <p className="text-slate-700">
                  <strong>Base Qualification:</strong> {cert.caretakerPrimaryQualification}
                </p>
                <p className="text-xs text-slate-500">
                  Submitted {formatDateTime(cert.submittedAt)}
                  {cert.reviewedByAdminName
                    ? ` · Reviewed by ${cert.reviewedByAdminName} on ${formatDateTime(cert.reviewedAt)}`
                    : ''}
                </p>
              </div>

              <DriveLinkBadge
                url={cert.documentDriveLink}
                reachable={cert.driveLinkReachable}
                note={cert.driveCheckNote}
                onRecheck={() => handleRecheckDrive(cert.id)}
                rechecking={recheckingId === cert.id}
              />

              {cert.rejectionReason && (
                <AlertBanner variant="error" title="Rejection Reason">
                  {cert.rejectionReason}
                </AlertBanner>
              )}

              {cert.verificationStatus === 'PENDING' && (
                <div className="grid grid-cols-2 gap-2.5 pt-1">
                  <Button
                    variant="danger"
                    onClick={() => {
                      setRejectError('');
                      setRejectTarget(cert);
                    }}
                    disabled={busyId === cert.id}
                  >
                    <X className="h-4 w-4" />
                    <span>Reject</span>
                  </Button>
                  <Button
                    variant="primary"
                    onClick={() => handleApprove(cert)}
                    loading={busyId === cert.id}
                  >
                    <Check className="h-4 w-4" />
                    <span>Approve</span>
                  </Button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}

      {rejectTarget && (
        <RejectReasonModal
          title="Reject New Certification"
          subtitle={`${rejectTarget.title} — ${rejectTarget.caretakerFullName}`}
          submitting={busyId === rejectTarget.id}
          error={rejectError}
          onClose={() => setRejectTarget(null)}
          onConfirm={handleConfirmReject}
        />
      )}
    </div>
  );
}
