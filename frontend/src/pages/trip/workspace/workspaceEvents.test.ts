import { describe, expect, it } from 'vitest'
import { generationEventMessage, isKnownWorkspaceEvent } from './workspaceEvents'
import { CHAT_MESSAGE_SENT, ITINERARY_GENERATION_STATUS_CHANGED, MEMBERSHIP_CHANGED } from '../../../api/realtime'
import type { TripRealtimeEvent } from '../../../api/realtime'

type GenerationChangedEvent = Extract<TripRealtimeEvent, { type: typeof ITINERARY_GENERATION_STATUS_CHANGED }>

function envelope(overrides: Partial<Omit<GenerationChangedEvent, 'type'>> = {}): GenerationChangedEvent {
  return {
    eventId: 'event-1',
    schemaVersion: 1,
    type: ITINERARY_GENERATION_STATUS_CHANGED,
    tripId: '1530',
    occurredAt: '2026-08-31T00:00:00Z',
    payload: {
      generationId: '1415',
      previousStatus: 'COLLECTING_CANDIDATES',
      status: 'COMPLETED',
      candidateCount: 10,
      failureReason: null,
      updatedAt: '2026-08-31T00:00:00Z',
    },
    ...overrides,
  }
}

describe('isKnownWorkspaceEvent', () => {
  it('accepts the known event type at the known schema version', () => {
    expect(isKnownWorkspaceEvent(envelope())).toBe(true)
  })

  it('ignores an unknown event type instead of throwing', () => {
    // The server can send a type this build's TS types don't know about yet —
    // `handleMessage` blind-casts parsed JSON, so simulate that here too.
    const unknownTypeEvent = { ...envelope(), type: 'SOME_FUTURE_EVENT' } as unknown as TripRealtimeEvent
    expect(isKnownWorkspaceEvent(unknownTypeEvent)).toBe(false)
  })

  it('ignores an unrecognized schema version even for a known type', () => {
    expect(isKnownWorkspaceEvent(envelope({ schemaVersion: 2 }))).toBe(false)
  })

  it('also accepts MEMBERSHIP_CHANGED, the second known event type', () => {
    const membershipEvent: TripRealtimeEvent = {
      eventId: 'event-2',
      schemaVersion: 1,
      type: MEMBERSHIP_CHANGED,
      tripId: '1530',
      occurredAt: '2026-08-31T00:00:00Z',
      payload: { affectedUserId: 2623, changeType: 'REMOVED' },
    }
    expect(isKnownWorkspaceEvent(membershipEvent)).toBe(true)
  })

  it('also accepts CHAT_MESSAGE_SENT, the third known event type', () => {
    const chatEvent: TripRealtimeEvent = {
      eventId: 'event-3',
      schemaVersion: 1,
      type: CHAT_MESSAGE_SENT,
      tripId: '1530',
      occurredAt: '2026-08-31T00:00:00Z',
      payload: {
        messageId: 1,
        clientMessageId: 'client-1',
        authorUserId: 2623,
        type: 'USER_TEXT',
        body: '안녕하세요',
        sentAt: '2026-08-31T00:00:00Z',
        replyToMessageId: null,
        replyAuthorUserId: null,
        replyBody: null,
        replyDeleted: false,
      },
    }
    expect(isKnownWorkspaceEvent(chatEvent)).toBe(true)
  })
})

describe('generationEventMessage', () => {
  it('has a message for every generation status', () => {
    const statuses = ['CREATED', 'COLLECTING_CANDIDATES', 'READY_FOR_PLANNING', 'COMPLETED', 'FAILED'] as const
    for (const status of statuses) {
      expect(generationEventMessage(status)).toBeTruthy()
    }
  })
})
