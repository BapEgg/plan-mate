import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { DayRoute } from '../../../../api/routes'
import { TripMapCanvas } from './TripMapCanvas'

vi.mock('./TripMapView', () => ({
  TripMapView: ({ routePoints }: { routePoints: unknown[] }) => (
    <div data-testid="map-route-points">{routePoints.length}</div>
  ),
}))

vi.mock('./PlacePanel', () => ({
  PlacePanel: () => null,
}))

const route: DayRoute = {
  itineraryId: 904,
  itineraryVersion: 3,
  dayNumber: 1,
  provider: 'KAKAO',
  status: 'READY',
  legs: [{
    fromItemId: 1,
    toItemId: 2,
    sequence: 1,
    status: 'READY',
    distanceMeters: 3200,
    durationSeconds: 780,
    geometry: [
      { latitude: 34.88, longitude: 128.62 },
      { latitude: 34.89, longitude: 128.63 },
    ],
    verifiedAt: '2026-09-02T08:00:00Z',
  }],
}

describe('TripMapCanvas route failure state', () => {
  it('keeps verified geometry visible and explains that it is the last confirmed route', () => {
    render(
      <TripMapCanvas
        activeDay={1}
        activeDate="2026-10-10"
        destination="거제도"
        destinationCenter={{ lat: 34.88, lng: 128.62 }}
        places={[
          { id: '1', day: 1, order: 1, title: '매미성', startTime: '10:00', duration: '2시간', durationMinutes: 120, latitude: 34.88, longitude: 128.62, locationLabel: null, googleMapsUri: null, placeId: 'place-1', resolved: true, displaySource: 'PROVIDER', source: 'AI_DRAFT' },
          { id: '2', day: 1, order: 2, title: '바람의 언덕', startTime: '13:00', duration: '1시간', durationMinutes: 60, latitude: 34.89, longitude: 128.63, locationLabel: null, googleMapsUri: null, placeId: 'place-2', resolved: true, displaySource: 'PROVIDER', source: 'AI_DRAFT' },
        ]}
        route={route}
        routeError="새 경로 확인이 지연되고 있어요."
        routeStatus="error"
        selectedPlace={null}
        selectedPlaceId="1"
        onSelectPlace={vi.fn()}
      />,
    )

    expect(screen.getByTestId('map-route-points')).toHaveTextContent('2')
    expect(screen.getByText(/마지막으로 확인한 경로를 표시합니다/)).toBeInTheDocument()
  })
})
