import { apiRequest } from './client';

export function unlockCaretakerContact(caretakerUserId) {
  return apiRequest(`/api/contacts/unlock/${caretakerUserId}`, {
    method: 'POST',
  });
}

export function fetchUnlockedCaretakerContact(caretakerUserId) {
  return apiRequest(`/api/contacts/unlock/${caretakerUserId}`);
}

export function fetchNegotiationRooms() {
  return apiRequest('/api/chats/rooms');
}

export function fetchNegotiationRoomDetail(roomId, limit = 100) {
  return apiRequest(`/api/chats/negotiation/${encodeURIComponent(roomId)}?limit=${limit}`);
}

export function sendNegotiationMessageRest(roomId, content) {
  return apiRequest(`/api/chats/negotiation/${encodeURIComponent(roomId)}/messages`, {
    method: 'POST',
    body: { content },
  });
}
