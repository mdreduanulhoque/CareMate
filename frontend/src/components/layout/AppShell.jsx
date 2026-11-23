import {
  Award,
  Bell,
  BriefcaseMedical,
  CalendarCheck2,
  ClipboardCheck,
  ClipboardList,
  LogOut,
  MessageSquare,
  Scale,
  Search,
  UserCircle2,
  Users,
  X,
} from 'lucide-react';
import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/useAuth';
import { useWebSocket } from '../../context/useWebSocket';
import { BrandLogo } from '../ui/BrandLogo';
import { ConnectionBanner } from '../ui/ConnectionBanner';
import { StatusBadge } from '../ui/StatusBadge';

const NAV_BY_ROLE = {
  CUSTOMER: [
    { to: '/customer/feed', label: 'Feed', Icon: Search },
    { to: '/customer/chats', label: 'Chats', Icon: MessageSquare },
    { to: '/customer/hires', label: 'My Hires', Icon: BriefcaseMedical },
    { to: '/customer/profile', label: 'Profile', Icon: UserCircle2 },
  ],
  CARETAKER: [
    { to: '/caretaker/requests', label: 'Requests', Icon: ClipboardList },
    { to: '/caretaker/chats', label: 'Chats', Icon: MessageSquare },
    { to: '/caretaker/bookings', label: 'Bookings', Icon: CalendarCheck2 },
    { to: '/caretaker/profile', label: 'Profile', Icon: UserCircle2 },
  ],
  ADMIN: [
    { to: '/admin/applications', label: 'Applications', Icon: ClipboardCheck },
    { to: '/admin/certifications', label: 'Certifications', Icon: Award },
    { to: '/admin/disputes', label: 'Disputes', Icon: Scale },
    { to: '/admin/users', label: 'Users', Icon: Users },
  ],
};

