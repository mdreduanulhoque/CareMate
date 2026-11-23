import { apiRequest } from './client';

export function markCustomerPaymentDone(contractId, paymentReferenceNote) {
  return apiRequest(`/api/payments/contracts/${contractId}/customer-done`, {
    method: 'POST',
    body: { paymentReferenceNote: paymentReferenceNote || null },
  });
}

export function confirmCaretakerPaymentReceived(contractId) {
  return apiRequest(`/api/payments/contracts/${contractId}/caretaker-confirm`, {
    method: 'POST',
  });
}

export function refuseCaretakerPayment(contractId, caretakerStatement) {
  return apiRequest(`/api/payments/contracts/${contractId}/caretaker-refuse`, {
    method: 'POST',
    body: { caretakerStatement },
  });
}

export function fetchDisputes(status = '') {
  const query = status && status !== 'ALL' ? `?status=${encodeURIComponent(status)}` : '';
  return apiRequest(`/api/disputes${query}`);
}

export function fetchDisputeByContract(contractId) {
  return apiRequest(`/api/disputes/contract/${contractId}`);
}

export function updateDisputeStatement(contractId, statement) {
  return apiRequest(`/api/disputes/contract/${contractId}/statement`, {
    method: 'PUT',
    body: { statement },
  });
}

export function joinDisputeAsAdmin(contractId) {
  return apiRequest(`/api/disputes/contract/${contractId}/join`, {
    method: 'POST',
  });
}

export function resolveDisputeAsAdmin(contractId, adminResolutionNote) {
  return apiRequest(`/api/disputes/contract/${contractId}/resolve`, {
    method: 'POST',
    body: { adminResolutionNote },
  });
}

export function fetchDisputeRoomDetail(contractId, limit = 150) {
  return apiRequest(`/api/chats/dispute/${contractId}?limit=${limit}`);
}

export function sendDisputeMessageRest(contractId, content) {
  return apiRequest(`/api/chats/dispute/${contractId}/messages`, {
    method: 'POST',
    body: { content },
  });
}

export function fetchAdminUsers({ role = '', keyword = '' } = {}) {
  const params = new URLSearchParams();
  if (role && role !== 'ALL') {
    params.set('role', role);
  }
  if (keyword && keyword.trim()) {
    params.set('keyword', keyword.trim());
  }
  const query = params.toString();
  return apiRequest(`/api/admin/users${query ? `?${query}` : ''}`);
}

export function updateAdminUserStatus(userId, active) {
  return apiRequest(`/api/admin/users/${userId}/status`, {
    method: 'PATCH',
    body: { active },
  });
}

export function moderateCaretakerAvailability(caretakerUserId, available) {
  return apiRequest(`/api/admin/users/caretakers/${caretakerUserId}/availability`, {
    method: 'PATCH',
    body: { available },
  });
}
