import { bearerHeaders, request } from './client'

export type TripStatus = 'PLANNING' | 'UPCOMING' | 'COMPLETED'

export type TripSummary = {
  id: string
  title: string
  destination: string
  startDate: string
  endDate: string
  status: TripStatus
  memberCount: number
  createdAt: string
  source?: 'api' | 'local'
}

export type CreateTripRequest = {
  title: string
  destination: string
  startDate: string
  endDate: string
}

export function listMyTrips(accessToken: string) {
  return request<TripSummary[]>('/api/trips', {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function createTrip(accessToken: string, payload: CreateTripRequest) {
  return request<TripSummary>('/api/trips', {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}
