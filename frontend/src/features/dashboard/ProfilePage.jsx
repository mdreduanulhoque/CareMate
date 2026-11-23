import { LogOut, Mail, MapPin, Phone, ShieldCheck, User } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/useAuth';

export function ProfilePage() {
  const { user, logout } = useAuth();

  return (
    <div className="mx-auto max-w-xl space-y-4">
      <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3.5">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-teal-700 text-lg font-bold text-white">
              {user?.fullName?.slice(0, 1)?.toUpperCase() || <User className="h-6 w-6" />}
            </div>
            <div>
              <h1 className="text-lg font-bold text-slate-900">{user?.fullName}</h1>
              <div className="mt-1 flex flex-wrap items-center gap-2">
                <StatusBadge status={user?.role} />
                <StatusBadge status="ACTIVE" label={user?.active ? 'Active Account' : 'Inactive'} />
              </div>
            </div>
          </div>
        </div>

        <dl className="mt-5 divide-y divide-slate-100 border-t border-slate-100 text-sm">
          <div className="flex items-center gap-3 py-3">
            <Phone className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
            <dt className="w-24 text-slate-500">Mobile</dt>
            <dd className="font-medium text-slate-900">{user?.mobileNumber}</dd>
          </div>
          <div className="flex items-center gap-3 py-3">
            <MapPin className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
            <dt className="w-24 text-slate-500">Address</dt>
            <dd className="font-medium text-slate-900">{user?.address}</dd>
          </div>
          <div className="flex items-center gap-3 py-3">
            <Mail className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
            <dt className="w-24 text-slate-500">Email</dt>
            <dd className="font-medium text-slate-900">{user?.email || 'Not provided (optional)'}</dd>
          </div>
          <div className="flex items-center gap-3 py-3">
            <ShieldCheck className="h-4 w-4 shrink-0 text-teal-600" aria-hidden="true" />
            <dt className="w-24 text-slate-500">Session</dt>
            <dd className="font-medium text-teal-800">Secured with Stateless JWT</dd>
          </div>
        </dl>

        <div className="mt-5 border-t border-slate-100 pt-4">
          <Button variant="outline" fullWidth onClick={logout}>
            <LogOut className="h-4 w-4" aria-hidden="true" />
            <span>Sign Out</span>
          </Button>
        </div>
      </section>
    </div>
  );
}
