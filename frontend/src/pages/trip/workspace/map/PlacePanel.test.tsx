import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PlacePanel } from './PlacePanel'
import type { ItineraryPlace } from '../workspaceTypes'

const basePlace: ItineraryPlace = {
  id: '1',
  day: 1,
  order: 2,
  title: '한라수목원',
  startTime: '09:00',
  duration: '1시간 30분',
  durationMinutes: 90,
  latitude: 33.5,
  longitude: 126.5,
  locationLabel: '33.50000, 126.50000',
  googleMapsUri: 'https://maps.google.com/?cid=123',
  placeId: 'place-1',
  resolved: true,
  displaySource: 'PROVIDER',
  source: 'AI_DRAFT',
}

describe('PlacePanel', () => {
  it('shows a prompt to select a place when nothing is selected', () => {
    render(<PlacePanel place={null} />)

    expect(screen.getByRole('heading', { name: '장소를 선택해 주세요.' })).toBeInTheDocument()
  })

  it('renders day/order, resolution status, title, and visit/stay time for a resolved place', () => {
    render(<PlacePanel place={basePlace} />)

    expect(screen.getByText('1일차 · 2번째 장소')).toBeInTheDocument()
    expect(screen.getByText('장소 확인됨')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '한라수목원' })).toBeInTheDocument()
    expect(screen.getByText('09:00')).toBeInTheDocument()
    expect(screen.getByText('1시간 30분')).toBeInTheDocument()
  })

  it('links out to Google Maps when a googleMapsUri is available', () => {
    render(<PlacePanel place={basePlace} />)

    const link = screen.getByRole('link', { name: /Google 지도에서 보기/ })
    expect(link).toHaveAttribute('href', basePlace.googleMapsUri)
  })

  it('shows the unresolved badge and a fallback message when location lookup has not resolved yet', () => {
    render(<PlacePanel place={{ ...basePlace, resolved: false, displaySource: 'UNRESOLVED', googleMapsUri: null }} />)

    expect(screen.getByText('장소 확인 전')).toBeInTheDocument()
    expect(screen.getByText('장소 이름과 지도 정보는 외부 조회가 완료되면 표시됩니다.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Google 지도에서 보기/ })).not.toBeInTheDocument()
  })

  it('explains when saved candidate information is shown during a provider outage', () => {
    render(<PlacePanel place={{ ...basePlace, displaySource: 'SAVED_SNAPSHOT', googleMapsUri: null }} />)

    expect(screen.getByText('저장된 장소 정보')).toBeInTheDocument()
    expect(screen.getByText('저장해 둔 이름과 위치를 지도에 표시하고 있어요.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Google 지도에서 보기/ })).not.toBeInTheDocument()
  })
})
