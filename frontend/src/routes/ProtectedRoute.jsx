import { Loader2 } from 'lucide-react';
import { Navigate, Outlet } from 'react-router-dom';
import { getHomePathForRole, useAuth } from '../context/useAuth';

export function ProtectedRoute({ allowedRoles }) {
  const { isAuthenticated, isInitializing, user } = useAuth();

  if (isInitializing) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-slate-50 p-6">
        <div className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white px-5 py-4 text-sm font-medium text-slate-700 shadow-sm">
          <Loader2 className="h-5 w-5 animate-spin text-teal-700" aria-hidden="true" />
          <span>Restoring your CareBridge session...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={getHomePathForRole(user.role)} replace />;
  }

  return <Outlet />;
}

export function PublicOnlyRoute() {
  const { isAuthenticated, isInitializing, user } = useAuth();

  if (isInitializing) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-slate-50 p-6">
        <Loader2 className="h-6 w-6 animate-spin text-teal-700" aria-label="Loading" />
      </div>
    );
  }

  if (isAuthenticated) {
    return <Navigate to={getHomePathForRole(user.role)} replace />;
  }

  return <Outlet />;
}
