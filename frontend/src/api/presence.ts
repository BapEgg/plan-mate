import { bearerHeaders, request } from './client'

export type PresenceStatus = 'ONLINE' | 'OFFLINE'

export type TripPresenceSnapshot = {
  tripId: number
  members: Array<{ memberId: number; status: PresenceStatus }>
  snapshotVersion: number
}

export function getTripPresence(accessToken: string, tripId: string) {
  return request<TripPresenceSnapshot>(`/api/trips/${tripId}/presence`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}
