import { bearerHeaders, request } from './client'

/**
 * WP-D phase 1: schema/history/send + live broadcast. reconnect gap 복구·unread는 이후
 * phase에서 확장한다 — see docs/api/collaboration-workspace-api.md §6/§7.
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
