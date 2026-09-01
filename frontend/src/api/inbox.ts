import { bearerHeaders, request } from './client'

export type InboxSummary = {
  tripInvitationCount: number
  friendRequestCount: number
  ownerTransferRequestCount: number
}

export function getInboxSummary(accessToken: string) {
  return request<InboxSummary>('/api/inbox/summary', {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}
