import { useCallback, useEffect, useMemo, useState } from 'react'
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
import { connectTripRealtimeEvents, ITINERARY_GENERATION_STATUS_CHANGED, MEMBERSHIP_CHANGED } from '../../../api/realtime'
import { WorkspaceHeader } from './components/WorkspaceHeader'
import { GenerationStatusPanel, WorkspaceSetupNotice } from './components/GenerationStatusPanel'
import { WorkspacePaneSwitcher } from './components/WorkspacePaneSwitcher'
import { ItinerarySchedule } from './schedule/ItinerarySchedule'
import { TripMapCanvas } from './map/TripMapCanvas'
import { RoomPanel } from './room/RoomPanel'
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
    }, 0)
    return () => window.clearTimeout(timeoutId)
  }, [loadTripData])

  useEffect(() => {
    if (!itineraryGenerationEnabled) {
      return undefined
    }

    let active = true

    const connection = connectTripRealtimeEvents({
      accessToken,
      tripId,
      onConnect: () => {
        setGenerationNotice('실시간 상태 업데이트에 연결되었습니다.')
        void loadGeneration()
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

    return () => {
      active = false
      connection.disconnect()
    }
  }, [accessToken, loadGeneration, loadTripData, tripId, user?.id])

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
        currentUser={user}
        generation={generation}
        generationFetchFailed={generationFetchFailed}
        generationNotice={generationNotice}
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
  generationFetchFailed,
  generationNotice,
  accessToken,
  currentUser,
  onBackToMain,
  onLogout,
  onRefresh,
}: {
  trip: TripDetail
  placeViews: ItineraryPlaceView[]
  placeViewsStatus: AsyncStatus
  generation: ItineraryGenerationDetailResponse | null
  generationFetchFailed: boolean
  generationNotice: string
  accessToken: string
  currentUser: AuthUser | null
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
          className={mobilePane === 'SCHEDULE' ? 'mobile-active' : ''}
          days={days}
          id={panelIds.SCHEDULE}
          panelRole="tabpanel"
          places={activePlaces}
          placesStatus={placeViewsStatus}
          selectedPlaceId={selectedPlace?.id ?? ''}
          onDayChange={handleDayChange}
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
            selectedPlaceId={selectedPlace?.id ?? ''}
            onSelectPlace={setSelectedPlaceId}
          />
        </div>
        <RoomPanel
          activeDay={resolvedDay}
          ariaLabelledBy="workspace-pane-tab-ROOM"
          className={mobilePane === 'ROOM' ? 'mobile-active' : ''}
          currentUser={currentUser}
          id={panelIds.ROOM}
          members={trip.members}
          panelRole="tabpanel"
          selectedPlace={selectedPlace}
        />
      </div>
    </section>
  )
}

function errorMessageFrom(error: unknown) {
  return error instanceof ApiError ? error.message : '요청 처리 중 오류가 발생했습니다.'
}
