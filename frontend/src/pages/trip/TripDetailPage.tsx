import { useCallback, useEffect, useMemo, useState } from 'react'
import type { MouseEvent } from 'react'
import type { AuthUser } from '../../api/auth'
import { API_BASE_URL, ApiError } from '../../api/client'
import {
  getAiRequest,
  getItineraryPlaceViews,
  getLatestItineraryGeneration,
  getManualPrompt,
  getTripDetail,
  submitManualResponse,
  validateManualResponse,
} from '../../api/trips'
import type {
  AiItineraryDraft,
  GenerationStatus,
  ItineraryGenerationDetailResponse,
  ItineraryPlaceView,
  TripDetail,
  TripMember,
  TripPlanningProfile,
} from '../../api/trips'
import type { AiItineraryValidationReport } from '../../api/itineraryValidation'
import { connectTripRealtimeEvents, ITINERARY_GENERATION_STATUS_CHANGED } from '../../api/realtime'
import { AiItineraryValidationReportPanel } from './AiItineraryValidationReportPanel'
import './TripDetailPage.css'
import './TripWorkspacePortfolio.css'

type TripDetailPageProps = {
  accessToken: string
  tripId: string
  user: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'
type MobileWorkspacePane = 'SCHEDULE' | 'MAP' | 'ROOM'
type CollaborationView = 'CHAT' | 'VOTE'

type ItineraryPlace = {
  id: string
  day: number
  order: number
  title: string
  startTime: string
  duration: string
  latitude: number | null
  longitude: number | null
  locationLabel: string | null
  googleMapsUri: string | null
  resolved: boolean
  source: ItineraryPlaceView['createdSource']
}

type ManualTools = {
  aiRequestJson: string
  message: string
  prompt: string
  responseJson: string
  status: AsyncStatus
  validationReport: AiItineraryValidationReport | null
  onAiRequestLoad: () => void
  onPromptLoad: () => void
  onResponseChange: (value: string) => void
  onResponseValidate: () => void
  onResponseSubmit: () => void
}

const manualHandoffEnabled = import.meta.env.VITE_ENABLE_MANUAL_HANDOFF === 'true'
const itineraryGenerationEnabled = import.meta.env.VITE_ITINERARY_GENERATION_ENABLED === 'true'

export function TripDetailPage({
  accessToken,
  tripId,
  user,
  onBackToMain,
  onLogout,
}: TripDetailPageProps) {
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [placeViews, setPlaceViews] = useState<ItineraryPlaceView[]>([])
  const [placeViewsStatus, setPlaceViewsStatus] = useState<AsyncStatus>('loading')
  const [generation, setGeneration] = useState<ItineraryGenerationDetailResponse | null>(null)
  const [status, setStatus] = useState<AsyncStatus>('loading')
  const [errorMessage, setErrorMessage] = useState('')
  const [generationNotice, setGenerationNotice] = useState('')
  const [manualPrompt, setManualPrompt] = useState('')
  const [aiRequestJson, setAiRequestJson] = useState('')
  const [manualResponseJson, setManualResponseJson] = useState('')
  const [manualStatus, setManualStatus] = useState<AsyncStatus>('idle')
  const [manualMessage, setManualMessage] = useState('')
  const [validationReport, setValidationReport] = useState<AiItineraryValidationReport | null>(null)

  const loadTripData = useCallback(async () => {
    try {
      const tripResponse = await getTripDetail(accessToken, tripId)
      const generationResponse = itineraryGenerationEnabled
        ? await getLatestItineraryGeneration(accessToken, tripId)
        : null
      setTrip(tripResponse)
      setGeneration(generationResponse)
      setStatus('success')
      setErrorMessage('')

      if (tripResponse.itineraries.length === 0) {
        setPlaceViews([])
        setPlaceViewsStatus('success')
        return
      }

      setPlaceViewsStatus('loading')
      try {
        setPlaceViews(await getItineraryPlaceViews(accessToken, tripId))
        setPlaceViewsStatus('success')
      } catch {
        setPlaceViews([])
        setPlaceViewsStatus('error')
      }
    } catch (error: unknown) {
      setStatus('error')
      setErrorMessage(errorMessageFrom(error))
    }
  }, [accessToken, tripId])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadTripData()
    }, 0)
    return () => window.clearTimeout(timeoutId)
  }, [loadTripData])

  useEffect(() => {
    if (!itineraryGenerationEnabled) {
      return undefined
    }

    let active = true

    async function refetchGeneration() {
      try {
        const response = await getLatestItineraryGeneration(accessToken, tripId)
        if (active) {
          setGeneration(response)
        }
      } catch (error: unknown) {
        if (active) {
          setGenerationNotice(errorMessageFrom(error))
        }
      }
    }

    const connection = connectTripRealtimeEvents({
      accessToken,
      tripId,
      onConnect: () => {
        setGenerationNotice('실시간 상태 업데이트에 연결되었습니다.')
        void refetchGeneration()
      },
      onError: () => {
        if (active) {
          setGenerationNotice('실시간 연결이 지연되고 있습니다. 새로고침하면 최신 상태를 확인할 수 있습니다.')
        }
      },
      onEvent: (event) => {
        if (event.type !== ITINERARY_GENERATION_STATUS_CHANGED) {
          return
        }
        setGeneration((current) => current?.generationId === event.payload.generationId
          ? {
              ...current,
              status: event.payload.status,
              candidateCount: event.payload.candidateCount,
              failureReason: event.payload.failureReason,
              updatedAt: event.payload.updatedAt,
            }
          : current)
        setGenerationNotice(generationEventMessage(event.payload.status))
        if (event.payload.status === 'COMPLETED') {
          void loadTripData()
        } else {
          void refetchGeneration()
        }
      },
    })

    return () => {
      active = false
      connection.disconnect()
    }
  }, [accessToken, loadTripData, tripId])

  function applyManualError(error: unknown) {
    setManualStatus('error')
    setManualMessage(errorMessageFrom(error))
    if (error instanceof ApiError && error.validationReport) {
      setValidationReport(error.validationReport)
    }
  }

  function parseManualResponse() {
    try {
      return JSON.parse(manualResponseJson) as AiItineraryDraft
    } catch {
      setManualStatus('error')
      setValidationReport(null)
      setManualMessage('AI 응답 JSON 형식이 올바르지 않습니다.')
      return null
    }
  }

  async function handleLoadManualPrompt() {
    if (generation?.status !== 'READY_FOR_PLANNING') return
    setManualStatus('loading')
    setManualMessage('')
    try {
      setManualPrompt(await getManualPrompt(accessToken, tripId, generation.generationId))
      setManualStatus('success')
      setManualMessage('수동 AI 요청용 프롬프트를 불러왔습니다.')
    } catch (error: unknown) {
      applyManualError(error)
    }
  }

  async function handleLoadAiRequest() {
    if (generation?.status !== 'READY_FOR_PLANNING') return
    setManualStatus('loading')
    setManualMessage('')
    try {
      const response = await getAiRequest(accessToken, tripId, generation.generationId)
      setAiRequestJson(JSON.stringify(response, null, 2))
      setManualStatus('success')
      setManualMessage('AI 요청 JSON을 불러왔습니다.')
    } catch (error: unknown) {
      applyManualError(error)
    }
  }

  async function handleValidateManualResponse() {
    if (generation?.status !== 'READY_FOR_PLANNING') return
    const parsed = parseManualResponse()
    if (!parsed) return
    setManualStatus('loading')
    setManualMessage('')
    try {
      const report = await validateManualResponse(accessToken, tripId, generation.generationId, parsed)
      setValidationReport(report)
      setManualStatus(report.errors.length ? 'error' : 'success')
      setManualMessage(validationReportMessage(report))
    } catch (error: unknown) {
      applyManualError(error)
    }
  }

  async function handleSubmitManualResponse() {
    if (generation?.status !== 'READY_FOR_PLANNING') return
    const parsed = parseManualResponse()
    if (!parsed) return
    setManualStatus('loading')
    setManualMessage('')
    try {
      setGeneration(await submitManualResponse(accessToken, tripId, generation.generationId, parsed))
      setManualStatus('success')
      setValidationReport(null)
      setManualMessage('검증된 일정 응답을 저장했습니다.')
      await loadTripData()
    } catch (error: unknown) {
      applyManualError(error)
    }
  }

  if (status === 'loading') {
    return (
      <main className="trip-detail-page">
        <section className="trip-detail-state-card" aria-live="polite">
          <span className="state-spinner" aria-hidden="true" />
          <p>여행과 일정 정보를 불러오고 있습니다.</p>
        </section>
      </main>
    )
  }

  if (status === 'error' || !trip) {
    return (
      <main className="trip-detail-page">
        <section className="trip-detail-state-card error" role="alert">
          <span className="state-code">불러오기 실패</span>
          <h1>여행 정보를 확인하지 못했습니다.</h1>
          <p>{errorMessage}</p>
          <div className="state-actions">
            <button type="button" onClick={() => {
              setStatus('loading')
              setErrorMessage('')
              void loadTripData()
            }}>다시 시도</button>
            <button className="secondary" type="button" onClick={onBackToMain}>메인으로 돌아가기</button>
          </div>
        </section>
      </main>
    )
  }

  const manualTools: ManualTools | null = manualHandoffEnabled ? {
    aiRequestJson,
    message: manualMessage,
    prompt: manualPrompt,
    responseJson: manualResponseJson,
    status: manualStatus,
    validationReport,
    onAiRequestLoad: handleLoadAiRequest,
    onPromptLoad: handleLoadManualPrompt,
    onResponseChange: (value) => {
      setManualResponseJson(value)
      setValidationReport(null)
    },
    onResponseValidate: handleValidateManualResponse,
    onResponseSubmit: handleSubmitManualResponse,
  } : null

  return (
    <main className="trip-detail-page">
      <a className="trip-detail-skip-link" href="#trip-detail-workspace">일정으로 바로가기</a>
      <TripWorkspace
        currentUser={user}
        generation={generation}
        generationNotice={generationNotice}
        manualTools={manualTools}
        onBackToMain={onBackToMain}
        onLogout={onLogout}
        onRefresh={() => void loadTripData()}
        placeViews={placeViews}
        placeViewsStatus={placeViewsStatus}
        trip={trip}
      />
    </main>
  )
}

