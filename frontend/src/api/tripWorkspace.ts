/**
 * Thin re-export of the existing `trips.ts` calls the workspace uses, so
 * WP-B..F can depend on a workspace-scoped module without reaching into the
 * general trips API (docs/api/collaboration-workspace-api.md §7).
 */
export {
  getTripDetail,
  getItineraryPlaceViews,
  getLatestItineraryGeneration,
} from './trips'
export type {
  TripDetail,
  TripMember,
  Itinerary,
  ItineraryDay,
  ItineraryItem,
  ItineraryPlaceView,
  ItineraryGenerationDetailResponse,
} from './trips'
