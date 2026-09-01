import type { AsyncStatus, ItineraryPlace } from '../workspaceTypes'
import { formatDayTabDate, formatFullDate, shiftDate } from '../workspaceFormatters'

export function ItinerarySchedule({ activeDate, activeDay, className, days, id, places, placesStatus, panelRole, ariaLabelledBy, selectedPlaceId, onDayChange, onSelectPlace }: {
  activeDate: string | null
  activeDay: number
  className?: string
  days: number[]
  id?: string
  places: ItineraryPlace[]
  placesStatus: AsyncStatus
  panelRole?: string
  ariaLabelledBy?: string
  selectedPlaceId: string
  onDayChange: (day: number) => void
  onSelectPlace: (placeId: string) => void
}) {
  return (
    <section
      aria-label={ariaLabelledBy ? undefined : `${activeDay}일차 일정`}
      aria-labelledby={ariaLabelledBy}
      className={`itinerary-schedule ${className ?? ''}`}
      id={id}
      role={panelRole}
    >
      <div className="schedule-heading">
        <div><span className="section-kicker">여행 일정</span><h2>{activeDay}일차</h2>{activeDate && <p>{formatFullDate(activeDate)}</p>}</div>
        <span className="schedule-place-count">{places.length}곳</span>
      </div>
      <div className="day-tabs" role="tablist" aria-label="여행 일자 선택">
          {days.map((day) => (
            <button aria-selected={day === activeDay} className={day === activeDay ? 'active' : ''} key={day} role="tab" type="button" onClick={() => onDayChange(day)}>
              <strong>DAY {day}</strong>
              <span>{activeDate ? formatDayTabDate(shiftDate(activeDate, day - activeDay)) : `${day}일차`}</span>
            </button>
          ))}
      </div>
      {placesStatus === 'loading' && <div className="place-load-notice" aria-live="polite"><span className="state-spinner" aria-hidden="true" /><span>저장된 일정을 먼저 표시했습니다. 장소 이름과 지도 정보를 불러오는 중입니다.</span></div>}
      {placesStatus === 'error' && <div className="place-load-notice error" role="status">일정 순서와 시간은 저장되어 있습니다. 장소 이름과 지도 정보만 다시 불러와 주세요.</div>}
      {places.length ? (
        <ol className="place-timeline">
          {places.map((place) => (
            <li className={place.id === selectedPlaceId ? 'selected' : ''} key={place.id}>
              <button aria-pressed={place.id === selectedPlaceId} type="button" onClick={() => onSelectPlace(place.id)}>
                <span className="timeline-order">{place.order}</span>
                <span className="timeline-time">{place.startTime}</span>
                <span className="timeline-content"><strong>{place.title}</strong><small>{place.duration} 머물기{place.locationLabel ? ' · 위치 확인됨' : ''}</small></span>
                <span className="timeline-action" aria-hidden="true">›</span>
              </button>
            </li>
          ))}
        </ol>
      ) : (
        <div className="day-empty-state">
          <h3>아직 정해진 일정이 없어요.</h3>
          <p>일정이 저장되면 시간과 방문 순서가 이곳에 표시됩니다.</p>
          <div className="schedule-blueprint" aria-hidden="true">
            {[0, 1, 2].map((item) => (
              <span key={item}><i /><b /><em /></span>
            ))}
          </div>
        </div>
      )}
    </section>
  )
}