function TripWorkspace({
  trip,
  placeViews,
  placeViewsStatus,
  generation,
  generationNotice,
  currentUser,
  manualTools,
  onBackToMain,
  onLogout,
  onRefresh,
}: {
  trip: TripDetail
  placeViews: ItineraryPlaceView[]
  placeViewsStatus: AsyncStatus
  generation: ItineraryGenerationDetailResponse | null
  generationNotice: string
  currentUser: AuthUser | null
  manualTools: ManualTools | null
  onBackToMain: () => void
  onLogout: () => void
  onRefresh: () => void
}) {
  const itinerary = trip.itineraries[0] ?? null
  const days = itinerary?.days.map((day) => day.day) ?? buildTripDayNumbers(trip.startDate, trip.endDate)
  const firstDay = days[0] ?? 1
  const [activeDay, setActiveDay] = useState(firstDay)
  const [selectedPlaceId, setSelectedPlaceId] = useState('')
  const [mobilePane, setMobilePane] = useState<MobileWorkspacePane>('SCHEDULE')
  const resolvedDay = days.includes(activeDay) ? activeDay : firstDay
  const storedPlaces = useMemo(() => itinerary?.days.flatMap((day) => day.items.map((item) => ({
    id: item.id.toString(),
    day: day.day,
    order: item.sequence,
    title: `저장된 장소 ${item.sequence}`,
    startTime: item.startTime.slice(0, 5),
    duration: formatDuration(item.durationMinutes),
    latitude: null,
    longitude: null,
    locationLabel: null,
    googleMapsUri: null,
    resolved: false,
    source: item.createdSource,
  }))) ?? [], [itinerary])
  const places = useMemo(() => {
    const resolvedPlaces = new Map(placeViews.map((item) => [item.itemId.toString(), toItineraryPlace(item)]))
    return storedPlaces.map((place) => resolvedPlaces.get(place.id) ?? place)
  }, [placeViews, storedPlaces])
  const activePlaces = useMemo(
    () => places.filter((place) => place.day === resolvedDay).sort((left, right) => left.order - right.order),
    [places, resolvedDay],
  )
  const selectedPlace = activePlaces.find((place) => place.id === selectedPlaceId) ?? activePlaces[0] ?? null
  const activeDate = itinerary?.days.find((day) => day.day === resolvedDay)?.date
    ?? dateForTripDay(trip.startDate, resolvedDay)
  const showGenerationProgress = generation && generation.status !== 'COMPLETED'

  function handleDayChange(day: number) {
    setActiveDay(day)
    setSelectedPlaceId(places.find((place) => place.day === day)?.id ?? '')
  }

  return (
    <section className="trip-workspace" id="trip-detail-workspace" aria-label="여행 상세 일정" tabIndex={-1}>
      <PlanningHeader
        trip={trip}
        members={trip.members}
        profile={trip.planningProfile}
        dayCount={days.length}
        placeCount={places.length}
        onBackToMain={onBackToMain}
        onLogout={onLogout}
        onRefresh={onRefresh}
      />
      {showGenerationProgress && (
        <GenerationStatusPanel generation={generation} hasItinerary={Boolean(itinerary)} notice={generationNotice} onRefresh={onRefresh} />
      )}
      {!itinerary && <WorkspaceSetupNotice generation={generation} />}
      <nav className="planning-mobile-switcher" aria-label="여행 상세 화면 선택">
        {([
          ['SCHEDULE', '일정'],
          ['MAP', '지도'],
          ['ROOM', '여행방'],
        ] as Array<[MobileWorkspacePane, string]>).map(([pane, label]) => (
          <button
            aria-pressed={mobilePane === pane}
            className={mobilePane === pane ? 'active' : ''}
            key={pane}
            type="button"
            onClick={() => setMobilePane(pane)}
          >{label}</button>
        ))}
      </nav>
      <div className="planning-layout">
        <ItinerarySchedule
          activeDate={activeDate}
          activeDay={resolvedDay}
          className={mobilePane === 'SCHEDULE' ? 'mobile-active' : ''}
          days={days}
          places={activePlaces}
          placesStatus={placeViewsStatus}
          selectedPlaceId={selectedPlace?.id ?? ''}
          onDayChange={handleDayChange}
          onSelectPlace={setSelectedPlaceId}
        />
        <div className={`planning-map-column ${mobilePane === 'MAP' ? 'mobile-active' : ''}`}>
          <TripMapCanvas
            activeDay={resolvedDay}
            activeDate={activeDate}
            destination={trip.destinationInfo.displayName || trip.destination}
            places={activePlaces}
            selectedPlace={selectedPlace}
            selectedPlaceId={selectedPlace?.id ?? ''}
            onSelectPlace={setSelectedPlaceId}
          />
        </div>
        <TripCollaborationPanel
          className={mobilePane === 'ROOM' ? 'mobile-active' : ''}
          members={trip.members}
          currentUser={currentUser}
          activeDay={resolvedDay}
          selectedPlace={selectedPlace}
        />
      </div>
      {manualTools && generation && <ManualHandoffPanel generation={generation} tools={manualTools} />}
    </section>
  )
}

