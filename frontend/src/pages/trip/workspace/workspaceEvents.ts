import type { TripRealtimeEvent } from '../../../api/realtime'
import {
  CHAT_MESSAGE_DELETED,
  CHAT_MESSAGE_SENT,
  CHAT_REACTION_CHANGED,
  CHAT_TYPING_UPDATED,
  MEMBER_PRESENCE_CHANGED,
  ITINERARY_GENERATION_STATUS_CHANGED,
  MEMBERSHIP_CHANGED,
} from '../../../api/realtime'
import type { GenerationStatus } from '../../../api/trips'

/**
 * The trip topic can carry event types this build doesn't know about yet
 * (spec §4.4: "client는 알 수 없는 schema/type을 무시하고 전체 화면을 실패시키지 않는다").
 * Only handle envelopes whose schema version and type this workspace
 * actually understands; everything else is silently ignored rather than
 * crashing the page.
 */
const KNOWN_SCHEMA_VERSION = 1
const KNOWN_EVENT_TYPES = new Set([
  ITINERARY_GENERATION_STATUS_CHANGED,
  MEMBERSHIP_CHANGED,
  CHAT_MESSAGE_SENT,
  CHAT_MESSAGE_DELETED,
  CHAT_REACTION_CHANGED,
  CHAT_TYPING_UPDATED,
  MEMBER_PRESENCE_CHANGED,
])

export function isKnownWorkspaceEvent(event: TripRealtimeEvent): boolean {
  return event.schemaVersion === KNOWN_SCHEMA_VERSION && KNOWN_EVENT_TYPES.has(event.type)
}

export function generationEventMessage(status: GenerationStatus) {
  return {
    CREATED: '일정 생성 요청이 접수되었습니다.',
    COLLECTING_CANDIDATES: '여행 조건에 맞는 장소 후보를 수집하고 있습니다.',
    READY_FOR_PLANNING: '장소 후보 수집을 마쳤습니다.',
    COMPLETED: '검증된 일정이 저장되었습니다.',
    FAILED: '일정 생성에 실패했습니다.',
  }[status]
}
