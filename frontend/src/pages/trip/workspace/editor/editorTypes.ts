/**
 * Shared types for the OWNER itinerary editor (spec §5.2 "선택 구간 다시 만들기 |
 * 전체 일정 다시 만들기"). WP-A only reserves this slot and its type contract so
 * WP-F can implement the editor UI against a stable shape — no editor UI or
 * entry point ships in WP-A (the product must not imply an unfinished
 * feature is available).
 */
export type EditorDraftSession = {
  draftId: string
  tripId: string
  /** ADR-0002 current pointer this draft was started from; a stale base forces a restart. */
  baseItineraryId: number
  createdAt: string
}

export type EditorScope =
  | { type: 'FULL' }
  | { type: 'PARTIAL'; dayNumber: number; fromItemId: number; toItemId: number }
