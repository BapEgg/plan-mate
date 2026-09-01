import { useEffect, useRef, useState } from 'react'
import { shouldRefitBounds } from './shouldRefitBounds'

/**
 * Thin adapter around the Google Maps JavaScript SDK so the rest of the
 * workspace only depends on real resolved coordinates and never has to know
 * about the underlying map provider. No route/polyline rendering here: this
 * adapter only ever draws markers for coordinates the backend has actually
 * resolved, per the product rule against implying a route that was never
 * computed by a routing provider.
 */

type GoogleMapsNamespace = typeof globalThis & {
  google?: {
    maps: {
      Map: new (element: HTMLElement, options: Record<string, unknown>) => GoogleMap
      Marker: new (options: Record<string, unknown>) => GoogleMarker
      InfoWindow: new (options?: Record<string, unknown>) => GoogleInfoWindow
      LatLngBounds: new () => GoogleLatLngBounds
      SymbolPath: { CIRCLE: number }
      event: { clearInstanceListeners: (instance: unknown) => void }
      places: {
        PlacesService: new (map: GoogleMap) => GooglePlacesService
      }
    }
  }
}

type GooglePlaceResult = {
  rating?: number
  user_ratings_total?: number
  formatted_address?: string
  opening_hours?: { isOpen?: () => boolean }
  photos?: Array<{ getUrl: (options: { maxWidth: number }) => string }>
}

