import { bearerHeaders, request, requestText } from './client'
import type { AiItineraryValidationReport } from './itineraryValidation'

export type TripStatus = 'PLANNING' | 'UPCOMING' | 'COMPLETED'
export type GenerationStatus =
  | 'CREATED'
  | 'COLLECTING_CANDIDATES'
  | 'READY_FOR_PLANNING'
  | 'COMPLETED'
  | 'FAILED'

export type CompanionType = 'SOLO' | 'COUPLE' | 'FRIENDS' | 'FAMILY' | 'PARENTS' | 'COWORKERS' | 'OTHER'
export type ChildAgeGroup = 'INFANT' | 'PRESCHOOL' | 'ELEMENTARY' | 'TEEN'
export type CurrencyCode = 'KRW' | 'JPY' | 'USD' | 'EUR'
export type BudgetLevel = 'VALUE' | 'BALANCED' | 'COMFORT'
export type BudgetItem = 'LODGING' | 'TRANSPORT' | 'FOOD' | 'FLIGHT'
export type TravelPace = 'RELAXED' | 'BALANCED' | 'PACKED'
export type InterestId =
  | 'FOOD'
  | 'SIGHTSEEING'
  | 'CAFE'
  | 'CULTURE'
  | 'NATURE'
  | 'SHOPPING'
  | 'PHOTO'
  | 'NIGHT_VIEW'
  | 'ACTIVITY'
  | 'REST'
  | 'ART'
  | 'THEME_PARK'
  | 'LOCAL'
export type TransportMode = 'WALK' | 'PUBLIC_TRANSIT' | 'RENTAL_CAR' | 'TAXI' | 'BIKE' | 'TOUR'
export type AccommodationMode = 'UNDECIDED' | 'PLACE_SEARCH'
export type AccommodationArea = 'TOURIST_CENTER' | 'TRANSIT' | 'QUIET' | 'ANYWHERE'
export type AvoidCondition =
  | 'EARLY_MORNING'
  | 'LATE_NIGHT'
  | 'LONG_WALK'
  | 'MANY_TRANSFERS'
  | 'CROWDED_PLACE'
  | 'SHOPPING'
  | 'MUSEUM'
  | 'EXPENSIVE_RESTAURANT'
  | 'TIGHT_SCHEDULE'

export type MustVisitPlace = {
  placeId: string
  name: string
  formattedAddress: string | null
  latitude: number | null
  longitude: number | null
  types: string[]
  primaryType: string | null
}

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
  destinationInfo: TripDestinationInfo
  planningProfile: TripPlanningProfile | null
  itineraries: Itinerary[]
}

export type CreateTripRequest = {
  title: string
  destinationPlaceId: string
  startDate: string
  endDate: string
  companion: {
    count: number
    type: CompanionType
    hasChildren: boolean
    childCount: number
    childAgeGroup: ChildAgeGroup | null
    hasSeniors: boolean
    seniorCount: number
  }
  budget: {
    currencyCode: CurrencyCode
    amount: number | null
    level: BudgetLevel
    includedItems: BudgetItem[]
  }
  preferences: {
    travelPace: TravelPace
    interests: InterestId[]
  }
  transportation: {
    primaryMode: TransportMode
    secondaryModes: TransportMode[]
  }
  accommodation: {
    mode: AccommodationMode
    preferredArea: AccommodationArea | null
    placeId: string | null
    checkInTime: string | null
    checkOutTime: string | null
  }
  schedulePreference: {
    dailyStartTime: string | null
    dailyEndTime: string | null
  }
  additionalRequest: {
    mustVisitPlaceIds: string[]
    avoidConditions: AvoidCondition[]
    freeRequest: string | null
  }
}

export type TripDestinationInfo = {
  placeId: string | null
  displayName: string
  formattedAddress: string | null
  latitude: number | null
  longitude: number | null
  viewportLowLatitude: number | null
  viewportLowLongitude: number | null
  viewportHighLatitude: number | null
  viewportHighLongitude: number | null
  types: string[]
  primaryType: string | null
}

