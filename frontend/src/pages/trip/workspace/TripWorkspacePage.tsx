import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { AuthUser } from '../../../api/auth'
import { ApiError } from '../../../api/client'
import {
  getItineraryPlaceViews,
  getLatestItineraryGeneration,
  getTripDetail,
} from '../../../api/trips'
import type {
  ItineraryGenerationDetailResponse,
  ItineraryPlaceView,
  TripDetail,
} from '../../../api/trips'
import { CHAT_MESSAGE_DELETED, CHAT_MESSAGE_SENT, CHAT_REACTION_CHANGED, CHAT_TYPING_UPDATED, connectTripRealtimeEvents, ITINERARY_GENERATION_STATUS_CHANGED, ITINERARY_REGENERATION_CHANGED, ITINERARY_REVISION_APPLIED, MEMBER_PRESENCE_CHANGED, MEMBERSHIP_CHANGED, VOTE_CLOSED, VOTE_OPENED } from '../../../api/realtime'
import type { ChatMessageChangedPayload, ChatMessageSentPayload, ChatTypingChangedPayload, TripRealtimeConnection } from '../../../api/realtime'
import { getChatUnreadCount } from '../../../api/chat'
import { getTripPresence } from '../../../api/presence'
import type { PresenceStatus } from '../../../api/presence'
import { getDayRoute } from '../../../api/routes'
import type { DayRoute } from '../../../api/routes'
import { getLatestItineraryRegeneration } from '../../../api/regenerations'
import type { ItineraryRegeneration } from '../../../api/regenerations'
import { WorkspaceHeader } from './components/WorkspaceHeader'
import { GenerationStatusPanel, WorkspaceSetupNotice } from './components/GenerationStatusPanel'
import { WorkspacePaneSwitcher } from './components/WorkspacePaneSwitcher'
import { ItinerarySchedule } from './schedule/ItinerarySchedule'
import { TripMapCanvas } from './map/TripMapCanvas'
import { RoomPanel } from './room/RoomPanel'
import { ItineraryEditor } from './editor/ItineraryEditor'
import { generationEventMessage, isKnownWorkspaceEvent } from './workspaceEvents'
import { buildTripDayNumbers, dateForTripDay } from './workspaceFormatters'
import type { AsyncStatus, MobileWorkspacePane } from './workspaceTypes'
import { formatDuration, toItineraryPlace } from './workspaceTypes'
import '../TripDetailPage.css'
import '../TripWorkspacePortfolio.css'

type TripWorkspacePageProps = {
  accessToken: string
  tripId: string
  user: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
}

const itineraryGenerationEnabled = import.meta.env.VITE_ITINERARY_GENERATION_ENABLED === 'true'
const dayRouteEnabled = import.meta.env.DEV || import.meta.env.VITE_DAY_ROUTE_ENABLED === 'true'