function PlanningHeader({ trip, members, profile, dayCount, placeCount, onBackToMain, onLogout, onRefresh }: {
  trip: TripDetail
  members: TripMember[]
  profile: TripPlanningProfile | null
  dayCount: number
  placeCount: number
  onBackToMain: () => void
  onLogout: () => void
  onRefresh: () => void
}) {
  const visibleMembers = members.slice(0, 5)
  const hiddenMemberCount = Math.max(0, members.length - visibleMembers.length)

  return (
    <header className="planning-header">
      <div className="planning-header-main">
        <a className="icon-back-button" href="/main" onClick={(event) => handleSpaNavigation(event, onBackToMain)} aria-label="여행 목록으로 돌아가기">
          <span aria-hidden="true">←</span>
        </a>
        <img className="planning-brand" src="/brand/planmate-lockup.svg" alt="PlanMate" width="126" height="38" fetchPriority="high" />
        <div className="planning-title-block">
          <p className="room-title-label">{trip.destinationInfo.displayName || trip.destination}</p>
          <h1>{trip.title}</h1>
          <div className="planning-title-meta">
            <span>{formatDateRange(trip.startDate, trip.endDate)}</span>
            <span>{tripCountdownLabel(trip.startDate, trip.endDate)}</span>
            <span>{dayCount}일 · {placeCount}곳</span>
          </div>
        </div>
      </div>
      <div className="planning-header-actions">
        <details className="member-roster">
          <summary aria-label={`참여자 ${members.length}명 보기`}>
            <span className="member-avatar-stack" aria-hidden="true">
              {visibleMembers.map((member) => <MemberAvatar member={member} key={member.userId} />)}
              {hiddenMemberCount > 0 && <span className="member-avatar fallback extra">+{hiddenMemberCount}</span>}
            </span>
            <span className="member-total">{members.length}명</span>
          </summary>
          <div className="member-roster-popover">
            <div className="member-roster-title"><strong>함께하는 사람</strong><span>{members.length}명</span></div>
            <ul>
              {members.map((member) => (
                <li key={member.userId}>
                  <MemberAvatar member={member} />
                  <span><strong>{member.nickname}</strong><small>{member.role === 'OWNER' ? '방장' : '참여자'}</small></span>
                </li>
              ))}
            </ul>
          </div>
        </details>
        {profile && (
          <details className="trip-condition-disclosure">
            <summary>여행 조건</summary>
            <div className="trip-condition-popover"><PlanningProfileSummary profile={profile} /></div>
          </details>
        )}
        <button className="header-icon-button refresh" type="button" onClick={onRefresh} aria-label="일정 새로고침" title="일정 새로고침">↻</button>
        <details className="account-menu">
          <summary aria-label="계정 메뉴">•••</summary>
          <div className="account-menu-popover"><button type="button" onClick={onLogout}>로그아웃</button></div>
        </details>
      </div>
    </header>
  )
}

