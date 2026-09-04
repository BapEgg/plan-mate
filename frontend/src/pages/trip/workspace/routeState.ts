import type { DayRoute } from '../../../api/routes'

/**
 * Keep a failed refresh's last verified route only when it belongs to the
 * exact itinerary revision and day that is still visible.
 */
export function retainMatchingVerifiedRoute(
  route: DayRoute | null,
  itineraryId: number,
  itineraryVersion: number,
  dayNumber: number,
): DayRoute | null {
  if (!route) return null
  if (route.itineraryId !== itineraryId) return null
  if (route.itineraryVersion !== itineraryVersion) return null
  if (route.dayNumber !== dayNumber) return null
  return route
}
