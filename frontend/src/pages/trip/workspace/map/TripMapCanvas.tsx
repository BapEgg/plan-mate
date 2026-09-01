import { useMemo, useState } from 'react'
import { TripMapView } from './TripMapView'
import type { MapMarkerPlace } from './TripMapView'
import { PlacePanel } from './PlacePanel'
import type { ItineraryPlace } from '../workspaceTypes'
import { formatFullDate } from '../workspaceFormatters'

export function TripMapCanvas({ activeDay, activeDate, destination, destinationCenter, places, selectedPlace, selectedPlaceId, onSelectPlace }: {
  activeDay: number
  activeDate: string | null
  destination: string
  destinationCenter: { lat: number; lng: number } | null
  places: ItineraryPlace[]
  selectedPlace: ItineraryPlace | null
  selectedPlaceId: string
  onSelectPlace: (placeId: string) => void
}) {
  const [manualFitCount, setManualFitCount] = useState(0)
  const [markerColor, setMarkerColor] = useState('#e0483e')
  const fitSignal = `${activeDay}:${manualFitCount}`

  const markerPlaces = useMemo<MapMarkerPlace[]>(() => places
    .filter((place): place is ItineraryPlace & { latitude: number; longitude: number } =>
      place.latitude !== null && place.longitude !== null)
    .map((place) => ({ id: place.id, order: place.order, title: place.title, startTime: place.startTime, duration: place.duration, placeId: place.placeId, latitude: place.latitude, longitude: place.longitude })),
  [places])
  const unresolvedCount = places.length - markerPlaces.length

  return (
    <section className="trip-map-panel" aria-label={`${activeDay}일차 장소 지도`}>
      <div className="trip-map-heading">
        <div><span className="section-kicker">{destination}</span><h2>{activeDay}일차 지도</h2></div>
        <div className="map-heading-meta">
          <span>{activeDate ? formatFullDate(activeDate) : ''}</span>
          <label className="map-marker-color-picker">
            마커 색상
            <input type="color" value={markerColor} onChange={(event) => setMarkerColor(event.target.value)} aria-label="마커 색상 선택" />
          </label>
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
            markerColor={markerColor}
            onSelectPlace={onSelectPlace}
          />
        )}
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