function GenerationStatusPanel({ generation, hasItinerary, notice, onRefresh }: {
  generation: ItineraryGenerationDetailResponse | null
  hasItinerary: boolean
  notice: string
  onRefresh: () => void
}) {
  const view = generationPresentation(generation, hasItinerary)
  const progressIndex = generation ? generationProgressIndex(generation.status) : -1
  const steps = generation?.status === 'FAILED'
    ? ['요청 접수', '후보 수집', '생성 실패', '저장 완료']
    : ['요청 접수', '후보 수집', '일정 검증', '저장 완료']
  return (
    <section className={`generation-status-panel ${view.tone}`} aria-live="polite">
      <div className="generation-status-copy">
        <span className="section-kicker">{view.eyebrow}</span>
        <h2>{view.title}</h2>
        <p>{view.description}</p>
        {generation && generation.candidateCount > 0 && <small>수집된 장소 후보 {generation.candidateCount}개</small>}
        {notice && <small className="generation-notice">{notice}</small>}
      </div>
      <ol className="generation-progress" aria-label="일정 생성 진행 단계">
        {steps.map((step, index) => (
          <li className={index < progressIndex ? 'complete' : index === progressIndex ? generation?.status === 'FAILED' ? 'failed' : 'active' : ''} key={step}>
            <span>{index + 1}</span><strong>{step}</strong>
          </li>
        ))}
      </ol>
      <button className="refresh-button" type="button" onClick={onRefresh}>최신 상태 확인</button>
    </section>
  )
}

