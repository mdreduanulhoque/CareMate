import { Award, CheckCircle2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';
import { CaretakerVerificationPanel } from './CaretakerVerificationPanel';

export function CaretakerRequestsGatePage() {
  const { user } = useAuth();

  if (!user?.caretakerApproved) {
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
    <div className="mx-auto max-w-2xl space-y-4">
      <section className="rounded-2xl border border-emerald-200 bg-emerald-50/70 p-5">
        <div className="flex items-start gap-3">
          <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-700" aria-hidden="true" />
          <div className="space-y-1">
            <h1 className="text-lg font-bold text-emerald-950">You Are Verified on CareBridge</h1>
            <p className="text-sm text-emerald-900">
              Incoming hire requests with customer review previews and the 2-shifts-per-day booking
              engine will activate in Phase 5.
            </p>
          </div>
        </div>
      </section>

      <Link
        to="/caretaker/profile"
        className="flex min-h-[64px] items-center gap-3.5 rounded-2xl border border-slate-200 bg-white p-4 shadow-xs transition-colors hover:border-teal-300 hover:bg-teal-50/40"
      >
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-teal-100 text-teal-800">
          <Award className="h-5 w-5" aria-hidden="true" />
        </div>
        <div>
          <div className="text-sm font-bold text-slate-900">Manage Additional Certifications</div>
          <div className="text-xs text-slate-600">
            Submit new certificates for Admin verification while your profile stays active
          </div>
        </div>
      </Link>
    </div>
  );
}
