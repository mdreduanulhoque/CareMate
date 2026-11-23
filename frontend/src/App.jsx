import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AppShell } from './components/layout/AppShell';
import { AuthProvider } from './context/AuthContext';
import { getHomePathForRole, useAuth } from './context/useAuth';
import { WebSocketProvider } from './context/WebSocketContext';
import { AdminApplicationsPage } from './features/admin/AdminApplicationsPage';
import { AdminCertificationsPage } from './features/admin/AdminCertificationsPage';
import { AdminDisputesPage } from './features/admin/AdminDisputesPage';
import { AdminUsersPage } from './features/admin/AdminUsersPage';
import { CaretakerRegisterPage } from './features/auth/CaretakerRegisterPage';
import { CustomerRegisterPage } from './features/auth/CustomerRegisterPage';
import { LoginPage } from './features/auth/LoginPage';
import { CaretakerProfilePage } from './features/caretaker/CaretakerProfilePage';
import { CaretakerRequestsGatePage } from './features/caretaker/CaretakerRequestsGatePage';
import { NegotiationChatRoomPage } from './features/chat/NegotiationChatRoomPage';
import { NegotiationChatsPage } from './features/chat/NegotiationChatsPage';
import { ProfilePage } from './features/dashboard/ProfilePage';
import { DisputeMediationRoomPage } from './features/disputes/DisputeMediationRoomPage';
import { CaretakerPublicProfilePage } from './features/feed/CaretakerPublicProfilePage';
import { HuntersFeedPage } from './features/feed/HuntersFeedPage';
import { CaretakerBookingsPage } from './features/hiring/CaretakerBookingsPage';
import { CustomerHiresPage } from './features/hiring/CustomerHiresPage';
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
        <WebSocketProvider>
          <Routes>
            <Route path="/" element={<RootRedirect />} />

            <Route element={<PublicOnlyRoute />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register/customer" element={<CustomerRegisterPage />} />
              <Route path="/register/caretaker" element={<CaretakerRegisterPage />} />
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['CUSTOMER']} />}>
              <Route path="/customer" element={<AppShell />}>
                <Route path="feed" element={<HuntersFeedPage />} />
                <Route
                  path="caretakers/:caretakerUserId"
                  element={<CaretakerPublicProfilePage />}
                />
                <Route path="chats" element={<NegotiationChatsPage />} />
                <Route path="chats/:roomId" element={<NegotiationChatRoomPage />} />
                <Route path="hires" element={<CustomerHiresPage />} />
                <Route path="disputes/:contractId" element={<DisputeMediationRoomPage />} />
                <Route path="profile" element={<ProfilePage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['CARETAKER']} />}>
              <Route path="/caretaker" element={<AppShell />}>
                <Route path="requests" element={<CaretakerRequestsGatePage />} />
                <Route path="chats" element={<NegotiationChatsPage />} />
                <Route path="chats/:roomId" element={<NegotiationChatRoomPage />} />
                <Route path="bookings" element={<CaretakerBookingsPage />} />
                <Route path="disputes/:contractId" element={<DisputeMediationRoomPage />} />
                <Route path="profile" element={<CaretakerProfilePage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
              <Route path="/admin" element={<AppShell />}>
                <Route path="applications" element={<AdminApplicationsPage />} />
                <Route path="certifications" element={<AdminCertificationsPage />} />
                <Route path="disputes" element={<AdminDisputesPage />} />
                <Route path="disputes/:contractId" element={<DisputeMediationRoomPage />} />
                <Route path="users" element={<AdminUsersPage />} />
              </Route>
            </Route>

            <Route path="*" element={<RootRedirect />} />
          </Routes>
        </WebSocketProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