function PlanningProfileSummary({ profile }: { profile: TripPlanningProfile }) {
  const accommodation = profile.accommodationMode === 'PLACE_SEARCH' ? profile.accommodationName ?? '선택된 숙소' : '숙소 미정'
  const accommodationDetail = profile.accommodationMode === 'PLACE_SEARCH'
    ? profile.accommodationFormattedAddress ?? 'Google Places에서 선택한 숙소'
    : accommodationAreaLabel(profile.accommodationArea)
  return (
    <section className="planning-profile-summary" aria-label="저장된 여행 조건">
      <ProfileItem label="여행 구성" value={`${companionLabel(profile.companionType)} · ${profile.companionCount}명`} detail={`${travelPaceLabel(profile.travelPace)} 일정`} />
      <ProfileItem label="하루 일정 시간" value={`${formatTime(profile.dailyStartTime)} ~ ${formatTime(profile.dailyEndTime)}`} detail={`${transportLabel(profile.primaryTransportMode)} 중심 이동`} />
      <ProfileItem label="숙소" value={accommodation} detail={accommodationDetail} />
      <ProfileItem label="관심사" value={`${profile.interests.length}개 선택`} detail={profile.interests.map(interestLabel).join(' · ') || '선택한 관심사 없음'} />
    </section>
  )
}

