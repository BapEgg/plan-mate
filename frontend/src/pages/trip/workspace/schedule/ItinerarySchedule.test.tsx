import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { DayRoute } from '../../../../api/routes'
import { ItinerarySchedule } from './ItinerarySchedule'

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
    geometry: [],
    verifiedAt: '2026-09-02T08:00:00Z',
  }],
}

describe('ItinerarySchedule route failure state', () => {
  it('keeps the last verified travel time instead of replacing the schedule with an error', () => {
    render(
      <ItinerarySchedule
        activeDate="2026-10-10"
        activeDay={1}
        days={[1]}
        places={[
          { id: '1', day: 1, order: 1, title: '매미성', startTime: '10:00', duration: '2시간', durationMinutes: 120, latitude: 34.88, longitude: 128.62, locationLabel: null, googleMapsUri: null, placeId: 'place-1', resolved: true, displaySource: 'PROVIDER', source: 'AI_DRAFT' },
          { id: '2', day: 1, order: 2, title: '바람의 언덕', startTime: '13:00', duration: '1시간', durationMinutes: 60, latitude: 34.89, longitude: 128.63, locationLabel: null, googleMapsUri: null, placeId: 'place-2', resolved: true, displaySource: 'PROVIDER', source: 'AI_DRAFT' },
        ]}
        placesStatus="success"
        route={route}
        routeError="새 경로 확인이 지연되고 있어요."
        routeStatus="error"
        selectedPlaceId="1"
        onDayChange={vi.fn()}
        onSelectPlace={vi.fn()}
      />,
    )

    expect(screen.getByText('자동차 13분 · 3.2km · 이전 확인')).toBeInTheDocument()
  })

  it('keeps the schedule visible and identifies saved place information', () => {
    render(
      <ItinerarySchedule
        activeDate="2026-10-10"
        activeDay={1}
        days={[1]}
        places={[
          { id: '1', day: 1, order: 1, title: '매미성', startTime: '10:00', duration: '2시간', durationMinutes: 120, latitude: 34.88, longitude: 128.62, locationLabel: null, googleMapsUri: null, placeId: 'place-1', resolved: true, displaySource: 'SAVED_SNAPSHOT', source: 'AI_DRAFT' },
        ]}
        placesStatus="success"
        route={null}
        routeError=""
        routeStatus="success"
        selectedPlaceId="1"
        onDayChange={vi.fn()}
        onSelectPlace={vi.fn()}
      />,
    )

    expect(screen.getByText('매미성')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('저장해 둔 이름과 위치를 표시하고 있어요')
  })
})
