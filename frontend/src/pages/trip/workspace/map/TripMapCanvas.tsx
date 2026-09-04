import { useMemo, useState } from 'react'
import type { DayRoute } from '../../../../api/routes'
import { TripMapView } from './TripMapView'
import type { MapMarkerPlace } from './TripMapView'
import { PlacePanel } from './PlacePanel'
import type { ItineraryPlace } from '../workspaceTypes'
import type { AsyncStatus } from '../workspaceTypes'
import { formatFullDate } from '../workspaceFormatters'

export function TripMapCanvas({ activeDay, activeDate, destination, destinationCenter, places, route, routeError, routeStatus, selectedPlace, selectedPlaceId, onSelectPlace }: {
  activeDay: number
  activeDate: string | null
  destination: string
  destinationCenter: { lat: number; lng: number } | null
  places: ItineraryPlace[]
  route: DayRoute | null
  routeError: string
  routeStatus: AsyncStatus
  selectedPlace: ItineraryPlace | null
  selectedPlaceId: string
  onSelectPlace: (placeId: string) => void
}) {
  const [manualFitCount, setManualFitCount] = useState(0)
  const fitSignal = `${activeDay}:${manualFitCount}`

  const markerPlaces = useMemo<MapMarkerPlace[]>(() => places
    .filter((place): place is ItineraryPlace & { latitude: number; longitude: number } =>
      place.latitude !== null && place.longitude !== null)
    .map((place) => ({ id: place.id, order: place.order, title: place.title, startTime: place.startTime, duration: place.duration, placeId: place.placeId, latitude: place.latitude, longitude: place.longitude })),
  [places])
  const unresolvedCount = places.length - markerPlaces.length
  const routePoints = useMemo(() => route?.legs
    .filter((leg) => leg.status === 'READY')
    .flatMap((leg) => leg.geometry) ?? [], [route])

  return (
    <section className="trip-map-panel" aria-label={`${activeDay}일차 장소 지도`}>
      <div className="trip-map-heading">
        <div><span className="section-kicker">{destination}</span><h2>{activeDay}일차 지도</h2></div>
        <div className="map-heading-meta">
          <span>{activeDate ? formatFullDate(activeDate) : ''}</span>
          <button className="map-refit-button" type="button" onClick={() => setManualFitCount((value) => value + 1)}>일정 전체 보기</button>
        </div>
      </div>
      <div className="trip-map-canvas">
        {places.length === 0 ? (
          <div className="trip-map-empty"><strong>표시할 장소가 없습니다.</strong><span>다른 날짜를 선택해 주세요.</span></div>
        ) : (
          <TripMapView
            places={markerPlaces}
            selectedPlaceId={selectedPlaceId}
            fitSignal={fitSignal}
            fallbackCenter={destinationCenter}
            routePoints={routePoints}
            onSelectPlace={onSelectPlace}
          />
        )}
        <div className={`map-route-status ${routeStatus}`} aria-live="polite">
          <span className="map-route-status-mark" aria-hidden="true" />
          <span>{routeStatusText(routeStatus, route, routeError)}</span>
        </div>
        {unresolvedCount > 0 && (
          <div className="map-preview-notice" role="status">
            <i aria-hidden="true" />
            <span><strong>장소 위치 확인 중</strong>{unresolvedCount}곳은 지도 위치를 아직 확인하지 못했습니다.</span>
          </div>
        )}
        <PlacePanel place={selectedPlace} />
      </div>
    </section>
  )
}

function routeStatusText(status: AsyncStatus, route: DayRoute | null, error: string) {
  if (status === 'loading') return '차로 이동할 길을 확인하고 있어요…'
  if (status === 'error' && route) return `${error || '새 경로를 확인하지 못했어요.'} 마지막으로 확인한 경로를 표시합니다.`
  if (status === 'error') return error || '경로를 잠시 확인하지 못했어요.'
  if (!route || route.legs.length === 0) return '장소를 고르면 이동 경로를 함께 볼 수 있어요.'
  if (route.status === 'PARTIAL') return '확인된 구간만 지도에 표시했어요. · Kakao Mobility'
  return '차로 이동할 길 · Kakao Mobility'
}