export function TripWorkspacePage({
  accessToken,
  tripId,
  user,
  onBackToMain,
  onLogout,
}: TripWorkspacePageProps) {
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [placeViews, setPlaceViews] = useState<ItineraryPlaceView[]>([])
  const [placeViewsStatus, setPlaceViewsStatus] = useState<AsyncStatus>('loading')
  const [generation, setGeneration] = useState<ItineraryGenerationDetailResponse | null>(null)
  const [tripStatus, setTripStatus] = useState<AsyncStatus>('loading')
  const [tripErrorMessage, setTripErrorMessage] = useState('')
  const [generationFetchFailed, setGenerationFetchFailed] = useState(false)
  const [generationNotice, setGenerationNotice] = useState('')
  const [latestChatMessage, setLatestChatMessage] = useState<ChatMessageSentPayload | null>(null)
  const [latestChatChange, setLatestChatChange] = useState<ChatMessageChangedPayload | null>(null)
  const [latestChatTyping, setLatestChatTyping] = useState<ChatTypingChangedPayload | null>(null)
  const [presenceByMember, setPresenceByMember] = useState<Record<number, PresenceStatus> | null>(null)
  const [chatConnected, setChatConnected] = useState(true)
  const [chatReconnectedAt, setChatReconnectedAt] = useState(0)
  const [chatUnreadCount, setChatUnreadCount] = useState(0)
  const [voteRefreshSignal, setVoteRefreshSignal] = useState(0)
  const [regenerationRefreshSignal, setRegenerationRefreshSignal] = useState(0)
  const hasConnectedBeforeRef = useRef(false)
  const disconnectTimerRef = useRef<number | null>(null)
  const realtimeConnectionRef = useRef<TripRealtimeConnection | null>(null)
  const presenceVersionRef = useRef(0)

  const loadChatUnreadCount = useCallback(async () => {
    try {
      const { unreadCount } = await getChatUnreadCount(accessToken, tripId)
      setChatUnreadCount(unreadCount)
    } catch {
      // Non-critical: the badge just stays at its last known value until the next trigger.
    }
  }, [accessToken, tripId])

  const loadPresence = useCallback(async () => {
    try {
      const snapshot = await getTripPresence(accessToken, tripId)
      presenceVersionRef.current = snapshot.snapshotVersion
      setPresenceByMember(Object.fromEntries(snapshot.members.map((member) => [member.memberId, member.status])))
    } catch {
      setPresenceByMember(null)
    }
  }, [accessToken, tripId])

  const sendChatTyping = useCallback((state: 'STARTED' | 'HEARTBEAT' | 'STOPPED', clientSessionId: string, clientEventId: string) => (
    realtimeConnectionRef.current?.setTyping(state, clientSessionId, clientEventId) ?? false
  ), [])

  const loadTripDetail = useCallback(async () => {
    try {
      const tripResponse = await getTripDetail(accessToken, tripId)
      setTrip(tripResponse)
      setTripStatus('success')
      setTripErrorMessage('')

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
      setTripStatus('error')
      // spec §7 P1: 여행방 삭제/멤버십 상실은 존재 여부를 불필요하게 노출하지 않고 같은 404로
      // 온다 — private state를 지우고 메인으로 돌아갈 수 있게 안내한다.
      setTripErrorMessage(
        error instanceof ApiError && error.code === 'TRIP_NOT_FOUND'
          ? '이 여행방에 더 이상 접근할 수 없습니다. 나가졌거나 삭제되었을 수 있습니다.'
          : errorMessageFrom(error),
      )
      setTrip(null)
    }
  }, [accessToken, tripId])

  // Generation status is fetched independently of trip detail: a failure here must
  // never fail the whole page, since the backend already serves them as two
  // separate endpoints (spec §5 workspace state, "GENERATING_* 실패가 trip read
  // 전체를 실패시키지 않는다").
  //
  // A TRIP_NOT_FOUND here is the exception: this polling call is often the first
  // request to hit the server after a realtime removal forcibly closes the WS
  // session (the MEMBERSHIP_CHANGED broadcast can race the disconnect and never
  // reach the removed client), so it doubles as the deterministic fallback signal
  // for membership loss rather than leaving the stale page displayed indefinitely.
  const loadGeneration = useCallback(async () => {
    if (!itineraryGenerationEnabled) return
    try {
      setGeneration(await getLatestItineraryGeneration(accessToken, tripId))
      setGenerationFetchFailed(false)
    } catch (error: unknown) {
      if (error instanceof ApiError && error.code === 'TRIP_NOT_FOUND') {
        setTripStatus('error')
        setTripErrorMessage('이 여행방에 더 이상 접근할 수 없습니다. 나가졌거나 삭제되었을 수 있습니다.')
        setTrip(null)
        return
      }
      setGenerationFetchFailed(true)
      setGenerationNotice(errorMessageFrom(error))
    }
  }, [accessToken, tripId])

  const loadTripData = useCallback(async () => {
    await Promise.all([loadTripDetail(), loadGeneration()])
  }, [loadTripDetail, loadGeneration])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadTripData()
      void loadChatUnreadCount()
      void loadPresence()
    }, 0)
    return () => window.clearTimeout(timeoutId)
  }, [loadTripData, loadChatUnreadCount, loadPresence])

  useEffect(() => {
    let active = true

    const connection = connectTripRealtimeEvents({
      accessToken,
      tripId,
      onConnect: () => {
        if (itineraryGenerationEnabled) setGenerationNotice('실시간 상태 업데이트에 연결되었습니다.')
        if (disconnectTimerRef.current !== null) {
          window.clearTimeout(disconnectTimerRef.current)
          disconnectTimerRef.current = null
        }
        setChatConnected(true)
        if (hasConnectedBeforeRef.current) {
          setChatReconnectedAt(Date.now())
        }
        hasConnectedBeforeRef.current = true
        void loadGeneration()
        void loadPresence()
      },
      onDisconnected: () => {
        // spec §4 "연결 상태": only surface a disconnect after a 2s grace period so a
        // momentary blip doesn't flash the composer-gating UI.
        if (disconnectTimerRef.current !== null) return
        disconnectTimerRef.current = window.setTimeout(() => {
          setChatConnected(false)
          disconnectTimerRef.current = null
        }, 2000)
      },
      onError: () => {
        if (active) {
          setGenerationNotice('실시간 연결이 지연되고 있습니다. 새로고침하면 최신 상태를 확인할 수 있습니다.')
        }
      },
      onEvent: (event) => {
        if (!isKnownWorkspaceEvent(event)) {
          return
        }
        if (event.type === MEMBERSHIP_CHANGED) {
          const { affectedUserId, changeType } = event.payload
          const iWasRemoved = affectedUserId === user?.id && (changeType === 'REMOVED' || changeType === 'LEFT')
          if (iWasRemoved) {
            setTripStatus('error')
            setTripErrorMessage('이 여행방에 더 이상 접근할 수 없습니다. 나가졌거나 삭제되었을 수 있습니다.')
            setTrip(null)
          } else {
            void loadTripData()
          }
          return
        }
        if (event.type === CHAT_MESSAGE_SENT) {
          setLatestChatMessage(event.payload)
          void loadChatUnreadCount()
          return
        }
        if (event.type === CHAT_MESSAGE_DELETED || event.type === CHAT_REACTION_CHANGED) {
          setLatestChatChange({ ...event.payload })
          return
        }
        if (event.type === CHAT_TYPING_UPDATED) {
          setLatestChatTyping({ ...event.payload })
          return
        }
        if (event.type === MEMBER_PRESENCE_CHANGED) {
          if (event.payload.eventSequence <= presenceVersionRef.current) return
          presenceVersionRef.current = event.payload.eventSequence
          setPresenceByMember((current) => ({ ...(current ?? {}), [event.payload.memberId]: event.payload.status }))
          return
        }
        if (event.type === VOTE_OPENED || event.type === VOTE_CLOSED) {
          setVoteRefreshSignal((current) => current + 1)
          return
        }
        if (event.type === ITINERARY_REGENERATION_CHANGED) {
          setRegenerationRefreshSignal((current) => current + 1)
          if (event.payload.status === 'APPLIED') void loadTripData()
          return
        }
        if (event.type === ITINERARY_REVISION_APPLIED) {
          setVoteRefreshSignal((current) => current + 1)
          void loadTripData()
          return
        }
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
          void loadGeneration()
        }
      },
    })
    realtimeConnectionRef.current = connection

    return () => {
      active = false
      if (disconnectTimerRef.current !== null) {
        window.clearTimeout(disconnectTimerRef.current)
        disconnectTimerRef.current = null
      }
      connection.disconnect()
      realtimeConnectionRef.current = null
    }
  }, [accessToken, loadGeneration, loadTripData, loadChatUnreadCount, loadPresence, tripId, user?.id])

  if (tripStatus === 'loading') {
    return (
      <main className="trip-detail-page">
        <section className="trip-detail-state-card" aria-live="polite">
          <span className="state-spinner" aria-hidden="true" />
          <p>여행과 일정 정보를 불러오고 있습니다.</p>
        </section>
      </main>
    )
  }

  if (tripStatus === 'error' || !trip) {
    return (
      <main className="trip-detail-page">
        <section className="trip-detail-state-card error" role="alert">
          <span className="state-code">불러오기 실패</span>
          <h1>여행 정보를 확인하지 못했습니다.</h1>
          <p>{tripErrorMessage}</p>
          <div className="state-actions">
            <button type="button" onClick={() => {
              setTripStatus('loading')
              setTripErrorMessage('')
              void loadTripData()
            }}>다시 시도</button>
            <button className="secondary" type="button" onClick={onBackToMain}>메인으로 돌아가기</button>
          </div>
        </section>
      </main>
    )
  }

  return (
    <main className="trip-detail-page">
      <a className="trip-detail-skip-link" href="#trip-detail-workspace">일정으로 바로가기</a>
      <TripWorkspace
        accessToken={accessToken}
        chatConnected={chatConnected}
        chatReconnectedAt={chatReconnectedAt}
        chatUnreadCount={chatUnreadCount}
        currentUser={user}
        generation={generation}
        generationFetchFailed={generationFetchFailed}
        generationNotice={generationNotice}
        latestChatMessage={latestChatMessage}
        latestChatChange={latestChatChange}
        latestChatTyping={latestChatTyping}
        presenceByMember={presenceByMember}
        sendChatTyping={sendChatTyping}
        onBackToMain={onBackToMain}
        onChatRead={loadChatUnreadCount}
        onLogout={onLogout}
        onRefresh={() => void loadTripData()}
        placeViews={placeViews}
        placeViewsStatus={placeViewsStatus}
        trip={trip}
        regenerationRefreshSignal={regenerationRefreshSignal}
        voteRefreshSignal={voteRefreshSignal}
      />
    </main>
  )
}

