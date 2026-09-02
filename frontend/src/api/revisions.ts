import { bearerHeaders, request } from './client'

export type ItineraryRevision = {
  itineraryId: number
  tripId: string
  generationId: number | null
  version: number
  baseItineraryId: number | null
  proposalId: number | null
  source: 'AI_GENERATION' | 'DIRECT' | 'VOTE' | 'AI_FULL_REGENERATION' | 'AI_PARTIAL_REGENERATION'
  revisedByUserId: number | null
  current: boolean
  createdAt: string
}

export type RegenerationScope =
  | { type: 'FULL' }
  | { type: 'PARTIAL'; dayNumber: number; fromItemId: number; toItemId: number }

export function listItineraryRevisions(accessToken: string, tripId: string) {
  return request<ItineraryRevision[]>(`/api/trips/${tripId}/itinerary-revisions`, {
    headers: bearerHeaders(accessToken),
  })
}
