/**
 * Type-only stub for WP-C (Map + Route). No fetch calls yet — see
 * docs/api/collaboration-workspace-api.md §6/§7 and ADR gate "route provider
 * ADR" (spec §15): no route number/polyline until the WP-C provider spike
 * lands.
 */

export type RouteMode = 'WALK' | 'PUBLIC_TRANSIT' | 'RENTAL_CAR' | 'TAXI'

export type RouteSnapshot = {
  itineraryVersion: number
  dayNumber: number
  mode: RouteMode
  provider: string
  geometry: string
  durationMinutes: number
  distanceMeters: number
  verifiedAt: string
}

export type CandidateDetourEstimate = {
  fromItemId: number
  toItemId: number
  candidatePlaceId: string
  additionalMinutes: number
}
