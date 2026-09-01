import { useEffect, useRef, useState } from 'react'
import type { ItineraryPlace } from '../workspaceTypes'

/**
 * Docked place-detail surface for the map column. Renders into the
 * `.trip-map-canvas > .place-detail-panel` CSS slot. Owns the live Google
 * Places lookup (rating/hours/address/photo) previously baked into
 * TripMapView's InfoWindow — TripMapView stays responsible for markers and
 * selection only.
 */

type GooglePlacesNamespace = typeof globalThis & {
  google?: {
    maps: {
      places: {
        PlacesService: new (element: HTMLElement) => GooglePlacesService
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

function usePlaceDetails(placeId: string | null) {
  const [details, setDetails] = useState<GooglePlaceResult | null>(null)
  const serviceRef = useRef<GooglePlacesService | null>(null)
  const cacheRef = useRef<Map<string, GooglePlaceResult>>(new Map())

  useEffect(() => {
    setDetails(placeId ? cacheRef.current.get(placeId) ?? null : null)
    if (!placeId) return
    if (cacheRef.current.has(placeId)) return

    const google = (window as GooglePlacesNamespace).google
    if (!google?.maps?.places) return

    if (!serviceRef.current) {
      try {
        serviceRef.current = new google.maps.places.PlacesService(document.createElement('div'))
      } catch {
        serviceRef.current = null
      }
    }
    if (!serviceRef.current) return

    let cancelled = false
    serviceRef.current.getDetails(
      { placeId, fields: ['rating', 'user_ratings_total', 'formatted_address', 'opening_hours', 'photos'] },
      (result, status) => {
        if (cancelled || status !== 'OK' || !result) return
        cacheRef.current.set(placeId, result)
        setDetails(result)
      },
    )
    return () => {
      cancelled = true
    }
  }, [placeId])

  return details
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>
}

export function PlacePanel({ place }: { place: ItineraryPlace | null }) {
  const details = usePlaceDetails(place?.placeId ?? null)

  if (!place) {
    return (
      <aside className="place-detail-panel empty">
        <span className="section-kicker">장소 정보</span>
        <h2>장소를 선택해 주세요.</h2>
        <p>장소를 선택하면 방문 시간과 위치를 확인할 수 있습니다.</p>
      </aside>
    )
  }

  const photoUrl = details?.photos?.[0]?.getUrl({ maxWidth: 260 })
  const isOpen = details?.opening_hours?.isOpen?.()

  return (
    <aside className="place-detail-panel" aria-live="polite">
      <div className="place-detail-heading">
        <span className="section-kicker">{place.day}일차 · {place.order}번째 장소</span>
        <span className={`place-resolution ${place.resolved ? 'resolved' : 'unresolved'}`}><i aria-hidden="true" />{place.resolved ? '장소 확인됨' : '장소 확인 전'}</span>
      </div>
      <h2>{place.title}</h2>
      {photoUrl && <img className="place-detail-photo" src={photoUrl} alt="" />}
      {(details?.rating || details?.opening_hours?.isOpen) && (
        <div className="place-detail-rating-row">
          {details?.rating && (
            <span className="place-detail-rating">
              ★ {details.rating.toFixed(1)}
              {details.user_ratings_total ? ` (리뷰 ${details.user_ratings_total.toLocaleString('ko-KR')}개)` : ''}
            </span>
          )}
          {details?.opening_hours?.isOpen && (
            <span className={`place-detail-open-status ${isOpen ? 'open' : 'closed'}`}>{isOpen ? '영업 중' : '영업 종료'}</span>
          )}
        </div>
      )}
      {details?.formatted_address && <p className="place-detail-address">{details.formatted_address}</p>}
      <dl>
        <DetailItem label="방문 시간" value={place.startTime} />
        <DetailItem label="체류 시간" value={place.duration} />
      </dl>
      {place.googleMapsUri
        ? <a href={place.googleMapsUri} target="_blank" rel="noopener noreferrer">Google Maps에서 위치 보기 <span aria-hidden="true">↗</span></a>
        : <p className="place-fallback-message">장소 이름과 지도 정보는 외부 조회가 완료되면 표시됩니다.</p>}
    </aside>
  )
}
