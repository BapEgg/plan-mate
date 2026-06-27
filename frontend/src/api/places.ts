import { bearerHeaders, request } from './client'

export type PlaceAutocompleteRequest = {
  query: string
  languageCode?: string
}

export type AccommodationAutocompleteRequest = {
  query: string
  destinationPlaceId: string
  languageCode?: string
}

export type PlaceInDestinationAutocompleteRequest = {
  query: string
  destinationPlaceId: string
  languageCode?: string
}

export type PlaceAutocompleteItem = {
  placeId: string
  mainText: string
  secondaryText: string
  displayText: string
  types: string[]
  searchScope: 'CITY' | 'REGION' | 'ACCOMMODATION' | 'PLACE'
}

export type PlaceAutocompleteResponse = {
  items: PlaceAutocompleteItem[]
}

export function autocompletePlaces(accessToken: string, payload: PlaceAutocompleteRequest) {
  return request<PlaceAutocompleteResponse>('/api/places/autocomplete', {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}

export function autocompleteAccommodations(accessToken: string, payload: AccommodationAutocompleteRequest) {
  return request<PlaceAutocompleteResponse>('/api/places/accommodations/autocomplete', {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}

export function autocompletePlacesInDestination(accessToken: string, payload: PlaceInDestinationAutocompleteRequest) {
  return request<PlaceAutocompleteResponse>('/api/places/destination/autocomplete', {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}
