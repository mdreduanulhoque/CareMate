import { apiRequest } from './client';

export function registerCustomer(payload) {
  return apiRequest('/api/auth/register/customer', {
    method: 'POST',
    body: payload,
    auth: false,
  });
}

export function registerCaretaker(payload) {
  return apiRequest('/api/auth/register/caretaker', {
    method: 'POST',
    body: payload,
    auth: false,
  });
}

export function loginWithMobile(payload) {
  return apiRequest('/api/auth/login', {
    method: 'POST',
    body: payload,
    auth: false,
  });
}

export function fetchCurrentUser() {
  return apiRequest('/api/auth/me');
}

export function fetchAdminStatus() {
  return apiRequest('/api/admin/status');
}
