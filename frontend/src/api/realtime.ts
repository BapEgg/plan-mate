import { Client } from '@stomp/stompjs'
import type { IMessage, StompSubscription } from '@stomp/stompjs'
import { API_BASE_URL } from './client'
import type { GenerationStatus } from './trips'

export type RealtimeEventEnvelope<TPayload> = {
  eventId: string
  schemaVersion: number
  type: string
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

export type TripRealtimeEvent =
  RealtimeEventEnvelope<ItineraryGenerationStatusChangedPayload>

export const ITINERARY_GENERATION_STATUS_CHANGED = 'ITINERARY_GENERATION_STATUS_CHANGED'

type TripRealtimeConnectionOptions = {
  accessToken: string
  tripId: string
  onConnect?: () => void
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
  onError,
  onEvent,
}: TripRealtimeConnectionOptions): TripRealtimeConnection {
  let subscription: StompSubscription | null = null
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
