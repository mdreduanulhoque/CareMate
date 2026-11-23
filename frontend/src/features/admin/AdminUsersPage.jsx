import {
  LogOut,
  MapPin,
  Phone,
  RefreshCw,
  Search,
  ShieldCheck,
  UserCheck,
  UserX,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import * as paymentDisputeApi from '../../api/paymentDisputeApi';
import { AlertBanner } from '../../components/ui/AlertBanner';
import { Button } from '../../components/ui/Button';
import { StatusBadge } from '../../components/ui/StatusBadge';
import { useAuth } from '../../context/useAuth';
import { formatDhakaDateTime } from '../../utils/dhakaTime';

const ROLE_FILTERS = [
  { key: 'ALL', label: 'All Users' },
  { key: 'CUSTOMER', label: 'Relatives' },
  { key: 'CARETAKER', label: 'Caretakers' },
  { key: 'ADMIN', label: 'Admins' },
];

export function AdminUsersPage() {
  const { user: currentAdmin, logout } = useAuth();

  const [roleFilter, setRoleFilter] = useState('ALL');
  const [keywordInput, setKeywordInput] = useState('');
  const [activeKeyword, setActiveKeyword] = useState('');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [feedback, setFeedback] = useState('');
  const [busyId, setBusyId] = useState(null);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await paymentDisputeApi.fetchAdminUsers({
        role: roleFilter,
        keyword: activeKeyword,
      });
      setUsers(data);
    } catch (err) {
      setError(err.message || 'Could not load users directory.');
    } finally {
      setLoading(false);
    }
  }, [roleFilter, activeKeyword]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  async function handleToggleUserStatus(target) {
    setBusyId(target.id);
    setError('');
    setFeedback('');
    try {
      const updated = await paymentDisputeApi.updateAdminUserStatus(target.id, !target.active);
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
      setFeedback(
        `${updated.fullName} is now ${updated.active ? 'Active' : 'Deactivated'}.`
      );
    } catch (err) {
      setError(err.message || 'Could not update user status.');
    } finally {
      setBusyId(null);
    }
  }

  async function handleToggleCaretakerAvailability(target) {
    setBusyId(target.id);
    setError('');
    setFeedback('');
    try {
      const updated = await paymentDisputeApi.moderateCaretakerAvailability(
        target.id,
        !target.caretakerAvailable
      );
      setUsers((prev) => prev.map((u) => (u.id === updated.id ? updated : u)));
      setFeedback(
        `${updated.fullName}'s Hunters Feed availability set to ${
          updated.caretakerAvailable ? 'Available' : 'Unavailable'
        }.`
      );
    } catch (err) {
      setError(err.message || 'Could not moderate caretaker availability.');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div className="space-y-4">
      <section className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-indigo-200 bg-indigo-50/70 p-4">
        <div className="flex items-center gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-700 text-white">
            <ShieldCheck className="h-5 w-5" aria-hidden="true" />
          </div>
          <div>
            <div className="text-sm font-bold text-indigo-950">{currentAdmin?.fullName}</div>
            <div className="text-xs text-indigo-800">
              Platform Administrator · {currentAdmin?.mobileNumber}
            </div>
          </div>
        </div>
        <Button variant="outline" onClick={logout}>
          <LogOut className="h-4 w-4" aria-hidden="true" />
          <span>Sign Out</span>
        </Button>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold tracking-tight text-slate-900">Users &amp; Moderation</h1>
          <p className="text-sm text-slate-600">
            Search registered relatives and caretakers, moderate availability, or manage account
            access.
          </p>
        </div>

        <Button variant="outline" onClick={loadUsers} disabled={loading}>
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          <span>Refresh</span>
        </Button>
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          setActiveKeyword(keywordInput);
        }}
        className="flex gap-2"
      >
        <div className="relative flex-1">
          <Search
            className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
            aria-hidden="true"
          />
          <input
            type="search"
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            placeholder="Search by name, mobile number, or Dhaka area..."
            aria-label="Search users"
            className="block min-h-[44px] w-full rounded-xl border border-slate-300 bg-white pl-10 pr-3.5 text-sm text-slate-900 focus:border-teal-600 focus:outline-none focus:ring-2 focus:ring-teal-100"
          />
        </div>
        <Button type="submit">Search</Button>
      </form>

      <div className="flex gap-2 overflow-x-auto pb-1">
        {ROLE_FILTERS.map((item) => (
          <button
            key={item.key}
            type="button"
            onClick={() => setRoleFilter(item.key)}
            className={`min-h-[44px] shrink-0 rounded-full border px-4 text-xs font-semibold transition-colors ${
              roleFilter === item.key
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

      {loading && users.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
          Loading platform users...
        </div>
      ) : users.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-8 text-center text-sm text-slate-500">
          No matching users found.
        </div>
      ) : (
        <div className="space-y-3">
          {users.map((u) => (
            <article
              key={u.id}
              className="flex flex-col gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-xs sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0 space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="truncate text-sm font-bold text-slate-900">{u.fullName}</h2>
                  <StatusBadge status={u.role} />
                  {u.role === 'CARETAKER' && (
                    <StatusBadge
                      status={u.caretakerApproved ? 'APPROVED' : 'PENDING'}
                      label={u.caretakerApproved ? 'Verified' : 'Unverified'}
                    />
                  )}
                  {!u.active && (
                    <span className="rounded-full bg-rose-100 px-2 py-0.5 text-[11px] font-bold text-rose-800">
                      Deactivated
                    </span>
                  )}
                </div>

                <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-slate-600">
                  <span className="inline-flex items-center gap-1">
                    <Phone className="h-3.5 w-3.5 text-slate-400" /> {u.mobileNumber}
                  </span>
                  <span className="inline-flex items-center gap-1">
                    <MapPin className="h-3.5 w-3.5 text-slate-400" /> {u.address}
                  </span>
                  <span className="text-slate-400">Joined {formatDhakaDateTime(u.createdAt)}</span>
                </div>
              </div>

              {u.role !== 'ADMIN' && (
                <div className="flex flex-wrap items-center gap-2">
                  {u.role === 'CARETAKER' && u.caretakerApproved && (
                    <Button
                      variant="outline"
                      onClick={() => handleToggleCaretakerAvailability(u)}
                      disabled={busyId === u.id}
                    >
                      {u.caretakerAvailable ? 'Set Unavailable' : 'Set Available'}
                    </Button>
                  )}

                  <Button
                    variant={u.active ? 'danger' : 'primary'}
                    onClick={() => handleToggleUserStatus(u)}
                    loading={busyId === u.id}
                  >
                    {u.active ? (
                      <>
                        <UserX className="h-4 w-4" aria-hidden="true" />
                        <span>Deactivate</span>
                      </>
                    ) : (
                      <>
                        <UserCheck className="h-4 w-4" aria-hidden="true" />
                        <span>Activate</span>
                      </>
                    )}
                  </Button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