type GooglePlacesService = {
  getDetails: (
    request: { placeId: string; fields: string[] },
    callback: (result: GooglePlaceResult | null, status: string) => void,
  ) => void
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

type GoogleInfoWindow = {
  setContent: (content: string) => void
  open: (options: { map: GoogleMap; anchor: GoogleMarker }) => void
  close: () => void
  addListener: (event: string, handler: () => void) => void
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
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(apiKey)}&libraries=places&callback=${MAPS_READY_CALLBACK}`
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

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function infoWindowContent(place: MapMarkerPlace, details?: GooglePlaceResult) {
  const ratingRow = details?.rating
    ? `<div style="display:flex;align-items:center;gap:4px;font-size:11px;color:#9d5f08;margin-bottom:4px">
        <strong>★ ${details.rating.toFixed(1)}</strong>
        ${details.user_ratings_total ? `<span style="color:#7f8899">리뷰 ${details.user_ratings_total.toLocaleString('ko-KR')}개</span>` : ''}
      </div>`
    : ''
  const openRow = details?.opening_hours?.isOpen
    ? `<div style="font-size:10px;font-weight:700;color:${details.opening_hours.isOpen() ? '#08764e' : '#b73443'};margin-bottom:4px">${details.opening_hours.isOpen() ? '영업 중' : '영업 종료'}</div>`
    : ''
  const addressRow = details?.formatted_address
    ? `<div style="font-size:10px;color:#7f8899;margin-bottom:6px">${escapeHtml(details.formatted_address)}</div>`
    : ''
  const photoUrl = details?.photos?.[0]?.getUrl({ maxWidth: 260 })
  const photoRow = photoUrl
    ? `<img src="${photoUrl}" alt="" style="display:block;width:100%;height:110px;object-fit:cover;border-radius:8px;margin-bottom:8px" />`
    : ''

  return `
    <div style="font-family:Pretendard,'Noto Sans KR',sans-serif;padding:2px 2px;min-width:200px;max-width:240px">
      <div style="font-size:11px;font-weight:700;color:#5b7fc9;margin-bottom:2px">${place.order}번째 장소</div>
      <div style="font-size:14px;font-weight:800;color:#17384a;margin-bottom:6px">${escapeHtml(place.title)}</div>
      ${photoRow}
      ${ratingRow}
      ${openRow}
      ${addressRow}
      <div style="display:flex;gap:10px;font-size:11px;color:#345568">
        <span><strong style="color:#17384a">${escapeHtml(place.startTime)}</strong> 방문</span>
        <span>${escapeHtml(place.duration)} 머물기</span>
      </div>
    </div>
  `
}

type TripMapViewProps = {
  places: MapMarkerPlace[]
  selectedPlaceId: string
  fitSignal: string
  fallbackCenter: { lat: number; lng: number } | null
  markerColor: string
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

export function TripMapView({ places, selectedPlaceId, fitSignal, fallbackCenter, markerColor, onSelectPlace }: TripMapViewProps) {
  const containerRef = useRef<HTMLDivElement | null>(null)
  const mapRef = useRef<GoogleMap | null>(null)
  const markersRef = useRef<Map<string, GoogleMarker>>(new Map())
  const infoWindowRef = useRef<GoogleInfoWindow | null>(null)
  const placesServiceRef = useRef<GooglePlacesService | null>(null)
  const placeDetailsCacheRef = useRef<Map<string, GooglePlaceResult>>(new Map())
  const onSelectPlaceRef = useRef(onSelectPlace)
  const selectedPlaceIdRef = useRef(selectedPlaceId)
  const lastFitRef = useRef<{ signal: string | null; locatedCount: number }>({ signal: null, locatedCount: 0 })
  useEffect(() => {
    onSelectPlaceRef.current = onSelectPlace
    selectedPlaceIdRef.current = selectedPlaceId
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
        infoWindowRef.current = new google.maps.InfoWindow()
        infoWindowRef.current.addListener('closeclick', () => onSelectPlaceRef.current(''))
        try {
          placesServiceRef.current = new google.maps.places.PlacesService(mapRef.current)
        } catch {
          placesServiceRef.current = null
        }
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
      marker.setIcon(markerIcon(google, isSelected, markerColor))
      marker.setLabel(markerLabel(place.order, isSelected))
      marker.setZIndex(isSelected ? 10 : 1)

      if (isSelected && infoWindowRef.current) {
        const cachedDetails = place.placeId ? placeDetailsCacheRef.current.get(place.placeId) : undefined
        infoWindowRef.current.setContent(infoWindowContent(place, cachedDetails))
        infoWindowRef.current.open({ map, anchor: marker })

        if (place.placeId && !cachedDetails && placesServiceRef.current) {
          placesServiceRef.current.getDetails(
            { placeId: place.placeId, fields: ['rating', 'user_ratings_total', 'formatted_address', 'opening_hours', 'photos'] },
            (result, requestStatus) => {
              if (requestStatus !== 'OK' || !result) return
              placeDetailsCacheRef.current.set(place.placeId as string, result)
              if (selectedPlaceIdRef.current === place.id && infoWindowRef.current) {
                infoWindowRef.current.setContent(infoWindowContent(place, result))
              }
            },
          )
        }
      }
    })

    if (!places.some((place) => place.id === selectedPlaceId)) {
      infoWindowRef.current?.close()
    }
  }, [places, selectedPlaceId, status, markerColor])

  useEffect(() => {
    if (status !== 'ready') return
    const google = (window as GoogleMapsNamespace).google
    const map = mapRef.current
    if (!google || !map) return
    const located = places.filter((place) => Number.isFinite(place.latitude) && Number.isFinite(place.longitude))

    const shouldFit = shouldRefitBounds(lastFitRef.current.signal, lastFitRef.current.locatedCount, fitSignal, located.length)
    lastFitRef.current = { signal: fitSignal, locatedCount: located.length }
    if (!shouldFit) return

    if (located.length === 0) {
      if (fallbackCenter) {
        map.setCenter(fallbackCenter)
        map.setZoom(13)
      }
      return
    }
    if (located.length === 1) {
      map.setCenter({ lat: located[0].latitude, lng: located[0].longitude })
      map.setZoom(15)
      return
    }
    const bounds = new google.maps.LatLngBounds()
    located.forEach((place) => bounds.extend({ lat: place.latitude, lng: place.longitude }))
    map.fitBounds(bounds, 56)
  }, [places, fitSignal, status, fallbackCenter])

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
      <div className="trip-map-surface" ref={containerRef} role="application" aria-label="여행 장소 지도" />
    </>
  )
}