function ProfileItem({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <article><span>{label}</span><strong>{value}</strong><p>{detail}</p></article>
}

function ItinerarySchedule({ activeDate, activeDay, className, days, places, placesStatus, selectedPlaceId, onDayChange, onSelectPlace }: {
  activeDate: string | null
  activeDay: number
  className?: string
  days: number[]
  places: ItineraryPlace[]
  placesStatus: AsyncStatus
  selectedPlaceId: string
  onDayChange: (day: number) => void
  onSelectPlace: (placeId: string) => void
}) {
  return (
    <section className={`itinerary-schedule ${className ?? ''}`} aria-label={`${activeDay}일차 일정`}>
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

function PlaceDetailPanel({ place }: { place: ItineraryPlace | null }) {
  if (!place) {
    return <aside className="place-detail-panel empty"><span className="section-kicker">장소 정보</span><h2>장소를 선택해 주세요.</h2><p>장소를 선택하면 방문 시간과 위치를 확인할 수 있습니다.</p></aside>
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

function DetailItem({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>
}

function TripMapCanvas({ activeDay, activeDate, destination, places, selectedPlace, selectedPlaceId, onSelectPlace }: {
  activeDay: number
  activeDate: string | null
  destination: string
  places: ItineraryPlace[]
  selectedPlace: ItineraryPlace | null
  selectedPlaceId: string
  onSelectPlace: (placeId: string) => void
}) {
  const positionedPlaces = useMemo(() => positionMapPlaces(places), [places])

  return (
    <section className="trip-map-panel" aria-label={`${activeDay}일차 장소 지도`}>
      <div className="trip-map-heading">
        <div><span className="section-kicker">{destination}</span><h2>{activeDay}일차 지도</h2></div>
        <div className="map-heading-meta"><span>{activeDate ? formatFullDate(activeDate) : ''}</span><small>지도 UI 미리보기</small></div>
      </div>
      <div className="trip-map-canvas">
        <div className="map-preview-notice"><i aria-hidden="true" /><span><strong>장소 위치 미리보기</strong>경로와 이동 시간은 지도 API 연결 후 표시됩니다.</span></div>
        <span className="map-water-shape" aria-hidden="true" />
        <span className="map-land-shape land-one" aria-hidden="true" />
        <span className="map-land-shape land-two" aria-hidden="true" />
        <span className="map-road road-one" aria-hidden="true" />
        <span className="map-road road-two" aria-hidden="true" />
        <span className="map-road road-three" aria-hidden="true" />
        {positionedPlaces.length ? positionedPlaces.map((place) => (
          <button
            className={`trip-map-pin ${place.id === selectedPlaceId ? 'active' : ''}`}
            key={place.id}
            style={{ left: `${place.x}%`, top: `${place.y}%` }}
            type="button"
            aria-label={`${place.order}번째 장소 ${place.title}`}
            aria-pressed={place.id === selectedPlaceId}
            onClick={() => onSelectPlace(place.id)}
          >
            <span><i>{place.order}</i></span>
            <strong>{place.title}</strong>
          </button>
        )) : (
          <div className="trip-map-empty"><strong>표시할 장소가 없습니다.</strong><span>다른 날짜를 선택해 주세요.</span></div>
        )}
        {selectedPlace && <PlaceDetailPanel place={selectedPlace} />}
      </div>
    </section>
  )
}

function TripCollaborationPanel({ className, members, currentUser, activeDay, selectedPlace }: {
  className?: string
  members: TripMember[]
  currentUser: AuthUser | null
  activeDay: number
  selectedPlace: ItineraryPlace | null
}) {
  const [view, setView] = useState<CollaborationView>('CHAT')
  const firstMember = members[0]?.nickname ?? '여행 멤버'
  const secondMember = members[1]?.nickname ?? '동행자'
  const currentNickname = currentUser?.nickname ?? members[2]?.nickname ?? '나'

  return (
    <aside className={`trip-chat-panel ${className ?? ''}`} aria-label="여행방 협업">
      <div className="trip-chat-heading">
        <div><span className="section-kicker">함께 정하는 여행</span><h2>여행방</h2></div>
        <span className="room-connection-state"><i aria-hidden="true" />연결 전</span>
      </div>
      <div className="collaboration-tabs" role="tablist" aria-label="여행방 기능">
        <button aria-selected={view === 'CHAT'} className={view === 'CHAT' ? 'active' : ''} role="tab" type="button" onClick={() => setView('CHAT')}>대화</button>
        <button aria-selected={view === 'VOTE'} className={view === 'VOTE' ? 'active' : ''} role="tab" type="button" onClick={() => setView('VOTE')}>투표</button>
      </div>
      {view === 'CHAT' ? (
        <>
          <div className="collaboration-preview-label">화면 구성 예시 · 채팅 연결 전</div>
          <div className="trip-chat-preview" aria-label="채팅 화면 구성 예시">
            <div className="chat-date-divider"><span>{activeDay}일차 일정 이야기</span></div>
            <article className="chat-message-row">
              <span className="preview-avatar blue" aria-hidden="true">{firstMember.slice(0, 1)}</span>
              <div><strong>{firstMember}</strong><p>{activeDay}일차 일정 확인했어요. 이동 중간에 쉬는 시간이 있으면 좋겠어요.</p><time>오후 7:12</time></div>
            </article>
            <article className="chat-message-row">
              <span className="preview-avatar sand" aria-hidden="true">{secondMember.slice(0, 1)}</span>
              <div><strong>{secondMember}</strong><p>{selectedPlace?.title ?? '오후 일정'} 다음에 카페를 넣어보는 건 어때요?</p><time>오후 7:14</time></div>
            </article>
            <article className="chat-message-row mine">
              <div><p>좋아요. 후보를 같이 보고 정해요.</p><time>오후 7:15</time></div>
              <span className="preview-avatar navy" aria-hidden="true">{currentNickname.slice(0, 1)}</span>
            </article>
          </div>
          <div className="trip-chat-composer" aria-label="대화 기능 연결 전">
            <textarea disabled aria-label="메시지 입력" placeholder="메시지를 입력하세요…" />
            <button type="button" disabled aria-label="메시지 보내기">↑</button>
          </div>
        </>
      ) : (
        <div className="trip-vote-preview" aria-label="투표 화면 구성 예시">
          <div className="collaboration-preview-label">화면 구성 예시 · 투표 연결 전</div>
          <span className="vote-author">{firstMember} 님이 제안</span>
          <h3>{activeDay}일차에 카페 시간을 넣을까요?</h3>
          <p>{selectedPlace?.title ?? '선택한 장소'} 다음 일정을 조금 여유롭게 조정하는 제안입니다.</p>
          <div className="vote-preview-options" aria-hidden="true">
            <span><i style={{ width: '72%' }} /><strong>좋아요</strong><small>3</small></span>
            <span><i style={{ width: '28%' }} /><strong>그대로 갈게요</strong><small>1</small></span>
          </div>
          <small className="vote-preview-footnote">투표 기능이 연결되면 참여와 결과 반영을 사용할 수 있습니다.</small>
        </div>
      )}
    </aside>
  )
}

function WorkspaceSetupNotice({ generation }: { generation: ItineraryGenerationDetailResponse | null }) {
  const view = generation ? generationPresentation(generation, false) : null
  return (
    <section className="workspace-setup-notice" aria-live="polite">
      <strong>{view?.eyebrow ?? '일정 연결 전'}</strong>
      <p>{view?.description ?? '일정이 저장되면 방문 순서와 장소 위치가 아래 화면에 바로 표시됩니다.'}</p>
    </section>
  )
}

function ManualHandoffPanel({ generation, tools }: { generation: ItineraryGenerationDetailResponse; tools: ManualTools }) {
  const disabled = generation.status !== 'READY_FOR_PLANNING' || tools.status === 'loading'
  return (
    <details className="manual-handoff-panel">
      <summary>개발자용 수동 AI 검증 도구</summary>
      <div className="manual-handoff-content">
        <p>일반 사용자 화면에서는 숨겨지며, 수동 handoff 방식의 AI 응답을 검증하고 저장할 때만 사용합니다.</p>
        <div className="manual-handoff-actions"><button type="button" onClick={tools.onPromptLoad} disabled={disabled}>프롬프트 불러오기</button><button type="button" onClick={tools.onAiRequestLoad} disabled={disabled}>AI 요청 JSON 불러오기</button></div>
        {tools.message && <p className={`manual-message ${tools.status}`}>{tools.message}</p>}
        {tools.prompt && <label>프롬프트<textarea readOnly value={tools.prompt} /></label>}
        {tools.aiRequestJson && <label>AI 요청 JSON<textarea readOnly value={tools.aiRequestJson} /></label>}
        <label>AI 응답 JSON<textarea autoComplete="off" name="manual-ai-response" placeholder="검증할 AI 응답 JSON을 입력하세요…" spellCheck={false} value={tools.responseJson} onChange={(event) => tools.onResponseChange(event.target.value)} /></label>
        <AiItineraryValidationReportPanel report={tools.validationReport} />
        <div className="manual-handoff-actions"><button type="button" onClick={tools.onResponseValidate} disabled={disabled || !tools.responseJson.trim()}>응답 검증</button><button className="primary" type="button" onClick={tools.onResponseSubmit} disabled={disabled || !tools.responseJson.trim()}>검증 후 일정 저장</button></div>
      </div>
    </details>
  )
}

function MemberAvatar({ member }: { member: TripMember }) {
  return member.profileImageUrl
    ? <img className="member-avatar" src={resolveBackendAssetUrl(member.profileImageUrl)} alt={`${member.nickname} 프로필`} width="38" height="38" />
    : <span className="member-avatar fallback" aria-hidden="true">{member.nickname.slice(0, 1)}</span>
}

function toItineraryPlace(item: ItineraryPlaceView): ItineraryPlace {
  return {
    id: item.itemId.toString(),
    day: item.dayNo,
    order: item.sequence,
    title: item.display.displayName ?? item.display.fallbackMessage ?? '장소 정보를 불러오지 못했습니다',
    startTime: item.startTime.slice(0, 5),
    duration: formatDuration(item.durationMinutes),
    latitude: item.display.location?.latitude ?? null,
    longitude: item.display.location?.longitude ?? null,
    locationLabel: item.display.location ? `${item.display.location.latitude.toFixed(5)}, ${item.display.location.longitude.toFixed(5)}` : null,
    googleMapsUri: item.display.googleMapsUri,
    resolved: item.display.resolved,
    source: item.createdSource,
  }
}

function positionMapPlaces(places: ItineraryPlace[]) {
  const located = places.filter((place) => place.latitude !== null && place.longitude !== null)
  if (located.length === 0) {
    return places.map((place, index) => ({
      ...place,
      hasCoordinates: false,
      x: 24 + ((index * 23) % 58),
      y: 24 + ((index * 19) % 54),
    }))
  }

  const latitudes = located.map((place) => place.latitude as number)
  const longitudes = located.map((place) => place.longitude as number)
  const minLatitude = Math.min(...latitudes)
  const maxLatitude = Math.max(...latitudes)
  const minLongitude = Math.min(...longitudes)
  const maxLongitude = Math.max(...longitudes)
  const latitudeSpan = Math.max(maxLatitude - minLatitude, 0.001)
  const longitudeSpan = Math.max(maxLongitude - minLongitude, 0.001)

  return places.map((place, index) => {
    const hasCoordinates = place.latitude !== null && place.longitude !== null
    const fallbackX = 24 + ((index * 23) % 58)
    const fallbackY = 24 + ((index * 19) % 54)
    return {
      ...place,
      hasCoordinates,
      x: hasCoordinates ? 12 + (((place.longitude as number) - minLongitude) / longitudeSpan) * 76 : fallbackX,
      y: hasCoordinates ? 12 + ((maxLatitude - (place.latitude as number)) / latitudeSpan) * 72 : fallbackY,
    }
  })
}

function buildTripDayNumbers(startDateValue: string, endDateValue: string) {
  const startDate = new Date(`${startDateValue}T00:00:00`)
  const endDate = new Date(`${endDateValue}T00:00:00`)
  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime()) || endDate < startDate) return [1]
  const dayCount = Math.max(1, Math.round((endDate.getTime() - startDate.getTime()) / 86_400_000) + 1)
  return Array.from({ length: dayCount }, (_, index) => index + 1)
}

function dateForTripDay(startDateValue: string, day: number) {
  const date = new Date(`${startDateValue}T00:00:00`)
  date.setDate(date.getDate() + day - 1)
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const dateOfMonth = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${dateOfMonth}`
}

function shiftDate(value: string, dayOffset: number) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + dayOffset)
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const dateOfMonth = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${dateOfMonth}`
}

function resolveBackendAssetUrl(value: string) {
  if (/^https?:\/\//i.test(value) || value.startsWith('data:')) {
    return value
  }
  return `${API_BASE_URL}${value.startsWith('/') ? value : `/${value}`}`
}

function generationPresentation(generation: ItineraryGenerationDetailResponse | null, hasItinerary: boolean) {
  if (generation?.status === 'COMPLETED' || (!generation && hasItinerary)) return { eyebrow: '일정 생성 완료', title: '검증을 통과한 일정이 저장되었습니다.', description: '날짜별 장소와 방문 시간을 선택해 상세 정보를 확인할 수 있습니다.', tone: 'completed' }
  if (!generation) return { eyebrow: '일정 생성 전', title: '아직 생성된 일정이 없습니다.', description: '여행 생성 화면에서 일정 생성을 요청하면 진행 상태가 이곳에 표시됩니다.', tone: 'idle' }
  const previousItineraryNotice = hasItinerary ? ' 기존에 저장된 일정은 아래에서 계속 확인할 수 있습니다.' : ''
  switch (generation.status) {
    case 'CREATED': return { eyebrow: '새 일정 요청 접수', title: '일정 생성 요청을 안전하게 저장했습니다.', description: `비동기 Worker가 요청을 가져갈 때까지 잠시 기다려 주세요.${previousItineraryNotice}`, tone: 'processing' }
    case 'COLLECTING_CANDIDATES': return { eyebrow: '새 장소 후보 수집 중', title: '여행 조건에 맞는 실제 장소를 찾고 있습니다.', description: `관심사와 이동 조건을 기준으로 일정에 사용할 후보를 정리하고 있습니다.${previousItineraryNotice}`, tone: 'processing' }
    case 'READY_FOR_PLANNING': return { eyebrow: '새 후보 수집 완료', title: '검증 가능한 일정 초안을 준비할 수 있습니다.', description: `장소 후보 수집을 마쳤으며, AI 응답을 서버 규칙으로 검증하는 단계입니다.${previousItineraryNotice}`, tone: 'ready' }
    case 'FAILED': return { eyebrow: '새 일정 생성 실패', title: '이번 요청을 완료하지 못했습니다.', description: `${generation.failureReason ?? '실패 원인을 확인한 뒤 일정 생성을 다시 요청해 주세요.'}${previousItineraryNotice}`, tone: 'failed' }
    default: return { eyebrow: '일정 생성 중', title: '일정을 준비하고 있습니다.', description: '최신 상태를 확인해 주세요.', tone: 'processing' }
  }
}

function generationProgressIndex(status: GenerationStatus) {
  return { CREATED: 0, COLLECTING_CANDIDATES: 1, READY_FOR_PLANNING: 2, COMPLETED: 3, FAILED: 2 }[status]
}

function generationEventMessage(status: GenerationStatus) {
  return { CREATED: '일정 생성 요청이 접수되었습니다.', COLLECTING_CANDIDATES: '여행 조건에 맞는 장소 후보를 수집하고 있습니다.', READY_FOR_PLANNING: '장소 후보 수집을 마쳤습니다.', COMPLETED: '검증된 일정이 저장되었습니다.', FAILED: '일정 생성에 실패했습니다.' }[status]
}

function formatDuration(minutes: number) {
  if (minutes < 60) return `${minutes}분`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours}시간 ${rest}분` : `${hours}시간`
}

function formatDateRange(start: string, end: string) {
  return `${formatShortDate(start)} – ${formatShortDate(end)}`
}

function formatShortDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric' }).format(new Date(`${value}T00:00:00`))
}

function formatFullDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(new Date(`${value}T00:00:00`))
}

function formatDayTabDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { month: 'numeric', day: 'numeric' }).format(new Date(`${value}T00:00:00`))
}

function tripCountdownLabel(startDateValue: string, endDateValue: string) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const start = new Date(`${startDateValue}T00:00:00`)
  const end = new Date(`${endDateValue}T23:59:59`)
  const daysUntilStart = Math.ceil((start.getTime() - today.getTime()) / 86_400_000)
  if (daysUntilStart > 0) return `출발 D-${daysUntilStart}`
  if (end >= today) return '여행 중'
  return '지난 여행'
}

function formatTime(value: string) {
  return value.slice(0, 5)
}

function accommodationAreaLabel(value: TripPlanningProfile['accommodationArea']) {
  switch (value) {
    case 'TOURIST_CENTER': return '중심 관광지 근처'
    case 'TRANSIT': return '대중교통이 편한 곳'
    case 'QUIET': return '조용한 지역'
    case 'ANYWHERE': return '지역 상관없음'
    default: return '선호 숙소 지역 없음'
  }
}

function companionLabel(value: TripPlanningProfile['companionType']) {
  return { SOLO: '혼자', COUPLE: '연인', FRIENDS: '친구', FAMILY: '가족', PARENTS: '부모님', COWORKERS: '동료', OTHER: '기타' }[value]
}

function travelPaceLabel(value: TripPlanningProfile['travelPace']) {
  return { RELAXED: '여유로운', BALANCED: '균형 잡힌', PACKED: '알찬' }[value]
}

function transportLabel(value: TripPlanningProfile['primaryTransportMode']) {
  return { WALK: '도보', PUBLIC_TRANSIT: '대중교통', RENTAL_CAR: '렌터카', TAXI: '택시', BIKE: '자전거', TOUR: '투어 이동' }[value]
}

function interestLabel(value: TripPlanningProfile['interests'][number]) {
  return { FOOD: '맛집', SIGHTSEEING: '관광', CAFE: '카페', CULTURE: '문화', NATURE: '자연', SHOPPING: '쇼핑', PHOTO: '사진', NIGHT_VIEW: '야경', ACTIVITY: '액티비티', REST: '휴식', ART: '예술', THEME_PARK: '테마파크', LOCAL: '로컬 경험' }[value]
}

function validationReportMessage(report: AiItineraryValidationReport) {
  if (report.errors.length) return `저장 전에 수정해야 할 오류 ${report.errors.length}건을 찾았습니다.`
  const advisoryCount = report.warnings.length + report.unverifiedConditions.length
  return advisoryCount ? `확인이 필요한 검증 항목 ${advisoryCount}건이 있습니다.` : '검증 가능한 모든 조건을 통과했습니다.'
}

function errorMessageFrom(error: unknown) {
  return error instanceof ApiError ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

function handleSpaNavigation(event: MouseEvent<HTMLAnchorElement>, navigate: () => void) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return
  }
  event.preventDefault()
  navigate()
}
