import { bearerHeaders, request } from './client'

export type FriendRequestStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'CANCELLED'

export type FriendRequest = {
  id: number
  requesterUserId: number
  addresseeUserId: number
  status: FriendRequestStatus
  createdAt: string
}

export type Friend = {
  userId: number
  nickname: string
  profileImageUrl: string | null
}

export function sendFriendRequest(
  accessToken: string,
  addressee: { addresseeUserId: number } | { addresseeEmail: string },
) {
  return request<FriendRequest>('/api/friend-requests', {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(addressee),
  })
}

export function listFriendRequests(accessToken: string, direction: 'incoming' | 'outgoing' = 'incoming') {
  return request<FriendRequest[]>(`/api/friend-requests?direction=${direction}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function listFriends(accessToken: string) {
  return request<Friend[]>('/api/friends', {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function acceptFriendRequest(accessToken: string, requestId: number) {
  return request<void>(`/api/friend-requests/${requestId}/accept`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}

export function declineFriendRequest(accessToken: string, requestId: number) {
  return request<void>(`/api/friend-requests/${requestId}/decline`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}

export function cancelFriendRequest(accessToken: string, requestId: number) {
  return request<void>(`/api/friend-requests/${requestId}/cancel`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}
