import { bearerHeaders, request } from './client'

export type RegenerationStatus =
  | 'GENERATING'
  | 'READY_FOR_REVIEW'
  | 'APPLIED'
  | 'REJECTED'
  | 'FAILED'
  | 'STALE'

export type RegenerationScope =
  | { type: 'FULL' }
  | {
      type: 'PARTIAL'
      dayNumber: number
      startItemId: number
      endItemId: number
      fixedItemIds: number[]
    }

export type CreateRegenerationRequest = {
  baseItineraryId: number
  expectedItineraryVersion: number
  scope: RegenerationScope
  additionalRequest: string | null
}

export type RegenerationItemComparison = {
  sequence: number
  originalItemId: number | null
  originalPlaceId: string | null
  originalDisplayName: string | null
  originalStartTime: string | null
  originalDurationMinutes: number | null
  proposedPlaceId: string
  proposedDisplayName: string | null
  proposedStartTime: string
  proposedDurationMinutes: number
  fixed: boolean
  changed: boolean
}

export type ItineraryRegeneration = {
  regenerationId: number
  tripId: string
  generationId: string
  baseItineraryId: number
  baseItineraryVersion: number
  scope: 'FULL' | 'PARTIAL'
  dayNumber: number | null
  startItemId: number | null
  endItemId: number | null
  fixedItemIds: number[]
  status: RegenerationStatus
  failureReason: string | null
  appliedItineraryId: number | null
  days: Array<{
    day: number
    date: string
    items: RegenerationItemComparison[]
  }>
  createdAt: string
  updatedAt: string
}

export function createItineraryRegeneration(
  accessToken: string,
  tripId: string,
  payload: CreateRegenerationRequest,
) {
  return request<ItineraryRegeneration>(`/api/trips/${tripId}/itinerary-regenerations`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}

export async function getLatestItineraryRegeneration(accessToken: string, tripId: string) {
  const response = await request<ItineraryRegeneration | undefined>(
    `/api/trips/${tripId}/itinerary-regenerations/latest`,
    { headers: bearerHeaders(accessToken) },
  )
  return response ?? null
}

export function getItineraryRegeneration(accessToken: string, tripId: string, regenerationId: number) {
  return request<ItineraryRegeneration>(
    `/api/trips/${tripId}/itinerary-regenerations/${regenerationId}`,
    { headers: bearerHeaders(accessToken) },
  )
}

export function applyItineraryRegeneration(accessToken: string, tripId: string, regenerationId: number) {
  return request<ItineraryRegeneration>(
    `/api/trips/${tripId}/itinerary-regenerations/${regenerationId}/apply`,
    { method: 'POST', headers: bearerHeaders(accessToken) },
  )
}

export function rejectItineraryRegeneration(accessToken: string, tripId: string, regenerationId: number) {
  return request<ItineraryRegeneration>(
    `/api/trips/${tripId}/itinerary-regenerations/${regenerationId}/reject`,
    { method: 'POST', headers: bearerHeaders(accessToken) },
  )
}
