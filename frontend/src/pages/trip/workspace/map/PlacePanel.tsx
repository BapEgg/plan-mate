import type { ItineraryPlace } from '../workspaceTypes'

/**
 * Docked place-detail surface for the map column. Renders into the
 * `.trip-map-canvas > .place-detail-panel` CSS slot. It intentionally consumes
 * only the backend-resolved place view. Optional photo/rating enrichment must
 * use the Google Places API (New) through a separate backend boundary; the
 * retired browser PlacesService is not called from this workspace.
 */

function DetailItem({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>
}

export function PlacePanel({ place }: { place: ItineraryPlace | null }) {
  if (!place) {
    return (
      <aside className="place-detail-panel empty">
        <span className="section-kicker">장소 정보</span>
        <h2>장소를 선택해 주세요.</h2>
        <p>장소를 선택하면 방문 시간과 위치를 확인할 수 있습니다.</p>
      </aside>
    )
  }

  return (
    <aside className="place-detail-panel" aria-live="polite">
      <div className="place-detail-heading">
        <span className="section-kicker">{place.day}일차 · {place.order}번째 장소</span>
        <span className={`place-resolution ${place.resolved ? 'resolved' : 'unresolved'}`}><i aria-hidden="true" />{place.resolved ? '장소 확인됨' : '장소 확인 전'}</span>
      </div>
      <h2>{place.title}</h2>
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
