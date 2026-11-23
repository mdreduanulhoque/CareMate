import { apiRequest } from './client';

export function fetchMyApplications() {
  return apiRequest('/api/caretaker/applications/me');
}

export function submitCaretakerApplication(payload) {
  return apiRequest('/api/caretaker/applications', {
    method: 'POST',
    body: payload,
  });
}

export function fetchMyCertifications() {
  return apiRequest('/api/caretaker/certifications/me');
}

export function submitNewCertification(payload) {
  return apiRequest('/api/caretaker/certifications', {
    method: 'POST',
    body: payload,
  });
}

export function fetchAdminApplications(status) {
  const query = status && status !== 'ALL' ? `?status=${encodeURIComponent(status)}` : '';
  return apiRequest(`/api/admin/applications${query}`);
}

export function fetchApplicantHistory(userId) {
  return apiRequest(`/api/admin/applications/user/${userId}/history`);
}

export function approveApplication(applicationId) {
  return apiRequest(`/api/admin/applications/${applicationId}/approve`, {
    method: 'POST',
  });
}

export function rejectApplication(applicationId, rejectionReason) {
  return apiRequest(`/api/admin/applications/${applicationId}/reject`, {
    method: 'POST',
    body: { rejectionReason },
  });
}

export function recheckApplicationDrive(applicationId) {
  return apiRequest(`/api/admin/applications/${applicationId}/recheck-drive`, {
    method: 'POST',
  });
}

export function fetchAdminCertifications(status) {
  const query = status && status !== 'ALL' ? `?status=${encodeURIComponent(status)}` : '';
  return apiRequest(`/api/admin/certifications${query}`);
}

export function approveCertification(certificationId) {
  return apiRequest(`/api/admin/certifications/${certificationId}/approve`, {
    method: 'POST',
  });
}

export function rejectCertification(certificationId, rejectionReason) {
  return apiRequest(`/api/admin/certifications/${certificationId}/reject`, {
    method: 'POST',
    body: { rejectionReason },
  });
}

export function recheckCertificationDrive(certificationId) {
  return apiRequest(`/api/admin/certifications/${certificationId}/recheck-drive`, {
    method: 'POST',
  });
}
