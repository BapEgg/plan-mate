import { bearerHeaders, request } from './client'

/**
 * WP-D phase 1-5: core chat + delete/reply/reaction + typing/presence/mention/search.
 * browser notification은 이후 phase에서 확장한다 — see
 * docs/api/collaboration-workspace-api.md §6/§7.
 */

export type ChatMessageType = 'USER_TEXT' | 'SYSTEM_NOTICE'
export type ChatReactionType = 'LIKE' | 'ACKNOWLEDGED'

export type ChatMention = {
  memberId: number
  displayNameSnapshot: string
  startCodePoint: number
  endCodePoint: number
}

export type ChatMentionDraft = Pick<ChatMention, 'memberId' | 'startCodePoint' | 'endCodePoint'>

export type ChatReplyPreview = {
  messageId: number
  authorUserId: number | null
  body: string
  deleted: boolean
}

export type ChatReactionSummary = {
  reaction: ChatReactionType
  count: number
  memberNames: string[]
  reactedByMe: boolean
}

export type ChatMessage = {
  id: number
  tripId: string
  authorUserId: number | null
  type: ChatMessageType
  body: string
  clientMessageId: string
  sentAt: string
  replyTo: ChatReplyPreview | null
  deleted: boolean
  deletedAt: string | null
  deletableUntil: string
  reactions: ChatReactionSummary[]
  mentions: ChatMention[]
}

export type ChatHistoryPage = {
  messages: ChatMessage[]
  nextCursor: string | null
}

export type ChatUnreadCount = {
  unreadCount: number
}

export function sendChatMessage(accessToken: string, tripId: string, message: { clientMessageId: string; body: string; replyToMessageId?: number; mentions?: ChatMentionDraft[] }) {
  return request<ChatMessage>(`/api/trips/${tripId}/chat/messages`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(message),
  })
}

export type ChatSearchMatchRange = {
  startCodePoint: number
  endCodePoint: number
}

export type ChatSearchResult = {
  messageId: number
  sequence: number
  senderSnapshot: string
  createdAtUtc: string
  snippet: string
  matchedRanges: ChatSearchMatchRange[]
}

export type ChatSearchResponse = {
  query: string
  results: ChatSearchResult[]
  nextCursor: string | null
  hasMore: boolean
  searchSnapshotSequence: number
}

export function searchChatMessages(accessToken: string, tripId: string, query: string, cursor?: string) {
  const params = new URLSearchParams({ q: query })
  if (cursor) params.set('cursor', cursor)
  return request<ChatSearchResponse>(`/api/trips/${tripId}/chat/messages/search?${params}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function getChatMessageContext(accessToken: string, tripId: string, messageId: number) {
  return request<ChatHistoryPage>(`/api/trips/${tripId}/chat/messages/${messageId}/context`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function getChatMessage(accessToken: string, tripId: string, messageId: number) {
  return request<ChatMessage>(`/api/trips/${tripId}/chat/messages/${messageId}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function deleteChatMessage(accessToken: string, tripId: string, messageId: number) {
  return request<ChatMessage>(`/api/trips/${tripId}/chat/messages/${messageId}`, {
    method: 'DELETE',
    headers: bearerHeaders(accessToken),
  })
}

export function setChatReaction(accessToken: string, tripId: string, messageId: number, reaction: ChatReactionType) {
  return request<ChatMessage>(`/api/trips/${tripId}/chat/messages/${messageId}/reaction`, {
    method: 'PUT',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify({ reaction }),
  })
}

export function removeChatReaction(accessToken: string, tripId: string, messageId: number) {
  return request<ChatMessage>(`/api/trips/${tripId}/chat/messages/${messageId}/reaction`, {
    method: 'DELETE',
    headers: bearerHeaders(accessToken),
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

export function getChatUnreadCount(accessToken: string, tripId: string) {
  return request<ChatUnreadCount>(`/api/trips/${tripId}/chat/unread-count`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function markChatRead(accessToken: string, tripId: string, messageId: number) {
  return request<void>(`/api/trips/${tripId}/chat/read`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify({ messageId }),
  })
}