function TripWorkspace({
  trip,
  placeViews,
  placeViewsStatus,
  generation,
  generationFetchFailed,
  generationNotice,
  latestChatMessage,
  latestChatChange,
  latestChatTyping,
  presenceByMember,
  sendChatTyping,
  chatConnected,
  chatReconnectedAt,
  chatUnreadCount,
  onChatRead,
  accessToken,
  currentUser,
  onBackToMain,
  onLogout,
  onRefresh,
  regenerationRefreshSignal,
  voteRefreshSignal,
}: {
  trip: TripDetail
  placeViews: ItineraryPlaceView[]
  placeViewsStatus: AsyncStatus
  generation: ItineraryGenerationDetailResponse | null
  generationFetchFailed: boolean
  generationNotice: string
  latestChatMessage: ChatMessageSentPayload | null
  latestChatChange: ChatMessageChangedPayload | null
  latestChatTyping: ChatTypingChangedPayload | null
  presenceByMember: Record<number, PresenceStatus> | null
  sendChatTyping: (state: 'STARTED' | 'HEARTBEAT' | 'STOPPED', clientSessionId: string, clientEventId: string) => boolean
  chatConnected: boolean
  chatReconnectedAt: number
  chatUnreadCount: number
  onChatRead: () => void
  accessToken: string
  currentUser: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
  onRefresh: () => void
  regenerationRefreshSignal: number
  voteRefreshSignal: number
}) {
  const itinerary = trip.itineraries[0] ?? null
  const days = itinerary?.days.map((day) => day.day) ?? buildTripDayNumbers(trip.startDate, trip.endDate)
  const firstDay = days[0] ?? 1
  const [activeDay, setActiveDay] = useState(firstDay)
  const [selectedPlaceId, setSelectedPlaceId] = useState('')
  const [mobilePane, setMobilePane] = useState<MobileWorkspacePane>('SCHEDULE')
  const [dayRoute, setDayRoute] = useState<DayRoute | null>(null)
  const [routeStatus, setRouteStatus] = useState<AsyncStatus>('idle')
  const [routeError, setRouteError] = useState('')
  const [routeRequestKey, setRouteRequestKey] = useState('')
  const [latestRegeneration, setLatestRegeneration] = useState<ItineraryRegeneration | null>(null)
  const [editorMode, setEditorMode] = useState<'FULL' | 'PARTIAL' | null>(null)
  const resolvedDay = days.includes(activeDay) ? activeDay : firstDay
  const storedPlaces = useMemo(() => itinerary?.days.flatMap((day) => day.items.map((item) => ({
    id: item.id.toString(),
    day: day.day,
    order: item.sequence,
    title: `저장된 장소 ${item.sequence}`,
    startTime: item.startTime.slice(0, 5),
    duration: formatDuration(item.durationMinutes),
    durationMinutes: item.durationMinutes,
    latitude: null,
    longitude: null,
    locationLabel: null,
    googleMapsUri: null,
    placeId: item.placeId,
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
  const routeKey = itinerary ? `${itinerary.id}:${itinerary.version}:${resolvedDay}` : ''
  const routeNeeded = Boolean(dayRouteEnabled && itinerary && activePlaces.length >= 2)
  const routeMatchesActiveDay = routeRequestKey === routeKey
  const activeDayRoute = routeMatchesActiveDay ? dayRoute : null
  const activeRouteStatus: AsyncStatus = routeNeeded
    ? (routeMatchesActiveDay ? routeStatus : 'loading')
    : 'success'
  const activeRouteError = routeMatchesActiveDay ? routeError : ''
  const currentMembership = trip.members.find((member) => member.userId === currentUser?.id)
  const canEditItinerary = currentMembership?.role === 'OWNER' && Boolean(itinerary)
  const activeRegeneration = latestRegeneration && ['GENERATING', 'READY_FOR_REVIEW'].includes(latestRegeneration.status)
    ? latestRegeneration
    : null

  useEffect(() => {
    let cancelled = false
    getLatestItineraryRegeneration(accessToken, trip.id)
      .then((response) => {
        if (!cancelled) setLatestRegeneration(response)
      })
      .catch(() => {
        if (!cancelled) setLatestRegeneration(null)
      })
    return () => { cancelled = true }
  }, [accessToken, regenerationRefreshSignal, trip.id, itinerary?.id])

  useEffect(() => {
    if (!dayRouteEnabled || !itinerary || activePlaces.length < 2) {
      return undefined
    }

    let cancelled = false
    getDayRoute(accessToken, trip.id, resolvedDay)
      .then((response) => {
        if (cancelled) return
        setRouteRequestKey(routeKey)
        if (response.itineraryId !== itinerary.id || response.itineraryVersion !== itinerary.version) {
          setDayRoute(null)
          setRouteStatus('error')
          setRouteError('일정이 갱신되어 경로를 다시 확인해 주세요.')
          return
        }
        setDayRoute(response)
        setRouteStatus('success')
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setRouteRequestKey(routeKey)
        setDayRoute(null)
        setRouteStatus('error')
        setRouteError(routeErrorMessage(error))
      })

    return () => {
      cancelled = true
    }
  }, [accessToken, activePlaces.length, itinerary, resolvedDay, routeKey, trip.id])

  function handleDayChange(day: number) {
    setActiveDay(day)
    setSelectedPlaceId(places.find((place) => place.day === day)?.id ?? '')
  }

  const panelIds: Record<MobileWorkspacePane, string> = {
    SCHEDULE: 'workspace-pane-schedule',
    MAP: 'workspace-pane-map',
    ROOM: 'workspace-pane-room',
  }

  return (
    <section className="trip-workspace" id="trip-detail-workspace" aria-label="여행 상세 일정" tabIndex={-1}>
      <WorkspaceHeader
        accessToken={accessToken}
        currentUser={currentUser}
        dayCount={days.length}
        onBackToMain={onBackToMain}
        onLeftTrip={onBackToMain}
        onLogout={onLogout}
        onMembershipChanged={onRefresh}
        onRefresh={onRefresh}
        placeCount={places.length}
        presenceByMember={presenceByMember}
        trip={trip}
      />
      {showGenerationProgress && (
        <GenerationStatusPanel generation={generation} hasItinerary={Boolean(itinerary)} notice={generationNotice} onRefresh={onRefresh} />
      )}
      {generationFetchFailed && (
        <div className="workspace-partial-error" role="status">
          최신 일정 생성 상태를 확인하지 못했습니다. 저장된 일정은 아래에서 계속 확인할 수 있습니다.
        </div>
      )}
      {!itinerary && <WorkspaceSetupNotice generation={generation} />}
      {/* CSS shows this switcher below the 1180px breakpoint (both the MEDIUM
          "일정·지도 2열 + 여행방 pane" and NARROW "한 pane" layouts) and hides it
          at WIDE — it stays mounted at every width so mobile-active state
          survives a resize/rotation without losing the selected pane. */}
      <WorkspacePaneSwitcher activePane={mobilePane} onChange={setMobilePane} panelIds={panelIds} />
      <div className="planning-layout">
        <ItinerarySchedule
          activeDate={activeDate}
          activeDay={resolvedDay}
          ariaLabelledBy="workspace-pane-tab-SCHEDULE"
          canEdit={canEditItinerary}
          className={mobilePane === 'SCHEDULE' ? 'mobile-active' : ''}
          days={days}
          editInProgress={Boolean(activeRegeneration)}
          id={panelIds.SCHEDULE}
          panelRole="tabpanel"
          places={activePlaces}
          placesStatus={placeViewsStatus}
          route={activeDayRoute}
          routeError={activeRouteError}
          routeStatus={activeRouteStatus}
          selectedPlaceId={selectedPlace?.id ?? ''}
          onDayChange={handleDayChange}
          onEditFull={() => setEditorMode('FULL')}
          onEditPartial={() => setEditorMode('PARTIAL')}
          onOpenActiveEdit={() => setEditorMode(activeRegeneration?.scope ?? 'FULL')}
          onSelectPlace={setSelectedPlaceId}
        />
        <div
          aria-labelledby="workspace-pane-tab-MAP"
          className={`planning-map-column ${mobilePane === 'MAP' ? 'mobile-active' : ''}`}
          id={panelIds.MAP}
          role="tabpanel"
        >
          <TripMapCanvas
            activeDay={resolvedDay}
            activeDate={activeDate}
            destination={trip.destinationInfo.displayName || trip.destination}
            destinationCenter={trip.destinationInfo.latitude !== null && trip.destinationInfo.longitude !== null
              ? { lat: trip.destinationInfo.latitude, lng: trip.destinationInfo.longitude }
              : null}
            places={activePlaces}
            route={activeDayRoute}
            routeError={activeRouteError}
            routeStatus={activeRouteStatus}
            selectedPlace={selectedPlace}
            selectedPlaceId={selectedPlace?.id ?? ''}
            onSelectPlace={setSelectedPlaceId}
          />
        </div>
        <RoomPanel
          accessToken={accessToken}
          activeDay={resolvedDay}
          baseItineraryId={itinerary?.id ?? null}
          baseItineraryVersion={itinerary?.version ?? null}
          ariaLabelledBy="workspace-pane-tab-ROOM"
          chatConnected={chatConnected}
          chatReconnectedAt={chatReconnectedAt}
          chatUnreadCount={chatUnreadCount}
          className={mobilePane === 'ROOM' ? 'mobile-active' : ''}
          currentUser={currentUser}
          destinationPlaceId={trip.destinationPlaceId}
          id={panelIds.ROOM}
          latestChatMessage={latestChatMessage}
          latestChatChange={latestChatChange}
          latestChatTyping={latestChatTyping}
          members={trip.members}
          onChatRead={onChatRead}
          onRevisionApplied={onRefresh}
          sendChatTyping={sendChatTyping}
          panelRole="tabpanel"
          selectedPlace={selectedPlace}
          tripId={trip.id}
          voteRefreshSignal={voteRefreshSignal}
        />
      </div>
      {editorMode && itinerary && canEditItinerary && (
        <ItineraryEditor
          accessToken={accessToken}
          activeDay={activeRegeneration?.dayNumber ?? resolvedDay}
          baseItineraryId={itinerary.id}
          baseItineraryVersion={itinerary.version}
          initialJob={activeRegeneration}
          key={`${editorMode}-${activeRegeneration?.regenerationId ?? 'new'}`}
          mode={activeRegeneration?.scope ?? editorMode}
          onApplied={onRefresh}
          onClose={() => setEditorMode(null)}
          onJobChanged={setLatestRegeneration}
          places={places}
          tripId={trip.id}
        />
      )}
    </section>
  )
}

function errorMessageFrom(error: unknown) {
  return error instanceof ApiError ? error.message : '요청 처리 중 오류가 발생했습니다.'
}

function routeErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return '경로를 잠시 확인하지 못했어요. 일정과 장소는 그대로 볼 수 있습니다.'
  if (error.code === 'ROUTE_QUOTA_EXCEEDED') return '오늘의 경로 조회 한도에 도달했어요. 일정과 장소는 그대로 볼 수 있습니다.'
  if (error.code === 'ROUTE_PROVIDER_TIMEOUT') return '경로 확인이 지연되고 있어요. 잠시 후 다시 확인해 주세요.'
  return '경로를 잠시 확인하지 못했어요. 일정과 장소는 그대로 볼 수 있습니다.'
}
