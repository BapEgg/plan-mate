import { bearerHeaders, request } from './client'

export type TripStatus = 'PLANNING' | 'UPCOMING' | 'COMPLETED'

export type TripSummary = {
  id: string
  title: string
  destination: string
  destinationPlaceId: string | null
  startDate: string
  endDate: string
  status: TripStatus
  memberCount: number
  createdAt: string
}

export type TripMember = {
  userId: number
  nickname: string
  profileImageUrl: string | null
  role: 'OWNER' | 'MEMBER'
}

export type TripDetail = TripSummary & {
  members: TripMember[]
}

export type CreateTripRequest = {
  title: string
  destination: string
  destinationPlaceId: string
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

export function getTripDetail(accessToken: string, tripId: string) {
  return request<TripDetail>(`/api/trips/${tripId}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}
