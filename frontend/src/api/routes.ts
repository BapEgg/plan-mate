import { bearerHeaders, request } from './client'

export type RouteCoordinate = {
  latitude: number
  longitude: number
}

export type RouteLegStatus = 'READY' | 'LOCATION_UNRESOLVED' | 'ROUTE_NOT_FOUND'

export type DayRouteLeg = {
  fromItemId: number
  toItemId: number
  sequence: number
  status: RouteLegStatus
  distanceMeters: number | null
  durationSeconds: number | null
  geometry: RouteCoordinate[]
  verifiedAt: string | null
}

export type DayRoute = {
  itineraryId: number
  itineraryVersion: number
  dayNumber: number
  provider: 'KAKAO'
  status: 'READY' | 'PARTIAL'
  legs: DayRouteLeg[]
}

export function getDayRoute(accessToken: string, tripId: string, dayNumber: number) {
  return request<DayRoute>(`/api/trips/${tripId}/routes/days/${dayNumber}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export type CandidateDetourEstimate = {
  fromItemId: number
  toItemId: number
  candidatePlaceId: string
  additionalMinutes: number
}
