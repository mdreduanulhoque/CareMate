import { createContext, useContext } from 'react';

export const AuthContext = createContext(null);

export function getHomePathForRole(role) {
  switch (role) {
    case 'CUSTOMER':
      return '/customer/feed';
    case 'CARETAKER':
      return '/caretaker/requests';
    case 'ADMIN':
      return '/admin/applications';
    default:
      return '/login';
  }
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider.');
  }
  return context;
}