export function AppShell() {
  const { user, logout, refreshUser } = useAuth();
  const { subscribe } = useWebSocket();
  const location = useLocation();
  const navItems = NAV_BY_ROLE[user?.role] || [];

  const [liveToast, setLiveToast] = useState(null);

  useEffect(() => {
    if (!user) {
      return undefined;
    }

    const cleanups = [];

    // Private per-user notifications (new chat messages, contact unlocks, verification decisions)
    cleanups.push(
      subscribe('/user/queue/notifications', (event) => {
        if (!event?.title) {
          return;
        }
        if (event.eventType === 'APPLICATION_APPROVED') {
          refreshUser().catch(() => {});
        }

        // Suppress chat message toast when the user is already inside that exact room
        if (
          event.eventType === 'NEW_CHAT_MESSAGE' &&
          event.roomId &&
          location.pathname.includes(encodeURIComponent(event.roomId))
        ) {
          return;
        }

        let actionTo = null;
        let actionLabel = 'View';
        if (event.roomId) {
          const base = user.role === 'CUSTOMER' ? '/customer/chats' : '/caretaker/chats';
          actionTo = `${base}/${encodeURIComponent(event.roomId)}`;
          actionLabel = 'Open Chat';
        } else if (event.contractId) {
          if (user.role === 'CUSTOMER') {
            actionTo = '/customer/hires';
            actionLabel =
              event.eventType === 'CONTRACT_COMPLETED_FEEDBACK_READY'
                ? 'Leave Feedback'
                : 'View Hires';
          } else if (event.eventType === 'HIRE_REQUESTED') {
            actionTo = '/caretaker/requests';
            actionLabel = 'Review Request';
          } else {
            actionTo = '/caretaker/bookings';
            actionLabel =
              event.eventType === 'CONTRACT_COMPLETED_FEEDBACK_READY'
                ? 'Leave Feedback'
                : 'View Bookings';
          }
        } else if (
          event.eventType === 'APPLICATION_APPROVED' ||
          event.eventType === 'CERTIFICATION_APPROVED'
        ) {
          actionTo = '/caretaker/profile';
        }

        setLiveToast({
          id: Date.now(),
          title: event.title,
          message: event.message,
          actionTo,
          actionLabel,
        });
      })
    );

    // Live Admin queue alerts across every Admin tab
    if (user.role === 'ADMIN') {
      cleanups.push(
        subscribe('/topic/admin/alerts', (event) => {
          if (!event?.title) {
            return;
          }
          const isCert = event.eventType === 'NEW_CERTIFICATION';
          setLiveToast({
            id: Date.now(),
            title: event.title,
            message: event.message,
            actionTo: isCert ? '/admin/certifications' : '/admin/applications',
            actionLabel: isCert ? 'Review Cert' : 'Review App',
          });
        })
      );
    }

    return () => {
      cleanups.forEach((fn) => fn());
    };
  }, [user, subscribe, refreshUser, location.pathname]);

  useEffect(() => {
    if (!liveToast) {
      return undefined;
    }
    const timer = setTimeout(() => {
      setLiveToast((current) => (current?.id === liveToast.id ? null : current));
    }, 6500);
    return () => clearTimeout(timer);
  }, [liveToast]);

  return (
    <div className="flex min-h-dvh flex-col bg-slate-50">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
          <BrandLogo />

          <nav aria-label="Primary navigation" className="hidden items-center gap-1 md:flex">
            {navItems.map((item) => {
              const NavIcon = item.Icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    `inline-flex min-h-[44px] items-center gap-2 rounded-xl px-3.5 py-2 text-sm font-semibold transition-colors ${
                      isActive
                        ? 'bg-teal-50 text-teal-800'
                        : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                    }`
                  }
                >
                  <NavIcon className="h-4 w-4" aria-hidden="true" />
                  <span>{item.label}</span>
                </NavLink>
              );
            })}
          </nav>

          <div className="flex items-center gap-2">
            <div className="hidden sm:block">
              <StatusBadge status={user?.role} />
            </div>
            <button
              type="button"
              onClick={logout}
              aria-label="Log out"
              className="inline-flex min-h-[44px] min-w-[44px] items-center justify-center gap-1.5 rounded-xl border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition-colors hover:bg-slate-100"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              <span className="hidden sm:inline">Log out</span>
            </button>
          </div>
        </div>
        <ConnectionBanner />
      </header>

      {liveToast && (
        <div className="mx-auto w-full max-w-5xl px-4 pt-3">
          <div
            role="status"
            aria-live="polite"
            className="flex items-start justify-between gap-3 rounded-2xl border border-teal-200 bg-teal-50 px-4 py-3 text-sm text-teal-950 shadow-xs"
          >
            <div className="flex min-w-0 items-start gap-2.5">
              <Bell className="mt-0.5 h-4 w-4 shrink-0 text-teal-700" aria-hidden="true" />
              <div className="min-w-0">
                <p className="font-bold">{liveToast.title}</p>
                <p className="truncate text-xs text-teal-900">{liveToast.message}</p>
              </div>
            </div>

            <div className="flex shrink-0 items-center gap-1.5">
              {liveToast.actionTo && (
                <Link
                  to={liveToast.actionTo}
                  onClick={() => setLiveToast(null)}
                  className="inline-flex min-h-[36px] items-center rounded-lg bg-teal-700 px-3 text-xs font-bold text-white hover:bg-teal-800"
                >
                  {liveToast.actionLabel}
                </Link>
              )}
              <button
                type="button"
                onClick={() => setLiveToast(null)}
                aria-label="Dismiss notification"
                className="inline-flex h-9 w-9 items-center justify-center rounded-lg text-teal-800 hover:bg-teal-100"
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      )}

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 pb-24 pt-5 md:pb-10">
        <Outlet />
      </main>

      <nav
        aria-label="Mobile navigation"
        className="safe-bottom-nav fixed inset-x-0 bottom-0 z-30 border-t border-slate-200 bg-white/95 backdrop-blur md:hidden"
      >
        <div className="mx-auto grid max-w-lg grid-cols-4 gap-1 px-2 pt-1.5">
          {navItems.map((item) => {
            const NavIcon = item.Icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `flex min-h-[48px] flex-col items-center justify-center gap-0.5 rounded-xl px-1 py-1.5 text-[11px] font-semibold transition-colors ${
                    isActive ? 'bg-teal-50 text-teal-800' : 'text-slate-500 hover:text-slate-800'
                  }`
                }
              >
                <NavIcon className="h-5 w-5" aria-hidden="true" />
                <span className="truncate">{item.label}</span>
              </NavLink>
            );
          })}
        </div>
      </nav>
    </div>
  );
}
