import { apiRequest } from './client';

export function createHireContract(payload) {
  return apiRequest('/api/hires', {
    method: 'POST',
    body: payload,
  });
}

export function fetchMyHires(statuses = []) {
  const params = new URLSearchParams();
  if (Array.isArray(statuses) && statuses.length > 0) {
    params.set('status', statuses.join(','));
  }
  const query = params.toString();
  return apiRequest(`/api/hires${query ? `?${query}` : ''}`);
}

export function fetchHireDetail(contractId) {
  return apiRequest(`/api/hires/${contractId}`);
}

export function fetchCaretakerDaySchedule(caretakerUserId, dhakaDate) {
  const query = dhakaDate ? `?date=${encodeURIComponent(dhakaDate)}` : '';
  return apiRequest(`/api/hires/caretaker/${caretakerUserId}/schedule${query}`);
}

export function acceptHireRequest(contractId) {
  return apiRequest(`/api/hires/${contractId}/accept`, {
    method: 'POST',
  });
}

export function declineHireRequest(contractId, reason) {
  return apiRequest(`/api/hires/${contractId}/decline`, {
    method: 'POST',
    body: { reason: reason || null },
  });
}

export function cancelHireContract(contractId, reason) {
  return apiRequest(`/api/hires/${contractId}/cancel`, {
    method: 'POST',
    body: { reason },
  });
}

export function endHireContractEarly(contractId, reason) {
  return apiRequest(`/api/hires/${contractId}/end-early`, {
    method: 'POST',
    body: { reason },
  });
}
