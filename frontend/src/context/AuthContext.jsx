import { useCallback, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/authApi';
import { getStoredToken, registerUnauthorizedHandler, setStoredToken } from '../api/client';
import { AuthContext } from './useAuth';

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(() => getStoredToken());
  const [isInitializing, setIsInitializing] = useState(() => Boolean(getStoredToken()));

  const clearSession = useCallback(() => {
    setStoredToken(null);
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    registerUnauthorizedHandler(clearSession);
    return () => registerUnauthorizedHandler(null);
  }, [clearSession]);

  useEffect(() => {
    if (!token) {
      setIsInitializing(false);
      return;
    }

    let cancelled = false;
    authApi
      .fetchCurrentUser()
      .then((currentUser) => {
        if (!cancelled) {
          setUser(currentUser);
        }
      })
      .catch(() => {
        if (!cancelled) {
          clearSession();
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsInitializing(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [token, clearSession]);

  const applyAuthResponse = useCallback((authResponse) => {
    setStoredToken(authResponse.accessToken);
    setToken(authResponse.accessToken);
    setUser(authResponse.user);
    return authResponse.user;
  }, []);

  const login = useCallback(
    async (credentials) => {
      const response = await authApi.loginWithMobile(credentials);
      return applyAuthResponse(response);
    },
    [applyAuthResponse]
  );

  const registerCustomer = useCallback(
    async (payload) => {
      const response = await authApi.registerCustomer(payload);
      return applyAuthResponse(response);
    },
    [applyAuthResponse]
  );

  const registerCaretaker = useCallback(
    async (payload) => {
      const response = await authApi.registerCaretaker(payload);
      return applyAuthResponse(response);
    },
    [applyAuthResponse]
  );

  const refreshUser = useCallback(async () => {
    if (!token) {
      return null;
    }
    const currentUser = await authApi.fetchCurrentUser();
    setUser(currentUser);
    return currentUser;
  }, [token]);

  const value = useMemo(
    () => ({
      user,
      token,
      isAuthenticated: Boolean(user && token),
      isInitializing,
      login,
      registerCustomer,
      registerCaretaker,
      refreshUser,
      logout: clearSession,
    }),
    [
      user,
      token,
      isInitializing,
      login,
      registerCustomer,
      registerCaretaker,
      refreshUser,
      clearSession,
    ]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
