import {
  Award,
  CheckCircle2,
  ClipboardList,
  Clock,
  FilePlus2,
  History,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import * as verificationApi from '../../api/verificationApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { DriveLinkBadge } from '../../components/ui/DriveLinkBadge';
import { FormField } from '../../components/ui/FormField';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/useAuth';

function formatDateTime(isoString) {
  if (!isoString) return '—';
  return new Date(isoString).toLocaleString('en-BD', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

export function CaretakerVerificationPanel({ showCertificationsWhenApproved = true }) {
  const { refreshUser } = useAuth();

  const [statusData, setStatusData] = useState(null);
  const [certifications, setCertifications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  const [appForm, setAppForm] = useState({
    dob: '',
    qualification: '',
    experience: '',
    documentsDriveLink: '',
  });
  const [appSubmitting, setAppSubmitting] = useState(false);
  const [appError, setAppError] = useState('');
  const [appFieldErrors, setAppFieldErrors] = useState({});
  const [appSuccess, setAppSuccess] = useState('');

  const [certForm, setCertForm] = useState({
    title: '',
    issuingOrganization: '',
    documentDriveLink: '',
  });
  const [certSubmitting, setCertSubmitting] = useState(false);
  const [certError, setCertError] = useState('');
  const [certFieldErrors, setCertFieldErrors] = useState({});
  const [certSuccess, setCertSuccess] = useState('');

  const loadVerificationState = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const appStatus = await verificationApi.fetchMyApplications();
      setStatusData(appStatus);

      if (appStatus.approved) {
        await refreshUser();
        if (showCertificationsWhenApproved) {
          const certs = await verificationApi.fetchMyCertifications();
          setCertifications(certs);
        }
      }
    } catch (error) {
      setLoadError(error.message || 'Could not load your verification status.');
    } finally {
      setLoading(false);
    }
  }, [refreshUser, showCertificationsWhenApproved]);

  useEffect(() => {
    loadVerificationState();
  }, [loadVerificationState]);

  async function handleApplicationSubmit(event) {
    event.preventDefault();
    setAppError('');
    setAppFieldErrors({});
    setAppSuccess('');
    setAppSubmitting(true);

    try {
      const created = await verificationApi.submitCaretakerApplication({
        dob: appForm.dob,
        qualification: appForm.qualification,
        experience: appForm.experience.trim() || null,
        documentsDriveLink: appForm.documentsDriveLink,
      });
      setAppSuccess(
        `Application #${created.applicationNumber} submitted! Our system is checking your Google Drive link and an Admin will review it shortly.`
      );
      setAppForm({ dob: '', qualification: '', experience: '', documentsDriveLink: '' });
      await loadVerificationState();
    } catch (error) {
      setAppError(error.message || 'Could not submit application.');
      if (error.fieldErrors) setAppFieldErrors(error.fieldErrors);
    } finally {
      setAppSubmitting(false);
    }
  }

  async function handleCertificationSubmit(event) {
    event.preventDefault();
    setCertError('');
    setCertFieldErrors({});
    setCertSuccess('');
    setCertSubmitting(true);

    try {
      await verificationApi.submitNewCertification(certForm);
      setCertSuccess(
        'New certification submitted for Admin verification. Your live profile remains active without interruption.'
      );
      setCertForm({ title: '', issuingOrganization: '', documentDriveLink: '' });
      await loadVerificationState();
    } catch (error) {
      setCertError(error.message || 'Could not submit certification.');
      if (error.fieldErrors) setCertFieldErrors(error.fieldErrors);
    } finally {
      setCertSubmitting(false);
    }
  }

  if (loading && !statusData) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 text-center text-sm text-slate-500">
        Loading verification details...
      </div>
    );
  }

  if (loadError) {
    return (
      <AlertBanner variant="error" title="Unable to load verification status">
        <div className="space-y-2">
          <p>{loadError}</p>
          <Button variant="outline" onClick={loadVerificationState}>
            Retry
          </Button>
        </div>
      </AlertBanner>
    );
  }

  const latestApp = statusData?.applications?.[0] || null;

  return (
    <div className="space-y-5">
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-teal-700" aria-hidden="true" />
              <h2 className="text-lg font-bold text-slate-900">
                {statusData?.approved ? 'Verified Caretaker Account' : 'Caretaker Verification'}
              </h2>
            </div>
            <p className="text-sm text-slate-600">
              {statusData?.approved
                ? 'Your credentials are verified by CareBridge Admin. You can add additional certificates anytime.'
                : 'Submit your personal details and Google Drive link containing your NID, certificates, and experience letters.'}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <StatusBadge status={statusData?.approved ? 'APPROVED' : statusData?.latestStatus} />
            <button
              type="button"
              onClick={loadVerificationState}
              disabled={loading}
              aria-label="Refresh verification status"
              className="inline-flex h-10 w-10 items-center justify-center rounded-xl border border-slate-200 text-slate-600 hover:bg-slate-100"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
            </button>
          </div>
        </div>

        {appSuccess && (
          <AlertBanner variant="success" className="mt-4">
            {appSuccess}
          </AlertBanner>
        )}

        {!statusData?.approved && statusData?.latestStatus === 'PENDING' && latestApp && (
          <div className="mt-4 space-y-3 rounded-xl border border-amber-200 bg-amber-50/60 p-4">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-2 text-sm font-bold text-amber-950">
                <Clock className="h-4 w-4" aria-hidden="true" />
                <span>Application #{latestApp.applicationNumber} Under Admin Review</span>
              </div>
              <span className="text-xs text-amber-800">{formatDateTime(latestApp.submittedAt)}</span>
            </div>
            <p className="text-sm text-amber-900">
              <strong>Qualification:</strong> {latestApp.qualification}
            </p>
            {latestApp.experience && (
              <p className="text-sm text-amber-900">
                <strong>Experience:</strong> {latestApp.experience}
              </p>
            )}
            <DriveLinkBadge
              url={latestApp.documentsDriveLink}
              reachable={latestApp.driveLinkReachable}
              note={latestApp.driveCheckNote}
            />
          </div>
        )}

        {!statusData?.approved && statusData?.latestStatus === 'REJECTED' && latestApp && (
          <AlertBanner
            variant="error"
            title={`Application #${latestApp.applicationNumber} Was Rejected`}
            className="mt-4"
          >
            <p className="font-medium">{latestApp.rejectionReason}</p>
            <p className="mt-1 text-xs opacity-90">
              Please fix the issue above and submit a new application (Attempt #
              {statusData.nextApplicationNumber}) below. Your previous submission remains archived
              for Admin reference.
            </p>
          </AlertBanner>
        )}

        {statusData?.canSubmitNewApplication && (
          <form onSubmit={handleApplicationSubmit} className="mt-5 space-y-4 border-t border-slate-100 pt-4">
            <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
              <ClipboardList className="h-4 w-4 text-teal-700" aria-hidden="true" />
              <span>
                {statusData.nextApplicationNumber === 1
                  ? 'Submit Verification Application (#1)'
                  : `Submit New Re-Application (Attempt #${statusData.nextApplicationNumber})`}
              </span>
            </div>

            {appError && (
              <AlertBanner variant="error" title="Could not submit application">
                {appError}
              </AlertBanner>
            )}

            <div className="grid gap-4 sm:grid-cols-2">
              <FormField
                id="app-dob"
                label="Date of Birth"
                type="date"
                value={appForm.dob}
                onChange={(e) => setAppForm((p) => ({ ...p, dob: e.target.value }))}
                error={appFieldErrors.dob}
                hint="Must be 18 or older"
                required
              />

              <FormField
                id="app-qualification"
                label="Relevant Qualification"
                placeholder="e.g., BSc in Nursing / Trained ICU Attendant"
                value={appForm.qualification}
                onChange={(e) => setAppForm((p) => ({ ...p, qualification: e.target.value }))}
                error={appFieldErrors.qualification}
                required
              />
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <label htmlFor="app-experience" className="text-sm font-medium text-slate-800">
                  Hospital / Care Experience
                </label>
                <span className="text-xs font-medium text-slate-400">Optional</span>
              </div>
              <textarea
                id="app-experience"
                rows={3}
                value={appForm.experience}
                onChange={(e) => setAppForm((p) => ({ ...p, experience: e.target.value }))}
                placeholder="Briefly describe your ward, ICU, elderly care, or post-surgery experience..."
                className="block w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
              />
              {appFieldErrors.experience && (
                <p className="text-xs font-medium text-rose-600">{appFieldErrors.experience}</p>
              )}
            </div>

            <FormField
              id="app-drive"
              label="Google Drive Documents Link"
              type="url"
              placeholder="https://drive.google.com/drive/folders/..."
              value={appForm.documentsDriveLink}
              onChange={(e) => setAppForm((p) => ({ ...p, documentsDriveLink: e.target.value }))}
              error={appFieldErrors.documentsDriveLink}
              hint="Include NID, Certificates & Experience Letters. Set sharing to 'Anyone with the link can view'."
              required
            />

            <Button type="submit" fullWidth loading={appSubmitting}>
              {statusData.nextApplicationNumber === 1
                ? 'Submit Application for Admin Verification'
                : `Submit Re-Application #${statusData.nextApplicationNumber}`}
            </Button>
          </form>
        )}
      </section>

      {statusData?.approved && showCertificationsWhenApproved && (
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs space-y-5">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Award className="h-5 w-5 text-teal-700" aria-hidden="true" />
              <h2 className="text-lg font-bold text-slate-900">Additional Certifications</h2>
            </div>
            <p className="text-sm text-slate-600">
              Submit new qualifications anytime. New certificates stay pending until Admin review
              while your approved profile stays live in the feed.
            </p>
          </div>

          {certSuccess && <AlertBanner variant="success">{certSuccess}</AlertBanner>}
          {certError && <AlertBanner variant="error">{certError}</AlertBanner>}

          <form onSubmit={handleCertificationSubmit} className="space-y-4 rounded-xl border border-slate-200 bg-slate-50/70 p-4">
            <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
              <FilePlus2 className="h-4 w-4 text-teal-700" aria-hidden="true" />
              <span>Add New Certification</span>
            </div>

            <div className="grid gap-3.5 sm:grid-cols-2">
              <FormField
                id="cert-title"
                label="Certification Title"
                placeholder="e.g., ICU & Ventilator Care Diploma"
                value={certForm.title}
                onChange={(e) => setCertForm((p) => ({ ...p, title: e.target.value }))}
                error={certFieldErrors.title}
                required
              />
              <FormField
                id="cert-org"
                label="Issuing Organization"
                placeholder="e.g., Bangladesh Nursing Council"
                value={certForm.issuingOrganization}
                onChange={(e) =>
                  setCertForm((p) => ({ ...p, issuingOrganization: e.target.value }))
                }
                error={certFieldErrors.issuingOrganization}
                required
              />
            </div>

            <FormField
              id="cert-drive"
              label="Certificate Google Drive Link"
              type="url"
              placeholder="https://drive.google.com/file/d/..."
              value={certForm.documentDriveLink}
              onChange={(e) => setCertForm((p) => ({ ...p, documentDriveLink: e.target.value }))}
              error={certFieldErrors.documentDriveLink}
              hint="Must be a public HTTPS Google Drive or Docs link"
              required
            />

            <Button type="submit" fullWidth loading={certSubmitting}>
              Submit Certification for Review
            </Button>
          </form>

          <div className="space-y-3">
            <h3 className="text-sm font-bold text-slate-800">
              Submitted Certifications ({certifications.length})
            </h3>
            {certifications.length === 0 ? (
              <p className="rounded-xl border border-dashed border-slate-200 p-4 text-center text-sm text-slate-500">
                No additional certifications submitted yet.
              </p>
            ) : (
              certifications.map((cert) => (
                <article
                  key={cert.id}
                  className="space-y-2.5 rounded-xl border border-slate-200 p-4"
                >
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <h4 className="text-sm font-bold text-slate-900">{cert.title}</h4>
                      <p className="text-xs text-slate-600">{cert.issuingOrganization}</p>
                    </div>
                    <StatusBadge status={cert.verificationStatus} />
                  </div>

                  <DriveLinkBadge
                    url={cert.documentDriveLink}
                    reachable={cert.driveLinkReachable}
                    note={cert.driveCheckNote}
                  />

                  {cert.rejectionReason && (
                    <AlertBanner variant="error" title="Rejection Reason">
                      {cert.rejectionReason}
                    </AlertBanner>
                  )}

                  {cert.verificationStatus === 'APPROVED' && (
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-emerald-700">
                      <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />
                      <span>Verified badge visible on your public profile</span>
                    </div>
                  )}
                </article>
              ))
            )}
          </div>
        </section>
      )}

      {statusData?.applications?.length > 0 && (
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs space-y-3">
          <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
            <History className="h-4 w-4 text-slate-500" aria-hidden="true" />
            <span>Application History ({statusData.applications.length})</span>
          </div>

          <div className="space-y-3">
            {statusData.applications.map((app) => (
              <article
                key={app.id}
                className="space-y-2 rounded-xl border border-slate-200 bg-slate-50/50 p-3.5 text-sm"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-bold text-slate-900">
                    Application #{app.applicationNumber}
                  </span>
                  <StatusBadge status={app.status} />
                </div>
                <p className="text-slate-700">{app.qualification}</p>
                <p className="text-xs text-slate-500">
                  Submitted {formatDateTime(app.submittedAt)}
                  {app.reviewedAt ? ` · Reviewed ${formatDateTime(app.reviewedAt)}` : ''}
                </p>
                {app.rejectionReason && (
                  <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-800">
                    <strong>Reason:</strong> {app.rejectionReason}
                  </p>
                )}
              </article>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
