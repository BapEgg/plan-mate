/**
 * Type-only stub for WP-E (Revision) / WP-F (Regeneration). No fetch calls
 * yet — see docs/api/collaboration-workspace-api.md §6/§7 and ADR-0002
 * (current itinerary pointer).
 */

export type ProposalStatus = 'PROPOSED' | 'BLOCKED' | 'NEEDS_REVIEW' | 'APPLIED' | 'REJECTED'

export type ItineraryProposal = {
  proposalId: string
  tripId: string
  baseItineraryId: number
  status: ProposalStatus
  createdByUserId: number
}

export type ItineraryRevision = {
  itineraryId: number
  tripId: string
  version: number
  createdAt: string
}

export type RegenerationScope =
  | { type: 'FULL' }
  | { type: 'PARTIAL'; dayNumber: number; fromItemId: number; toItemId: number }
