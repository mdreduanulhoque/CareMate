import { apiRequest } from './client';

export function fetchPendingReviews() {
  return apiRequest('/api/reviews/pending');
}

export function fetchAuthoredReviews() {
  return apiRequest('/api/reviews/authored');
}

export function fetchContractReviews(contractId) {
  return apiRequest(`/api/reviews/contract/${contractId}`);
}

export function submitReview(reviewId, payload) {
  return apiRequest(`/api/reviews/${reviewId}/submit`, {
    method: 'POST',
    body: payload,
  });
}

export function editReview(reviewId, payload) {
  return apiRequest(`/api/reviews/${reviewId}`, {
    method: 'PUT',
    body: payload,
  });
}

export function fetchCaretakerPublicReviews(caretakerUserId) {
  return apiRequest(`/api/reviews/caretaker/${caretakerUserId}`);
}

export function fetchCustomerReviews(customerUserId) {
  return apiRequest(`/api/reviews/customer/${customerUserId}`);
}
