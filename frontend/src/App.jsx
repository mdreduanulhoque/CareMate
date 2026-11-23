import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { AuthProvider } from './context/AuthContext';
import { getHomePathForRole, useAuth } from './context/useAuth';
import { CaretakerRegisterPage } from './features/auth/CaretakerRegisterPage';
import { CustomerRegisterPage } from './features/auth/CustomerRegisterPage';
import { LoginPage } from './features/auth/LoginPage';
import { ProfilePage } from './features/dashboard/ProfilePage';
import { RoleWorkspacePage } from './features/dashboard/RoleWorkspacePage';
import { ProtectedRoute, PublicOnlyRoute } from './routes/ProtectedRoute';

function RootRedirect() {
  const { isAuthenticated, isInitializing, user } = useAuth();
  if (isInitializing) {
    return null;
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to={getHomePathForRole(user.role)} replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<RootRedirect />} />

          <Route element={<PublicOnlyRoute />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register/customer" element={<CustomerRegisterPage />} />
            <Route path="/register/caretaker" element={<CaretakerRegisterPage />} />
          </Route>

          <Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>
            <Route path="/customer" element={<AppShell />}>
              <Route
                path="feed"
                element={
                  <RoleWorkspacePage
                    title="Hunters Feed"
                    description="Browse and filter Admin-verified nurses and hospital caretakers by availability, BDT budget, area, and rating."
                    upcomingPhase="Phase 3"
                  />
                }
              />
              <Route
                path="chats"
                element={
                  <RoleWorkspacePage
                    title="Negotiation Chats"
                    description="Unlock caretaker contacts and negotiate shift timing and rates over real-time WebSocket chat."
                    upcomingPhase="Phase 4"
                  />
                }
              />
              <Route
                path="hires"
                element={
                  <RoleWorkspacePage
                    title="My Hires"
                    description="Track active hospital contracts, confirm payments, and leave mutual feedback."
                    upcomingPhase="Phase 5–7"
                  />
                }
              />
              <Route path="profile" element={<ProfilePage />} />
            </Route>
          </Route>

          <Route element={<ProtectedRoute allowedRoles={['CARETAKER']} />}>
            <Route path="/caretaker" element={<AppShell />}>
              <Route
                path="requests"
                element={
                  <RoleWorkspacePage
                    title="Incoming Requests"
                    description="Review customer ratings and accept or decline hospital care requests (max 2 non-overlapping shifts per day)."
                    upcomingPhase="Phase 5"
                  />
                }
              />
              <Route
                path="chats"
                element={
                  <RoleWorkspacePage
                    title="Negotiation Chats"
                    description="Communicate directly with hospitalized patients' relatives in real time."
                    upcomingPhase="Phase 4"
                  />
                }
              />
              <Route
                path="bookings"
                element={
                  <RoleWorkspacePage
                    title="My Bookings"
                    description="Manage scheduled shifts, early completions, payment confirmations, and reviews."
                    upcomingPhase="Phase 5–7"
                  />
                }
              />
              <Route path="profile" element={<ProfilePage />} />
            </Route>
          </Route>

          <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
            <Route path="/admin" element={<AppShell />}>
              <Route
                path="applications"
                element={
                  <RoleWorkspacePage
                    title="Caretaker Applications Queue"
                    description="Inspect submitted NIDs, certificates, and Google Drive links to approve or reject applicants with clear reasons."
                    upcomingPhase="Phase 2"
                    showAdminCheck
                  />
                }
              />
              <Route
                path="certifications"
                element={
                  <RoleWorkspacePage
                    title="Certification Verification Queue"
                    description="Verify new qualifications submitted by already-approved caretakers without interrupting their live profile."
                    upcomingPhase="Phase 2"
                  />
                }
              />
              <Route
                path="disputes"
                element={
                  <RoleWorkspacePage
                    title="Payment Dispute Mediation"
                    description="Join 3-way real-time WebSocket dispute rooms to mediate payment disagreements fairly."
                    upcomingPhase="Phase 7"
                  />
                }
              />
              <Route path="users" element={<ProfilePage />} />
            </Route>
          </Route>

          <Route path="*" element={<RootRedirect />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
