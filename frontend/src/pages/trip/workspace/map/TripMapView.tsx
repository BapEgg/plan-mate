import { useEffect, useRef, useState } from 'react'
import { shouldRefitBounds } from './shouldRefitBounds'

/**
 * Thin adapter around the Google Maps JavaScript SDK so the rest of the
 * workspace only depends on real resolved coordinates and never has to know
 * about the underlying map provider. It draws markers for resolved places and
 * a polyline only from verified Kakao route coordinates supplied by the backend.
 */

type GoogleMapsNamespace = typeof globalThis & {
  google?: {
    maps: {
      Map: new (element: HTMLElement, options: Record<string, unknown>) => GoogleMap
      Marker: new (options: Record<string, unknown>) => GoogleMarker
      Polyline: new (options: Record<string, unknown>) => GooglePolyline
      LatLngBounds: new () => GoogleLatLngBounds
      SymbolPath: { CIRCLE: number }
      event: { clearInstanceListeners: (instance: unknown) => void }
    }
  }
}

type GoogleMap = {
  fitBounds: (bounds: GoogleLatLngBounds, padding?: number) => void
  setCenter: (position: { lat: number; lng: number }) => void
  setZoom: (zoom: number) => void
  getZoom: () => number
}

type GoogleMarker = {
  setMap: (map: GoogleMap | null) => void
  setIcon: (icon: Record<string, unknown>) => void
  setLabel: (label: Record<string, unknown>) => void
  setZIndex: (zIndex: number) => void
  addListener: (event: string, handler: () => void) => void
}

type GooglePolyline = {
  setMap: (map: GoogleMap | null) => void
}

type GoogleLatLngBounds = {
  extend: (position: { lat: number; lng: number }) => void
  isEmpty: () => boolean
}

let mapsLoadPromise: Promise<void> | null = null
const MAPS_READY_CALLBACK = '__planmateGoogleMapsReady'