export type TripPlanningProfile = {
  companionCount: number
  companionType: CompanionType
  hasChildren: boolean
  childCount: number
  childAgeGroup: ChildAgeGroup | null
  hasSeniors: boolean
  seniorCount: number
  currencyCode: CurrencyCode
  budgetAmount: number | null
  budgetLevel: BudgetLevel
  includedBudgetItems: BudgetItem[]
  travelPace: TravelPace
  interests: InterestId[]
  primaryTransportMode: TransportMode
  secondaryTransportModes: TransportMode[]
  accommodationMode: AccommodationMode
  accommodationArea: AccommodationArea | null
  accommodationName: string | null
  accommodationPlaceId: string | null
  accommodationFormattedAddress: string | null
  accommodationLatitude: number | null
  accommodationLongitude: number | null
  accommodationTypes: string[]
  accommodationPrimaryType: string | null
  checkInTime: string | null
  checkOutTime: string | null
  dailyStartTime: string
  dailyEndTime: string
  mustVisitPlaces: MustVisitPlace[]
  avoidConditions: AvoidCondition[]
  freeRequest: string | null
}

export type Itinerary = {
  id: number
  generationId: number
  createdAt: string
  version: number
  days: ItineraryDay[]
}

export type ItineraryDay = {
  id: number
  day: number
  date: string
  items: ItineraryItem[]
}

export type ItineraryItem = {
  id: number
  sequence: number
  placeId: string
  startTime: string
  durationMinutes: number
  createdSource: 'AI_DRAFT' | 'USER_SELECTED' | 'MANUAL_EDIT'
}

export type ItineraryGenerationCreateResponse = {
  generationId: string
  status: GenerationStatus
  candidateCount: number
}

export type ItineraryGenerationDetailResponse = ItineraryGenerationCreateResponse & {
  tripId: string
  promptVersion: string
  failureReason: string | null
  createdAt: string
  updatedAt: string
}

export type AiItineraryRequest = Record<string, unknown>
export type AiItineraryDraft = {
  generationId: string
  days: Array<{
    day: number
    items: Array<{
      sequence: number
      placeId: string
      startTime: string
      durationMinutes: number
    }>
  }>
}

export type ItineraryPlaceView = {
  itineraryId: number
  itemId: number
  dayNo: number
  sequence: number
  placeId: string
  startTime: string
  durationMinutes: number
  createdSource: 'AI_DRAFT' | 'USER_SELECTED' | 'MANUAL_EDIT'
  display: {
    resolved: boolean
    displayName: string | null
    location: {
      latitude: number
      longitude: number
    } | null
    googleMapsUri: string | null
    fallbackMessage: string | null
  }
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

export function deleteTrip(accessToken: string, tripId: string) {
  return request<void>(`/api/trips/${tripId}`, {
    method: 'DELETE',
    headers: bearerHeaders(accessToken),
  })
}

export function getTripDetail(accessToken: string, tripId: string) {
  return request<TripDetail>(`/api/trips/${tripId}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function getItineraryPlaceViews(accessToken: string, tripId: string, dayNo?: number) {
  const search = dayNo == null ? '' : `?dayNo=${dayNo}`
  return request<ItineraryPlaceView[]>(`/api/trips/${tripId}/itinerary-place-views${search}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function createItineraryGeneration(accessToken: string, tripId: string) {
  return request<ItineraryGenerationCreateResponse>(`/api/trips/${tripId}/itinerary-generations`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}

export function getItineraryGeneration(accessToken: string, tripId: string, generationId: string) {
  return request<ItineraryGenerationDetailResponse>(`/api/trips/${tripId}/itinerary-generations/${generationId}`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export async function getLatestItineraryGeneration(accessToken: string, tripId: string) {
  const response = await request<ItineraryGenerationDetailResponse | undefined>(`/api/trips/${tripId}/itinerary-generations/latest`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
  return response ?? null
}

export function getManualPrompt(accessToken: string, tripId: string, generationId: string) {
  return requestText(`/api/trips/${tripId}/itinerary-generations/${generationId}/manual-prompt`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function getAiRequest(accessToken: string, tripId: string, generationId: string) {
  return request<AiItineraryRequest>(`/api/trips/${tripId}/itinerary-generations/${generationId}/ai-request`, {
    method: 'GET',
    headers: bearerHeaders(accessToken),
  })
}

export function submitManualResponse(
  accessToken: string,
  tripId: string,
  generationId: string,
  payload: AiItineraryDraft,
) {
  return request<ItineraryGenerationDetailResponse>(`/api/trips/${tripId}/itinerary-generations/${generationId}/manual-response`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}

export function validateManualResponse(
  accessToken: string,
  tripId: string,
  generationId: string,
  payload: AiItineraryDraft,
) {
  return request<AiItineraryValidationReport>(`/api/trips/${tripId}/itinerary-generations/${generationId}/manual-response/validate`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}
