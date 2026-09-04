import { describe, expect, it } from 'vitest'
import type { DayRoute } from '../../../api/routes'
import { retainMatchingVerifiedRoute } from './routeState'

const route: DayRoute = {
  itineraryId: 904,
  itineraryVersion: 3,
  dayNumber: 1,
  provider: 'KAKAO',
  status: 'READY',
  legs: [],
}

describe('retainMatchingVerifiedRoute', () => {
  it('keeps the last verified route for the same itinerary revision and day', () => {
    expect(retainMatchingVerifiedRoute(route, 904, 3, 1)).toBe(route)
  })

  it('drops a route from another itinerary, revision, or day', () => {
    expect(retainMatchingVerifiedRoute(route, 905, 3, 1)).toBeNull()
    expect(retainMatchingVerifiedRoute(route, 904, 4, 1)).toBeNull()
    expect(retainMatchingVerifiedRoute(route, 904, 3, 2)).toBeNull()
  })
})
