/**
 * Type-only stub for WP-D (Chat + Presence). No fetch calls yet — see
 * docs/api/collaboration-workspace-api.md §6/§7.
 */

export type ChatMessageType = 'USER_TEXT' | 'SYSTEM_NOTICE'

export type ChatMessage = {
  messageId: string
  clientMessageId: string
  tripId: string
  authorUserId: number | null
  type: ChatMessageType
  body: string
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
