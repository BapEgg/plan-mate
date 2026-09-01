import { bearerHeaders, request } from './client'

export type OwnerTransferRequestStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED'

export type OwnerTransferRequest = {
  id: number
  tripId: string
  fromUserId: number
  toUserId: number
  status: OwnerTransferRequestStatus
  createdAt: string
  expiresAt: string
}

export function updateTripTitle(accessToken: string, tripId: string, title: string) {
  return request<void>(`/api/trips/${tripId}/title`, {
    method: 'PATCH',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify({ title }),
  })
}

export function removeTripMember(accessToken: string, tripId: string, userId: number) {
  return request<void>(`/api/trips/${tripId}/members/${userId}`, {
    method: 'DELETE',
    headers: bearerHeaders(accessToken),
  })
}

export function leaveTrip(accessToken: string, tripId: string) {
  return request<void>(`/api/trips/${tripId}/leave`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}

export function listMyOwnerTransferRequests(accessToken: string) {
  return request<OwnerTransferRequest[]>('/api/owner-transfer-requests', {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function createOwnerTransferRequest(accessToken: string, tripId: string, targetUserId: number) {
  return request<OwnerTransferRequest>(`/api/trips/${tripId}/owner-transfer-requests`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify({ targetUserId }),
  })
}

export function acceptOwnerTransferRequest(accessToken: string, requestId: number) {
  return request<void>(`/api/owner-transfer-requests/${requestId}/accept`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}

export function declineOwnerTransferRequest(accessToken: string, requestId: number) {
  return request<void>(`/api/owner-transfer-requests/${requestId}/decline`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}

export function cancelOwnerTransferRequest(accessToken: string, requestId: number) {
  return request<void>(`/api/owner-transfer-requests/${requestId}/cancel`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}
