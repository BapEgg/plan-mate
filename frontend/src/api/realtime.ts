import { Client } from '@stomp/stompjs'
import type { IMessage, StompSubscription } from '@stomp/stompjs'
import { API_BASE_URL } from './client'
import type { GenerationStatus } from './trips'

export type RealtimeEventEnvelope<TType extends string, TPayload> = {
  eventId: string
  schemaVersion: number
  type: TType
  tripId: string
  occurredAt: string
  payload: TPayload
}

export type ItineraryGenerationStatusChangedPayload = {
  generationId: string
  previousStatus: GenerationStatus
  status: GenerationStatus
  candidateCount: number
  failureReason: string | null
  updatedAt: string
}

export type MembershipChangeType = 'REMOVED' | 'LEFT' | 'JOINED' | 'OWNER_TRANSFERRED' | 'TITLE_UPDATED'

export type MembershipChangedPayload = {
  affectedUserId: number | null
  changeType: MembershipChangeType
}

export type ChatMessageSentPayload = {
  messageId: number
  clientMessageId: string
  authorUserId: number | null
  type: 'USER_TEXT' | 'SYSTEM_NOTICE'
  body: string
  sentAt: string
}

export const ITINERARY_GENERATION_STATUS_CHANGED = 'ITINERARY_GENERATION_STATUS_CHANGED'
export const MEMBERSHIP_CHANGED = 'MEMBERSHIP_CHANGED'
export const CHAT_MESSAGE_SENT = 'CHAT_MESSAGE_SENT'

// `type` is a literal discriminant per variant so `event.type === X` narrows `event.payload`.
export type TripRealtimeEvent =
  | RealtimeEventEnvelope<typeof ITINERARY_GENERATION_STATUS_CHANGED, ItineraryGenerationStatusChangedPayload>
  | RealtimeEventEnvelope<typeof MEMBERSHIP_CHANGED, MembershipChangedPayload>
  | RealtimeEventEnvelope<typeof CHAT_MESSAGE_SENT, ChatMessageSentPayload>

type TripRealtimeConnectionOptions = {
  accessToken: string
  tripId: string
  onConnect?: () => void
  /** An involuntary drop (network, server restart) — never fired for this connection's own `disconnect()`. */
  onDisconnected?: () => void
  onError?: (message: string) => void
  onEvent: (event: TripRealtimeEvent) => void
}

export type TripRealtimeConnection = {
  disconnect: () => void
}

export function connectTripRealtimeEvents({
  accessToken,
  tripId,
  onConnect,
  onDisconnected,
  onError,
  onEvent,
}: TripRealtimeConnectionOptions): TripRealtimeConnection {
  let subscription: StompSubscription | null = null
  let deliberatelyClosed = false
  const client = new Client({
    brokerURL: websocketUrl('/ws/events'),
    connectHeaders: {
      Authorization: `Bearer ${accessToken}`,
    },
    reconnectDelay: 3000,
    onConnect: () => {
      subscription?.unsubscribe()
      subscription = client.subscribe(`/topic/trips/${tripId}/events`, (message) => {
        handleMessage(message, onEvent, onError)
      })
      onConnect?.()
    },
    onWebSocketClose: () => {
      if (!deliberatelyClosed) {
        onDisconnected?.()
      }
    },
    onStompError: (frame) => {
      onError?.(frame.headers.message ?? frame.body ?? 'Realtime connection failed.')
    },
    onWebSocketError: () => {
      onError?.('Realtime connection failed.')
    },
  })

  client.activate()

  return {
    disconnect: () => {
      deliberatelyClosed = true
      subscription?.unsubscribe()
      subscription = null
      void client.deactivate()
    },
  }
}

function handleMessage(
  message: IMessage,
  onEvent: (event: TripRealtimeEvent) => void,
  onError?: (message: string) => void,
) {
  try {
    onEvent(JSON.parse(message.body) as TripRealtimeEvent)
  } catch {
    onError?.('Realtime event payload could not be parsed.')
  }
}

function websocketUrl(path: string) {
  const baseUrl = API_BASE_URL || window.location.origin
  const url = new URL(path, baseUrl)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}
