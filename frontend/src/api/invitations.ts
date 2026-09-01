import { bearerHeaders, request } from './client'

export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED' | 'EXPIRED'

export type TripInvitation = {
  id: number
  tripId: string
  inviteeUserId: number
  invitedByUserId: number
  status: InvitationStatus
  createdAt: string
  expiresAt: string
}

export function sendTripInvitation(
  accessToken: string,
  tripId: string,
  invitee: { inviteeUserId: number } | { inviteeEmail: string },
) {
  return request<TripInvitation>(`/api/trips/${tripId}/invitations`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(invitee),
  })
}

export function listMyTripInvitations(accessToken: string) {
  return request<TripInvitation[]>('/api/invitations', {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function acceptTripInvitation(accessToken: string, invitationId: number) {
  return request<void>(`/api/invitations/${invitationId}/accept`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}

export function declineTripInvitation(accessToken: string, invitationId: number) {
  return request<void>(`/api/invitations/${invitationId}/decline`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}

export function cancelTripInvitation(accessToken: string, invitationId: number) {
  return request<void>(`/api/invitations/${invitationId}/cancel`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}
