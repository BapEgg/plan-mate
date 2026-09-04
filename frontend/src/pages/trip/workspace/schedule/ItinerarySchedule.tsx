import { Fragment, useEffect, useRef, useState } from 'react'
import type { KeyboardEvent as ReactKeyboardEvent } from 'react'
import type { DayRoute, DayRouteLeg } from '../../../../api/routes'
import type { AsyncStatus, ItineraryPlace } from '../workspaceTypes'
import { formatDayTabDate, formatFullDate, shiftDate } from '../workspaceFormatters'

export function ItinerarySchedule({ activeDate, activeDay, canEdit, className, days, editDisabledReason, editInProgress, id, places, placesStatus, route, routeError, routeStatus, panelRole, ariaLabelledBy, selectedPlaceId, onDayChange, onEditFull, onEditPartial, onOpenActiveEdit, onSelectPlace }: {
  activeDate: string | null
  activeDay: number
  canEdit?: boolean
  className?: string
  days: number[]
  id?: string
  places: ItineraryPlace[]
  placesStatus: AsyncStatus
  route: DayRoute | null
  routeError: string
  routeStatus: AsyncStatus
  editDisabledReason?: string
  editInProgress?: boolean
  panelRole?: string
  ariaLabelledBy?: string
  selectedPlaceId: string
  onDayChange: (day: number) => void
  onEditFull?: () => void
  onEditPartial?: () => void
  onOpenActiveEdit?: () => void
  onSelectPlace: (placeId: string) => void
}) {
  const [editMenuOpen, setEditMenuOpen] = useState(false)
  const editMenuRef = useRef<HTMLDivElement>(null)
  const editTriggerRef = useRef<HTMLButtonElement>(null)
  const editMenuItemsRef = useRef<Array<HTMLButtonElement | null>>([])
  const usesSavedSnapshots = places.some((place) => place.displaySource === 'SAVED_SNAPSHOT')

  useEffect(() => {
    if (!editMenuOpen) return undefined
    const closeOnOutside = (event: MouseEvent) => {
      if (!editMenuRef.current?.contains(event.target as Node)) setEditMenuOpen(false)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setEditMenuOpen(false)
        editTriggerRef.current?.focus()
      }
    }
    document.addEventListener('mousedown', closeOnOutside)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnOutside)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [editMenuOpen])

  function openEditMenu() {
    setEditMenuOpen(true)
    window.requestAnimationFrame(() => editMenuItemsRef.current[0]?.focus())
  }

  function handleMenuKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    const enabledItems = editMenuItemsRef.current.filter((item): item is HTMLButtonElement => Boolean(item && !item.disabled))
    if (!enabledItems.length) return
    const currentIndex = enabledItems.indexOf(document.activeElement as HTMLButtonElement)
    let nextIndex: number | null = null
    if (event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % enabledItems.length
    if (event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + enabledItems.length) % enabledItems.length
    if (event.key === 'Home') nextIndex = 0
    if (event.key === 'End') nextIndex = enabledItems.length - 1
    if (nextIndex === null) return
    event.preventDefault()
    enabledItems[nextIndex].focus()
  }

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
        <div className="schedule-heading-actions">
          <span className="schedule-place-count">{places.length}곳</span>
          {canEdit && (
            <div className="schedule-edit-menu" ref={editMenuRef}>
              <button
                ref={editTriggerRef}
                className="schedule-edit-trigger"
                type="button"
                aria-haspopup={editInProgress ? undefined : 'menu'}
                aria-expanded={editInProgress ? undefined : editMenuOpen}
                aria-describedby={editDisabledReason ? 'schedule-edit-disabled-reason' : undefined}
                disabled={Boolean(editDisabledReason)}
                onClick={() => {
                  if (editInProgress) onOpenActiveEdit?.()
                  else if (editMenuOpen) setEditMenuOpen(false)
                  else openEditMenu()
                }}
                onKeyDown={(event) => {
                  if (!editInProgress && !editMenuOpen && ['ArrowDown', 'Enter', ' '].includes(event.key)) {
                    event.preventDefault()
                    openEditMenu()
                  }
                }}
              >{editInProgress ? '새 일정 확인' : '일정 수정'} {!editInProgress && <span aria-hidden="true">⌄</span>}</button>
              {editMenuOpen && !editInProgress && (
                <div className="schedule-edit-popover" role="menu" onKeyDown={handleMenuKeyDown}>
                  <button ref={(element) => { editMenuItemsRef.current[0] = element }} type="button" role="menuitem" disabled={!places.length} onClick={() => { setEditMenuOpen(false); onEditPartial?.() }}>
                    <strong>선택 구간 다시 만들기</strong>
                    <span>선택한 날짜의 일부 일정만 바꿔요.</span>
                  </button>
                  <button ref={(element) => { editMenuItemsRef.current[1] = element }} type="button" role="menuitem" onClick={() => { setEditMenuOpen(false); onEditFull?.() }}>
                    <strong>전체 일정 다시 만들기</strong>
                    <span>여행 조건을 확인하고 전체 일정을 새로 만들어요.</span>
                  </button>
                </div>
              )}
              {editDisabledReason && <span className="sr-only" id="schedule-edit-disabled-reason">{editDisabledReason}</span>}
            </div>
          )}
        </div>
      </div>
      <div className="day-tabs" role="group" aria-label="여행 일자 선택">
          {days.map((day) => (
            <button aria-pressed={day === activeDay} className={day === activeDay ? 'active' : ''} key={day} type="button" onClick={() => onDayChange(day)}>
              <strong>DAY {day}</strong>
              <span>{activeDate ? formatDayTabDate(shiftDate(activeDate, day - activeDay)) : `${day}일차`}</span>
            </button>
          ))}
      </div>
      {placesStatus === 'loading' && <div className="place-load-notice" aria-live="polite"><span className="state-spinner" aria-hidden="true" /><span>저장된 일정을 먼저 표시했습니다. 장소 이름과 지도 정보를 불러오는 중입니다.</span></div>}
      {placesStatus === 'success' && usesSavedSnapshots && <div className="place-load-notice saved" role="status">장소 정보 업데이트가 지연되어 저장해 둔 이름과 위치를 표시하고 있어요.</div>}
      {placesStatus === 'error' && <div className="place-load-notice error" role="status">일정 순서와 시간은 저장되어 있습니다. 장소 이름과 지도 정보만 다시 불러와 주세요.</div>}
      {places.length ? (
        <ol className="place-timeline">
          {places.map((place, index) => {
            const nextPlace = places[index + 1]
            const leg = nextPlace ? route?.legs.find((candidate) =>
              candidate.fromItemId.toString() === place.id && candidate.toItemId.toString() === nextPlace.id) : undefined
            return (
              <Fragment key={place.id}>
                <li className={place.id === selectedPlaceId ? 'selected' : ''}>
                  <button aria-pressed={place.id === selectedPlaceId} type="button" onClick={() => onSelectPlace(place.id)}>
                    <span className="timeline-order">{place.order}</span>
                    <span className="timeline-time">{place.startTime}</span>
                    <span className="timeline-content"><strong>{place.title}</strong><small>{place.duration} 머물기{place.locationLabel ? ' · 위치 확인됨' : ''}</small></span>
                    <span className="timeline-action" aria-hidden="true">›</span>
                  </button>
                </li>
                {nextPlace && (
                  <li className={`route-movement-row ${leg?.status === 'READY' ? 'ready' : ''}`}>
                    <span className="route-movement-line" aria-hidden="true" />
                    <span className="route-movement-icon" aria-hidden="true">↘</span>
                    <span>{movementLabel(leg, routeStatus, routeError)}</span>
                  </li>
                )}
              </Fragment>
            )
          })}
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

function movementLabel(leg: DayRouteLeg | undefined, status: AsyncStatus, error: string) {
  if (status === 'loading') return '자동차 경로 확인 중…'
  if (status === 'error' && leg?.status === 'READY' && leg.durationSeconds !== null && leg.distanceMeters !== null) {
    return `자동차 ${formatRouteDuration(leg.durationSeconds)} · ${formatDistance(leg.distanceMeters)} · 이전 확인`
  }
  if (status === 'error') return error || '이동 경로를 확인하지 못했어요.'
  if (!leg || leg.status !== 'READY' || leg.durationSeconds === null || leg.distanceMeters === null) {
    return '이동 경로를 확인하지 못했어요.'
  }
  return `자동차 ${formatRouteDuration(leg.durationSeconds)} · ${formatDistance(leg.distanceMeters)}`
}

function formatRouteDuration(seconds: number) {
  const minutes = Math.max(1, Math.round(seconds / 60))
  if (minutes < 60) return `${minutes}분`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours}시간 ${rest}분` : `${hours}시간`
}

function formatDistance(meters: number) {
  if (meters < 1000) return `${new Intl.NumberFormat('ko-KR').format(meters)}m`
  return `${new Intl.NumberFormat('ko-KR', {
    maximumFractionDigits: meters >= 10000 ? 0 : 1,
  }).format(meters / 1000)}km`
}
