import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError } from '../../../../api/client'
import {
  applyItineraryRegeneration,
  createItineraryRegeneration,
  getItineraryRegeneration,
  rejectItineraryRegeneration,
} from '../../../../api/regenerations'
import type { ItineraryRegeneration } from '../../../../api/regenerations'
import type { ItineraryPlace } from '../workspaceTypes'
import { formatDuration } from '../workspaceTypes'

type EditorMode = 'FULL' | 'PARTIAL'

export function ItineraryEditor({
  accessToken,
  activeDay,
  baseItineraryId,
  baseItineraryVersion,
  initialJob,
  mode,
  places,
  tripId,
  onClose,
  onJobChanged,
  onApplied,
}: {
  accessToken: string
  activeDay: number
  baseItineraryId: number
  baseItineraryVersion: number
  initialJob: ItineraryRegeneration | null
  mode: EditorMode
  places: ItineraryPlace[]
  tripId: string
  onClose: () => void
  onJobChanged: (job: ItineraryRegeneration) => void
  onApplied: () => void
}) {
  const surfaceRef = useRef<HTMLDivElement>(null)
  const discardDialogRef = useRef<HTMLDivElement>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const [job, setJob] = useState(initialJob)
  const [firstItemId, setFirstItemId] = useState<number | null>(null)
  const [lastItemId, setLastItemId] = useState<number | null>(null)
  const [fixedItemIds, setFixedItemIds] = useState<number[]>([])
  const [additionalRequest, setAdditionalRequest] = useState('')
  const [action, setAction] = useState<'create' | 'apply' | 'reject' | 'refresh' | null>(null)
  const [error, setError] = useState('')
  const [showDiscardDialog, setShowDiscardDialog] = useState(false)
  const [showUnchanged, setShowUnchanged] = useState(false)

  const dayPlaces = useMemo(
    () => places.filter((place) => place.day === activeDay).sort((left, right) => left.order - right.order),
    [activeDay, places],
  )
  const selectedRange = useMemo(() => {
    if (firstItemId === null) return []
    const firstIndex = dayPlaces.findIndex((place) => Number(place.id) === firstItemId)
    const lastIndex = lastItemId === null
      ? firstIndex
      : dayPlaces.findIndex((place) => Number(place.id) === lastItemId)
    if (firstIndex < 0 || lastIndex < 0) return []
    return dayPlaces.slice(Math.min(firstIndex, lastIndex), Math.max(firstIndex, lastIndex) + 1)
  }, [dayPlaces, firstItemId, lastItemId])
  const replaceCount = selectedRange.length - fixedItemIds.length
  const isDirty = additionalRequest.trim().length > 0 || firstItemId !== null || fixedItemIds.length > 0
  const activeRegenerationId = job?.regenerationId
  const activeRegenerationStatus = job?.status

  useEffect(() => {
    headingRef.current?.focus()
  }, [])

  useEffect(() => {
    if (!isDirty || job) return undefined
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
    }
    window.addEventListener('beforeunload', warnBeforeUnload)
    return () => window.removeEventListener('beforeunload', warnBeforeUnload)
  }, [isDirty, job])

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (showDiscardDialog) {
          setShowDiscardDialog(false)
        } else if (isDirty && !job) {
          setShowDiscardDialog(true)
        } else {
          onClose()
        }
        return
      }
      if (event.key === 'Tab') {
        const focusRoot = showDiscardDialog ? discardDialogRef.current : surfaceRef.current
        if (!focusRoot) return
        const focusable = getFocusableElements(focusRoot)
        if (!focusable.length) return
        const first = focusable[0]
        const last = focusable.at(-1)!
        const activeIndex = focusable.indexOf(document.activeElement as HTMLElement)
        if (event.shiftKey && activeIndex <= 0) {
          event.preventDefault()
          last.focus()
        } else if (!event.shiftKey && (activeIndex < 0 || document.activeElement === last)) {
          event.preventDefault()
          first.focus()
        }
      }
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [isDirty, job, onClose, showDiscardDialog])

  useEffect(() => {
    if (!activeRegenerationId || activeRegenerationStatus !== 'GENERATING') return undefined
    const timer = window.setInterval(() => {
      void getItineraryRegeneration(accessToken, tripId, activeRegenerationId)
        .then((next) => {
          setJob(next)
          onJobChanged(next)
        })
        .catch(() => undefined)
    }, 2000)
    return () => window.clearInterval(timer)
  }, [accessToken, activeRegenerationId, activeRegenerationStatus, onJobChanged, tripId])

  function requestClose() {
    if (isDirty && !job) {
      setShowDiscardDialog(true)
      return
    }
    onClose()
  }

  function handleRangeItem(place: ItineraryPlace) {
    const itemId = Number(place.id)
    setError('')
    if (firstItemId === null || lastItemId !== null) {
      setFirstItemId(itemId)
      setLastItemId(null)
      setFixedItemIds([])
      return
    }
    setLastItemId(itemId)
  }

  function toggleFixed(itemId: number) {
    setFixedItemIds((current) => current.includes(itemId)
      ? current.filter((candidate) => candidate !== itemId)
      : [...current, itemId])
  }

  async function createJob() {
    if (mode === 'PARTIAL' && (firstItemId === null || lastItemId === null)) {
      setError('바꿀 구간의 첫 장소와 마지막 장소를 선택해 주세요.')
      return
    }
    if (mode === 'PARTIAL' && replaceCount < 1) {
      setError('바꿀 장소를 한 곳 이상 남겨 주세요.')
      return
    }
    setAction('create')
    setError('')
    try {
      const response = await createItineraryRegeneration(accessToken, tripId, {
        baseItineraryId,
        expectedItineraryVersion: baseItineraryVersion,
        scope: mode === 'FULL'
          ? { type: 'FULL' }
          : {
              type: 'PARTIAL',
              dayNumber: activeDay,
              startItemId: firstItemId!,
              endItemId: lastItemId!,
              fixedItemIds,
            },
        additionalRequest: additionalRequest.trim() || null,
      })
      updateJob(response)
    } catch (cause) {
      setError(editorError(cause))
    } finally {
      setAction(null)
    }
  }

  async function refreshJob(regenerationId = job?.regenerationId, announce = true) {
    if (!regenerationId) return
    if (announce) setAction('refresh')
    try {
      updateJob(await getItineraryRegeneration(accessToken, tripId, regenerationId))
      if (announce) setError('')
    } catch (cause) {
      if (announce) setError(editorError(cause))
    } finally {
      if (announce) setAction(null)
    }
  }

  async function applyJob() {
    if (!job) return
    setAction('apply')
    setError('')
    try {
      const response = await applyItineraryRegeneration(accessToken, tripId, job.regenerationId)
      updateJob(response)
      onApplied()
      onClose()
    } catch (cause) {
      setError(editorError(cause))
    } finally {
      setAction(null)
    }
  }

  async function rejectJob() {
    if (!job) return
    setAction('reject')
    setError('')
    try {
      const response = await rejectItineraryRegeneration(accessToken, tripId, job.regenerationId)
      updateJob(response)
      onClose()
    } catch (cause) {
      setError(editorError(cause))
    } finally {
      setAction(null)
    }
  }

  function updateJob(next: ItineraryRegeneration) {
    setJob(next)
    onJobChanged(next)
  }

  const changedItems = job?.days.flatMap((day) => day.items.filter((item) => item.changed)).length ?? 0

  return (
    <div ref={surfaceRef} className="itinerary-editor-surface" role="dialog" aria-modal="true" aria-labelledby="itinerary-editor-heading">
      <header className="itinerary-editor-header">
        <button className="editor-close-button" type="button" onClick={requestClose} aria-label="일정 수정 닫기">←</button>
        <div>
          <span>{job?.scope === 'PARTIAL' || (!job && mode === 'PARTIAL') ? `${activeDay}일차` : '전체 일정'}</span>
          <h2 id="itinerary-editor-heading" ref={headingRef} tabIndex={-1}>일정 다시 만들기</h2>
        </div>
        <button className="editor-text-close" type="button" onClick={requestClose}>닫기</button>
      </header>

      <div className="itinerary-editor-body">
        {!job && mode === 'PARTIAL' && (
          <section className="editor-step" aria-labelledby="range-heading">
            <div className="editor-step-heading">
              <span>바꿀 범위</span>
              <h3 id="range-heading">
                {firstItemId === null
                  ? '첫 장소를 선택하세요.'
                  : lastItemId === null
                    ? '마지막 장소를 선택하세요.'
                    : '그대로 둘 장소를 확인하세요.'}
              </h3>
              <p>한 날짜 안에서 이어진 구간만 선택할 수 있어요.</p>
            </div>
            <ol className="editor-range-list">
              {dayPlaces.map((place) => {
                const itemId = Number(place.id)
                const inRange = selectedRange.some((candidate) => candidate.id === place.id)
                const fixed = fixedItemIds.includes(itemId)
                return (
                  <li className={`${inRange ? 'in-range' : ''} ${fixed ? 'fixed' : ''}`} key={place.id}>
                    <button className="editor-range-place" type="button" onClick={() => handleRangeItem(place)}>
                      <span>{place.startTime}</span>
                      <strong>{place.title}</strong>
                      <small>{place.duration}</small>
                    </button>
                    {inRange && lastItemId !== null && (
                      <button
                        className="editor-keep-button"
                        type="button"
                        aria-pressed={fixed}
                        aria-label={`${place.title}, ${fixed ? '그대로 두기' : '바꾸기'}`}
                        onClick={() => toggleFixed(itemId)}
                      >
                        {fixed ? '⌑ 그대로 두기' : '바꾸기'}
                      </button>
                    )}
                  </li>
                )
              })}
            </ol>
            {selectedRange.length > 0 && lastItemId !== null && (
              <div className="editor-range-summary" aria-live="polite">
                <strong>{activeDay}일차 {selectedRange[0].startTime}–{selectedRange.at(-1)?.startTime}</strong>
                <span>{selectedRange.length}곳 중 {replaceCount}곳 바꾸기</span>
              </div>
            )}
          </section>
        )}

        {!job && (
          <section className="editor-step editor-request-step" aria-labelledby="request-heading">
            <div className="editor-step-heading">
              <span>원하는 흐름</span>
              <h3 id="request-heading">이번 일정에서 바라는 점이 있나요?</h3>
              <p>{mode === 'FULL' ? '저장된 여행 조건을 바탕으로 전체 일정을 새로 만들어요.' : '선택한 구간과 그대로 둘 장소를 반영해 새 흐름을 만들어요.'}</p>
            </div>
            <label htmlFor="regeneration-request">추가 요청 <small>선택</small></label>
            <textarea
              autoComplete="off"
              id="regeneration-request"
              maxLength={1000}
              name="regenerationRequest"
              rows={4}
              value={additionalRequest}
              placeholder="예: 오후에는 바다를 천천히 볼 수 있는 곳을 넣어 주세요…"
              onChange={(event) => setAdditionalRequest(event.target.value)}
            />
            <span className="editor-character-count">{additionalRequest.length}/1000</span>
            <div className="editor-current-safe-note">
              <strong>지금 일정은 그대로 유지돼요.</strong>
              <span>새 일정이 준비되면 비교한 뒤 바꿀지 정할 수 있어요.</span>
            </div>
          </section>
        )}

        {job?.status === 'GENERATING' && (
          <section className="editor-generation-state" aria-live="polite">
            <div className="editor-generation-orbit" aria-hidden="true"><span /><i /><b /></div>
            <span>새 일정 준비 중</span>
            <h3>{job.scope === 'FULL' ? '여행 전체의 방문 순서와 시간을 확인하고 있어요.' : `${job.dayNumber}일차 선택 구간에 맞는 장소와 이동 흐름을 확인하고 있어요.`}</h3>
            <p>현재 일정은 모든 참여자에게 그대로 보입니다. 이 화면을 닫아도 작업은 계속돼요.</p>
            <button type="button" className="secondary" disabled={action !== null} onClick={() => void refreshJob()}>
              {action === 'refresh' ? '확인 중…' : '준비 상태 확인'}
            </button>
          </section>
        )}

        {job?.status === 'READY_FOR_REVIEW' && (
          <section className="editor-review" aria-labelledby="review-heading">
            <div className="editor-step-heading">
              <span>비교하기</span>
              <h3 id="review-heading">새 일정에서 {changedItems}곳이 달라져요.</h3>
              <p>바뀐 장소와 시간만 먼저 보여드려요. 적용하기 전까지 기존 일정은 유지됩니다.</p>
            </div>
            <label className="editor-unchanged-toggle">
              <input type="checkbox" checked={showUnchanged} onChange={(event) => setShowUnchanged(event.target.checked)} />
              유지되는 일정도 보기
            </label>
            <div className="editor-comparison-days">
              {job.days.map((day) => {
                const visibleItems = showUnchanged ? day.items : day.items.filter((item) => item.changed)
                if (!visibleItems.length) return null
                return (
                  <section key={day.day} className="editor-comparison-day">
                    <header><strong>DAY {day.day}</strong><span>{formatReviewDate(day.date)}</span></header>
                    <ol>
                      {visibleItems.map((item) => (
                        <li className={item.changed ? 'changed' : 'unchanged'} key={`${day.day}-${item.sequence}`}>
                          <span className="comparison-sequence">{item.sequence}</span>
                          <div className="comparison-before">
                            <small>기존</small>
                            <strong>{displayName(item.originalDisplayName, item.originalPlaceId, places)}</strong>
                            <span>{timeAndDuration(item.originalStartTime, item.originalDurationMinutes)}</span>
                          </div>
                          <span className="comparison-arrow" aria-hidden="true">→</span>
                          <div className="comparison-after">
                            <small>{item.fixed ? '유지됨' : item.changed ? '새 일정' : '유지됨'}</small>
                            <strong>{displayName(item.proposedDisplayName, item.proposedPlaceId, places)}</strong>
                            <span>{timeAndDuration(item.proposedStartTime, item.proposedDurationMinutes)}</span>
                          </div>
                        </li>
                      ))}
                    </ol>
                  </section>
                )
              })}
            </div>
          </section>
        )}

        {job && ['FAILED', 'STALE'].includes(job.status) && (
          <section className="editor-generation-state error" role="alert">
            <span>{job.status === 'STALE' ? '일정이 먼저 변경됐어요' : '새 일정을 준비하지 못했어요'}</span>
            <h3>{job.status === 'STALE' ? '최신 일정을 불러온 뒤 다시 범위를 선택해 주세요.' : '현재 일정은 그대로 유지되어 있어요.'}</h3>
            <p>{job.failureReason || '장소 후보나 이동 시간을 확인하지 못했습니다.'}</p>
          </section>
        )}

        {error && <div className="itinerary-editor-error" role="alert">{error}</div>}
      </div>

      <footer className="itinerary-editor-actions">
        {!job && <>
          <button className="secondary" type="button" onClick={requestClose}>취소</button>
          <button type="button" disabled={action !== null || (mode === 'PARTIAL' && lastItemId === null)} onClick={() => void createJob()}>
            {action === 'create' ? '요청하는 중…' : mode === 'FULL' ? '전체 일정 다시 만들기' : '선택한 구간 다시 만들기'}
          </button>
        </>}
        {job?.status === 'READY_FOR_REVIEW' && <>
          <button className="secondary" type="button" disabled={action !== null} onClick={() => void rejectJob()}>
            {action === 'reject' ? '처리 중…' : '기존 일정 유지'}
          </button>
          <button type="button" disabled={action !== null} onClick={() => void applyJob()}>
            {action === 'apply' ? '반영하는 중…' : '이 일정으로 바꾸기'}
          </button>
        </>}
        {job && job.status !== 'READY_FOR_REVIEW' && !['GENERATING'].includes(job.status) && (
          <button type="button" onClick={onClose}>일정으로 돌아가기</button>
        )}
      </footer>

      {showDiscardDialog && (
        <div className="editor-confirm-backdrop" role="presentation">
          <div ref={discardDialogRef} className="editor-confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="discard-heading" aria-describedby="discard-description">
            <h3 id="discard-heading">작성 중인 수정을 그만둘까요?</h3>
            <p id="discard-description">변경한 내용은 일정에 반영되지 않아요.</p>
            <div>
              <button type="button" autoFocus onClick={() => setShowDiscardDialog(false)}>계속 수정</button>
              <button className="danger-text" type="button" onClick={onClose}>변경 내용 버리고 나가기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function editorError(error: unknown) {
  if (!(error instanceof ApiError)) return '요청을 처리하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.'
  const messageByCode: Record<string, string> = {
    REGENERATION_ALREADY_ACTIVE: '이미 준비 중이거나 검토할 새 일정이 있어요.',
    REGENERATION_STALE_BASE: '일정이 먼저 변경됐어요. 최신 일정을 불러온 뒤 다시 선택해 주세요.',
    REGENERATION_INVALID_RANGE: '같은 날짜 안에서 이어진 장소를 다시 선택해 주세요.',
    REGENERATION_NO_REPLACEMENT: '바꿀 장소를 한 곳 이상 남겨 주세요.',
    REGENERATION_FIXED_ITEM_CONFLICT: '그대로 둘 장소의 시간과 새 이동 흐름이 맞지 않아요. 고정을 풀거나 범위를 바꿔 주세요.',
    REGENERATION_WINDOW_CLOSED: '이미 지난 일정은 다시 만들 수 없어요.',
    ROUTE_QUOTA_EXCEEDED: '오늘의 경로 확인 한도에 도달했어요. 현재 일정은 그대로 유지됩니다.',
  }
  return (error.code ? messageByCode[error.code] : undefined) ?? error.message
}

function displayName(name: string | null, placeId: string | null, places: ItineraryPlace[]) {
  return name || places.find((place) => place.placeId === placeId)?.title || '장소 정보 확인 중'
}

function timeAndDuration(time: string | null, duration: number | null) {
  if (!time || duration === null) return '새로 추가'
  return `${time.slice(0, 5)} · ${formatDuration(duration)}`
}

function formatReviewDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date(`${value}T00:00:00`))
}

function getFocusableElements(root: HTMLElement) {
  return Array.from(root.querySelectorAll<HTMLElement>(
    'button:not([disabled]), a[href], input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
  )).filter((element) => !element.hasAttribute('hidden') && element.getAttribute('aria-hidden') !== 'true')
}
