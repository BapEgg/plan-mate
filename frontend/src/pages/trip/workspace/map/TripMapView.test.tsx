import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { TripMapView } from './TripMapView'

const noop = () => {}

describe('TripMapView', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('renders an honest unconfigured fallback when no map key is set, instead of a blank or broken map', () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_API_KEY', '')

    render(
      <TripMapView
        places={[]}
        selectedPlaceId=""
        fitSignal="1:0"
        fallbackCenter={null}
        markerColor="#e0483e"
        onSelectPlace={noop}
      />,
    )

    expect(screen.getByRole('status')).toHaveTextContent('지도를 표시할 수 없습니다.')
  })

  it('shows a loading state before the map key is verified, when one is configured', () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_API_KEY', 'test-key')

    render(
      <TripMapView
        places={[]}
        selectedPlaceId=""
        fitSignal="1:0"
        fallbackCenter={null}
        markerColor="#e0483e"
        onSelectPlace={noop}
      />,
    )

    expect(screen.getByText('지도를 불러오고 있습니다.')).toBeInTheDocument()
  })
})
