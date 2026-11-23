import { CheckCircle2, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { fetchAdminStatus } from '../../api/authApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/useAuth';

export function RoleWorkspacePage({ title, description, upcomingPhase, showAdminCheck = false }) {
  const { user } = useAuth();
  const [adminCheck, setAdminCheck] = useState(null);

  useEffect(() => {
    if (!showAdminCheck || user?.role !== 'ADMIN') {
      return;
    }
    let cancelled = false;
    fetchAdminStatus()
      .then((res) => {
        if (!cancelled) setAdminCheck({ ok: true, data: res });
      })
      .catch((err) => {
        if (!cancelled) setAdminCheck({ ok: false, message: err.message });
      });
    return () => {
      cancelled = true;
    };
  }, [showAdminCheck, user?.role]);

  return (
    <div className="space-y-4">
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <h1 className="text-xl font-bold tracking-tight text-slate-900">{title}</h1>
            <p className="text-sm leading-relaxed text-slate-600">{description}</p>
          </div>
          <StatusBadge status={user?.role} />
        </div>
      </section>

      {user?.role === 'CARETAKER' && (
        <AlertBanner variant="warning" title="Verification Step Ready in Phase 2">
          Your Caretaker account is active and secured. Submitting your NID, qualifications, and
          Google Drive documents for Admin verification will unlock in Phase 2.
        </AlertBanner>
      )}

      {showAdminCheck && adminCheck?.ok && (
        <AlertBanner variant="success" title="Protected Admin RBAC Verified">
          Authenticated as <strong>{adminCheck.data.adminName}</strong> (ID #{adminCheck.data.adminId})
          with verified <code className="rounded bg-emerald-100 px-1 py-0.5 text-xs">ROLE_ADMIN</code>{' '}
          access.
        </AlertBanner>
      )}

      {showAdminCheck && adminCheck && !adminCheck.ok && (
        <AlertBanner variant="error" title="Admin verification error">
          {adminCheck.message}
        </AlertBanner>
      )}

      <section className="rounded-2xl border border-dashed border-slate-300 bg-white/80 p-5">
        <div className="flex items-start gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
            {user?.role === 'ADMIN' ? (
              <ShieldCheck className="h-5 w-5" aria-hidden="true" />
            ) : (
              <CheckCircle2 className="h-5 w-5" aria-hidden="true" />
            )}
          </div>
          <div className="space-y-1">
            <h2 className="text-sm font-bold text-slate-900">Phase 1 Foundation Active</h2>
            <p className="text-sm leading-relaxed text-slate-600">
              You are signed in as <strong>{user?.fullName}</strong> ({user?.mobileNumber}). Full{' '}
              <strong>{title}</strong> capabilities are scheduled for <strong>{upcomingPhase}</strong>.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
