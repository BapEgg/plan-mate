import { bearerHeaders, request } from './client'

/**
 * WP-D phase 1+2: schema/history/send + live broadcast + reconnect gap recovery. unread·삭제/답장/
 * 반응·typing/search/notification은 이후 phase에서 확장한다 — see
 * docs/api/collaboration-workspace-api.md §6/§7.
 */

export type ChatMessageType = 'USER_TEXT' | 'SYSTEM_NOTICE'

export type ChatMessage = {
  id: number
  tripId: string
  authorUserId: number | null
  type: ChatMessageType
  body: string
  clientMessageId: string
  sentAt: string
}

export type ChatHistoryPage = {
  messages: ChatMessage[]
  nextCursor: string | null
}

export type UnreadSummary = {
  tripId: string
  unreadCount: number
}

export function sendChatMessage(accessToken: string, tripId: string, message: { clientMessageId: string; body: string }) {
  return request<ChatMessage>(`/api/trips/${tripId}/chat/messages`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(message),
  })
}

export function listChatHistory(accessToken: string, tripId: string, cursor?: string) {
  const query = cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''
  return request<ChatHistoryPage>(`/api/trips/${tripId}/chat/messages${query}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

/** Reconnect gap recovery: everything strictly newer than `sinceId`, ascending. */
export function listChatSince(accessToken: string, tripId: string, sinceId: number) {
  return request<ChatHistoryPage>(`/api/trips/${tripId}/chat/messages?since=${sinceId}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

/** UNKNOWN-message resolution: did a send that raced a disconnect actually land? Throws ApiError(404) if not. */
export function getChatMessageByClientId(accessToken: string, tripId: string, clientMessageId: string) {
  return request<ChatMessage>(`/api/trips/${tripId}/chat/messages/by-client-id/${encodeURIComponent(clientMessageId)}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}
