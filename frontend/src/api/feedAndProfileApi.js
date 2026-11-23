import { apiRequest, apiUpload } from './client';

export function fetchMyCaretakerProfile() {
  return apiRequest('/api/caretaker/profile/me');
}

export function updateMyCaretakerProfile(payload) {
  return apiRequest('/api/caretaker/profile/me', {
    method: 'PUT',
    body: payload,
  });
}

export function updateMyAvailability(available) {
  return apiRequest('/api/caretaker/profile/availability', {
    method: 'PATCH',
    body: { available },
  });
}

export function uploadCaretakerAvatar(file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload('/api/caretaker/profile/avatar', formData);
}

export function fetchHuntersFeed(filters = {}) {
  const params = new URLSearchParams();
  params.set('availableOnly', String(filters.availableOnly ?? true));

  if (filters.rateType) params.set('rateType', filters.rateType);
  if (filters.maxBudget) params.set('maxBudget', String(filters.maxBudget));
  if (filters.area?.trim()) params.set('area', filters.area.trim());
  if (filters.minRating) params.set('minRating', String(filters.minRating));
  if (filters.keyword?.trim()) params.set('keyword', filters.keyword.trim());
  if (filters.sort) params.set('sort', filters.sort);
  params.set('page', String(filters.page ?? 0));
  params.set('size', String(filters.size ?? 12));

  return apiRequest(`/api/feed/caretakers?${params.toString()}`);
}

export function fetchCaretakerPublicDetail(caretakerUserId) {
  return apiRequest(`/api/feed/caretakers/${caretakerUserId}`);
}
