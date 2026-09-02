import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { TripMapView } from './TripMapView'
import type { MapMarkerPlace } from './TripMapView'

const noop = () => {}

const place: MapMarkerPlace = {
  id: 'p1',
  order: 1,
  title: '한라수목원',
  startTime: '09:00',
  duration: '1시간 30분',
  placeId: 'place-1',
  latitude: 33.5,
  longitude: 126.5,
}

class MockMap {
  fitBounds = vi.fn()
  setCenter = vi.fn()
  setZoom = vi.fn()
  getZoom = vi.fn(() => 10)
}

class MockMarker {
  static instances: MockMarker[] = []
  listeners: Record<string, () => void> = {}
  setMap = vi.fn()
  setIcon = vi.fn()
  setLabel = vi.fn()
  setZIndex = vi.fn()
  constructor() {
    MockMarker.instances.push(this)
  }
  addListener(event: string, handler: () => void) {
    this.listeners[event] = handler
  }
}

class MockLatLngBounds {
  extend = vi.fn()
  isEmpty = vi.fn(() => false)
}

class MockPolyline {
  static instances: MockPolyline[] = []
  setMap = vi.fn()
  constructor() {
    MockPolyline.instances.push(this)
  }
}

describe('TripMapView', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    delete (window as { google?: unknown }).google
    MockMarker.instances = []
    MockPolyline.instances = []
  })

  it('renders an honest unconfigured fallback when no map key is set, instead of a blank or broken map', () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_API_KEY', '')

    render(
      <TripMapView
        places={[]}
        selectedPlaceId=""
        fitSignal="1:0"
        fallbackCenter={null}
        routePoints={[]}
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
        routePoints={[]}
        onSelectPlace={noop}
      />,
    )

    expect(screen.getByText('지도를 불러오고 있습니다.')).toBeInTheDocument()
  })

  it('creates a marker per place and calls onSelectPlace when it is clicked', async () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_API_KEY', 'test-key')
    ;(window as unknown as { google: unknown }).google = {
      maps: {
        Map: MockMap,
        Marker: MockMarker,
        Polyline: MockPolyline,
        LatLngBounds: MockLatLngBounds,
        SymbolPath: { CIRCLE: 0 },
        event: { clearInstanceListeners: vi.fn() },
      },
    }
    const onSelectPlace = vi.fn()

    render(
      <TripMapView
        places={[place]}
        selectedPlaceId=""
        fitSignal="1:0"
        fallbackCenter={null}
        routePoints={[]}
        onSelectPlace={onSelectPlace}
      />,
    )

    await waitFor(() => expect(MockMarker.instances).toHaveLength(1))
    MockMarker.instances[0].listeners.click()

    expect(onSelectPlace).toHaveBeenCalledWith('p1')
  })

  it('draws a polyline only when verified route coordinates are supplied', async () => {
    vi.stubEnv('VITE_GOOGLE_MAPS_API_KEY', 'test-key')
    ;(window as unknown as { google: unknown }).google = {
      maps: {
        Map: MockMap,
        Marker: MockMarker,
        Polyline: MockPolyline,
        LatLngBounds: MockLatLngBounds,
        SymbolPath: { CIRCLE: 0 },
        event: { clearInstanceListeners: vi.fn() },
      },
    }

    render(
      <TripMapView
        places={[place]}
        selectedPlaceId=""
        fitSignal="1:0"
        fallbackCenter={null}
        routePoints={[
          { latitude: 33.5, longitude: 126.5 },
          { latitude: 33.6, longitude: 126.6 },
        ]}
        onSelectPlace={noop}
      />,
    )

    await waitFor(() => expect(MockPolyline.instances).toHaveLength(1))
  })
})