function loadGoogleMaps(apiKey: string): Promise<void> {
  const win = window as GoogleMapsNamespace
  if (win.google?.maps?.Map) return Promise.resolve()
  if (mapsLoadPromise) return mapsLoadPromise

  mapsLoadPromise = new Promise<void>((resolve, reject) => {
    // Google's `callback` param only fires once the core `maps` library is
    // actually usable — unlike the script's own `load` event, which can fire
    // before `google.maps.Map` exists and cause a "not a constructor" error.
    ;(window as unknown as Record<string, () => void>)[MAPS_READY_CALLBACK] = () => resolve()

    if (document.querySelector('script[data-planmate-google-maps]')) {
      return
    }
    const script = document.createElement('script')
    // `libraries=places` is required for `google.maps.places.PlacesService` —
    // without it the places namespace is undefined and place-detail lookups
    // silently no-op.
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&loading=async&callback=${MAPS_READY_CALLBACK}`
    script.async = true
    script.dataset.planmateGoogleMaps = 'true'
    script.onerror = () => reject(new Error('Google Maps 스크립트를 불러오지 못했습니다.'))
    document.head.appendChild(script)
  }).catch((error: unknown) => {
    mapsLoadPromise = null
    throw error
  })

  return mapsLoadPromise
}

export type MapMarkerPlace = {
  id: string
  order: number
  title: string
  startTime: string
  duration: string
  placeId: string | null
  latitude: number
  longitude: number
}

type TripMapViewProps = {
  places: MapMarkerPlace[]
  selectedPlaceId: string
  fitSignal: string
  fallbackCenter: { lat: number; lng: number } | null
  routePoints: Array<{ latitude: number; longitude: number }>
  onSelectPlace: (placeId: string) => void
}

type MapStatus = 'loading' | 'ready' | 'error' | 'unconfigured'

const ACCENT = '#345568'

function markerLabel(order: number, selected: boolean) {
  return { text: String(order), color: '#ffffff', fontWeight: '800', fontSize: selected ? '14px' : '12px' }
}

function markerIcon(google: GoogleMapsNamespace['google'], selected: boolean, color: string) {
  return {
    path: google!.maps.SymbolPath.CIRCLE,
    scale: selected ? 18 : 14,
    fillColor: selected ? ACCENT : color,
    fillOpacity: 1,
    strokeColor: '#ffffff',
    strokeWeight: selected ? 4 : 3,
  }
}

export function TripMapView({ places, selectedPlaceId, fitSignal, fallbackCenter, routePoints, onSelectPlace }: TripMapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const mapRef = useRef<GoogleMap | null>(null)
  const markersRef = useRef<Map<string, GoogleMarker>>(new Map())
  const routeLineRef = useRef<GooglePolyline | null>(null)
  const onSelectPlaceRef = useRef(onSelectPlace)
  const lastFitRef = useRef<{ signal: string | null; locatedCount: number }>({ signal: null, locatedCount: 0 })
  useEffect(() => {
    onSelectPlaceRef.current = onSelectPlace
  })
  const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY as string | undefined
  const [status, setStatus] = useState<MapStatus>(() => (apiKey ? 'loading' : 'unconfigured'))

  useEffect(() => {
    if (!apiKey) {
      return
    }
    let cancelled = false
    loadGoogleMaps(apiKey)
      .then(() => {
        if (cancelled || !containerRef.current) return
        const google = (window as GoogleMapsNamespace).google
        if (!google) return
        mapRef.current = new google.maps.Map(containerRef.current, {
          center: fallbackCenter ?? { lat: 36.5, lng: 127.8 },
          zoom: fallbackCenter ? 13 : 7,
          disableDefaultUI: false,
          fullscreenControl: false,
          streetViewControl: false,
          mapTypeControl: false,
          clickableIcons: false,
        })
        setStatus('ready')
      })
      .catch(() => {
        if (!cancelled) setStatus('error')
      })
    return () => {
      cancelled = true
    }
    // Only (re)initialize the map instance once per mount + key availability.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiKey])

  useEffect(() => {
    if (status !== 'ready') return
    const google = (window as GoogleMapsNamespace).google
    const map = mapRef.current
    if (!google || !map) return

    const existingMarkers = markersRef.current
    const nextIds = new Set(places.map((place) => place.id))

    for (const [id, marker] of existingMarkers) {
      if (!nextIds.has(id)) {
        google.maps.event.clearInstanceListeners(marker)
        marker.setMap(null)
        existingMarkers.delete(id)
      }
    }

    places.forEach((place) => {
      const isSelected = place.id === selectedPlaceId
      let marker = existingMarkers.get(place.id)
      if (!marker) {
        marker = new google.maps.Marker({
          position: { lat: place.latitude, lng: place.longitude },
          map,
          title: place.title,
        })
        marker.addListener('click', () => onSelectPlaceRef.current(place.id))
        existingMarkers.set(place.id, marker)
      }
      marker.setIcon(markerIcon(google, isSelected, '#5f7fd0'))
      marker.setLabel(markerLabel(place.order, isSelected))
      marker.setZIndex(isSelected ? 10 : 1)
    })
  }, [places, selectedPlaceId, status])

  useEffect(() => {
    if (status !== 'ready') return undefined
    const google = (window as GoogleMapsNamespace).google
    const map = mapRef.current
    if (!google || !map) return undefined

    routeLineRef.current?.setMap(null)
    routeLineRef.current = null
    if (routePoints.length < 2) return undefined

    routeLineRef.current = new google.maps.Polyline({
      path: routePoints.map((point) => ({ lat: point.latitude, lng: point.longitude })),
      map,
      strokeColor: '#5879c7',
      strokeOpacity: 0.88,
      strokeWeight: 5,
      geodesic: false,
      zIndex: 2,
    })

    return () => {
      routeLineRef.current?.setMap(null)
      routeLineRef.current = null
    }
  }, [routePoints, status])

  useEffect(() => {
    if (status !== 'ready') return
    const google = (window as GoogleMapsNamespace).google
    const map = mapRef.current
    if (!google || !map) return
    const located = places.filter((place) => Number.isFinite(place.latitude) && Number.isFinite(place.longitude))
    const routeLocations = routePoints.map((point) => ({ latitude: point.latitude, longitude: point.longitude }))
    const visibleLocations = routeLocations.length ? [...located, ...routeLocations] : located

    const shouldFit = shouldRefitBounds(lastFitRef.current.signal, lastFitRef.current.locatedCount, fitSignal, visibleLocations.length)
    lastFitRef.current = { signal: fitSignal, locatedCount: visibleLocations.length }
    if (!shouldFit) return

    if (visibleLocations.length === 0) {
      if (fallbackCenter) {
        map.setCenter(fallbackCenter)
        map.setZoom(13)
      }
      return
    }
    if (visibleLocations.length === 1) {
      map.setCenter({ lat: visibleLocations[0].latitude, lng: visibleLocations[0].longitude })
      map.setZoom(15)
      return
    }
    const bounds = new google.maps.LatLngBounds()
    visibleLocations.forEach((place) => bounds.extend({ lat: place.latitude, lng: place.longitude }))
    map.fitBounds(bounds, 56)
  }, [places, routePoints, fitSignal, status, fallbackCenter])

  if (status === 'unconfigured') {
    return (
      <div className="trip-map-fallback" role="status">
        <strong>지도를 표시할 수 없습니다.</strong>
        <span>Google Maps 연결 설정이 없어 지도를 불러올 수 없습니다. 아래 목록에서 방문 순서와 시간을 확인해 주세요.</span>
      </div>
    )
  }

  if (status === 'error') {
    return (
      <div className="trip-map-fallback" role="alert">
        <strong>지도를 불러오지 못했습니다.</strong>
        <span>네트워크 연결을 확인한 뒤 다시 시도해 주세요. 일정 순서와 시간은 좌측에서 계속 확인할 수 있습니다.</span>
      </div>
    )
  }

  return (
    <>
      {status === 'loading' && (
        <div className="trip-map-fallback" role="status" aria-live="polite">
          <span className="state-spinner" aria-hidden="true" />
          <span>지도를 불러오고 있습니다.</span>
        </div>
      )}
      <div className="trip-map-surface" ref={containerRef} role="region" aria-label="여행 장소 지도" />
    </>
  )
}
