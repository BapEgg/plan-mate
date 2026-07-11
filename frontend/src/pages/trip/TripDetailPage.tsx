import { useEffect, useMemo, useState } from 'react'
import type { AuthUser } from '../../api/auth'
import { ApiError } from '../../api/client'
import { getAiRequest, getLatestItineraryGeneration, getManualPrompt, getTripDetail, submitManualResponse } from '../../api/trips'
import type { AiItineraryResponse, Itinerary, ItineraryGenerationDetailResponse, ItineraryItem, TripDetail, TripMember, TripPlanningProfile } from '../../api/trips'
import { connectTripRealtimeEvents, ITINERARY_GENERATION_STATUS_CHANGED } from '../../api/realtime'
import './TripDetailPage.css'

type TripDetailPageProps = {
  accessToken: string
  tripId: string
  user: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'

type ItineraryPlace = {
  id: string
  day: number
  order: number
  title: string
  category: string
  time: string
  duration: string
  memo?: string
  x: number
  y: number
  hours: string
  parking: string
  price: string
  rating: string
  contentSummary: string
  photoTip: string
}

export function TripDetailPage({
  accessToken,
  tripId,
  user,
  onBackToMain,
  onLogout,
}: TripDetailPageProps) {
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [latestGeneration, setLatestGeneration] = useState<ItineraryGenerationDetailResponse | null>(null)
  const [status, setStatus] = useState<AsyncStatus>('idle')
  const [errorMessage, setErrorMessage] = useState('')
  const [manualPrompt, setManualPrompt] = useState('')
  const [aiRequestJson, setAiRequestJson] = useState('')
  const [manualResponseJson, setManualResponseJson] = useState('')
  const [manualStatus, setManualStatus] = useState<AsyncStatus>('idle')
  const [manualMessage, setManualMessage] = useState('')

  useEffect(() => {
    let active = true

    async function loadTrip() {
      setStatus('loading')
      setErrorMessage('')
      try {
        const [response, generation] = await Promise.all([
          getTripDetail(accessToken, tripId),
          getLatestItineraryGeneration(accessToken, tripId),
        ])
        if (!active) {
          return
        }
        setTrip(response)
        setLatestGeneration(generation)
        setStatus('success')
      } catch (error: unknown) {
        if (!active) {
          return
        }
        setStatus('error')
        setErrorMessage(errorMessageFrom(error))
      }
    }

    void loadTrip()
    return () => {
      active = false
    }
  }, [accessToken, tripId])

  useEffect(() => {
    if (!accessToken || !tripId) {
      return undefined
    }

    let active = true

    async function refetchLatestGeneration() {
      try {
        const generation = await getLatestItineraryGeneration(accessToken, tripId)
        if (active) {
          setLatestGeneration(generation)
        }
      } catch (error: unknown) {
        if (active) {
          setManualStatus('error')
          setManualMessage(errorMessageFrom(error))
        }
      }
    }

    async function refetchTrip() {
      const response = await getTripDetail(accessToken, tripId)
      if (active) {
        setTrip(response)
        setStatus('success')
      }
    }

    const connection = connectTripRealtimeEvents({
      accessToken,
      tripId,
      onConnect: () => {
        void refetchLatestGeneration()
      },
      onError: (message) => {
        if (active) {
          setManualMessage(message)
        }
      },
      onEvent: (event) => {
        if (event.type !== ITINERARY_GENERATION_STATUS_CHANGED) {
          return
        }
        void refetchLatestGeneration()
        setLatestGeneration((current) => {
          if (!current || current.generationId !== event.payload.generationId) {
            return current
          }
          return {
            ...current,
            status: event.payload.status,
            candidateCount: event.payload.candidateCount,
            failureReason: event.payload.failureReason,
            updatedAt: event.payload.updatedAt,
          }
        })
        if (event.payload.status === 'READY_FOR_PLANNING') {
          setManualStatus('success')
          setManualMessage('?쇱젙 ?앹꽦 ?꾨낫媛 以鍮꾨릺?덉뒿?덈떎.')
        }
        if (event.payload.status === 'FAILED') {
          setManualStatus('error')
          setManualMessage(event.payload.failureReason ?? '?쇱젙 ?앹꽦???ㅽ뙣?덉뒿?덈떎.')
        }
        if (event.payload.status === 'COMPLETED') {
          setManualStatus('success')
          setManualMessage('?쇱젙????λ릺?덉뒿?덈떎.')
          void refetchTrip().catch((error: unknown) => {
            if (active) {
              setManualStatus('error')
              setManualMessage(errorMessageFrom(error))
            }
          })
        }
      },
    })

    return () => {
      active = false
      connection.disconnect()
    }
  }, [accessToken, tripId])

  async function handleLoadManualPrompt() {
    if (!latestGeneration || latestGeneration.status !== 'READY_FOR_PLANNING') {
      return
    }
    setManualStatus('loading')
    setManualMessage('')
    try {
      const prompt = await getManualPrompt(accessToken, tripId, latestGeneration.generationId)
      setManualPrompt(prompt)
      setManualStatus('success')
      setManualMessage('?꾨＼?꾪듃瑜?遺덈윭?붿뒿?덈떎.')
    } catch (error: unknown) {
      setManualStatus('error')
      setManualMessage(errorMessageFrom(error))
    }
  }

  async function handleLoadAiRequest() {
    if (!latestGeneration || latestGeneration.status !== 'READY_FOR_PLANNING') {
      return
    }
    setManualStatus('loading')
    setManualMessage('')
    try {
      const aiRequest = await getAiRequest(accessToken, tripId, latestGeneration.generationId)
      setAiRequestJson(JSON.stringify(aiRequest, null, 2))
      setManualStatus('success')
      setManualMessage('AI request JSON??遺덈윭?붿뒿?덈떎.')
    } catch (error: unknown) {
      setManualStatus('error')
      setManualMessage(errorMessageFrom(error))
    }
  }

  async function handleSubmitManualResponse() {
    if (!latestGeneration || latestGeneration.status !== 'READY_FOR_PLANNING') {
      return
    }
    let parsed: AiItineraryResponse
    try {
      parsed = JSON.parse(manualResponseJson) as AiItineraryResponse
    } catch {
      setManualStatus('error')
      setManualMessage('ChatGPT ?묐떟 JSON ?뺤떇???щ컮瑜댁? ?딆뒿?덈떎.')
      return
    }

    setManualStatus('loading')
    setManualMessage('')
    try {
      const generation = await submitManualResponse(accessToken, tripId, latestGeneration.generationId, parsed)
      setLatestGeneration(generation)
      setManualStatus('success')
      setManualMessage('?쇱젙????λ릺?덉뒿?덈떎.')
      const response = await getTripDetail(accessToken, tripId)
      setTrip(response)
    } catch (error: unknown) {
      setManualStatus('error')
      setManualMessage(errorMessageFrom(error))
    }
  }

  if (status === 'loading' || status === 'idle') {
    return (
      <main className="trip-detail-page">
        <section className="trip-detail-loading">
          <p>여행 정보를 불러오고 있습니다.</p>
        </section>
      </main>
    )
  }

  if (status === 'error' || !trip) {
    return (
      <main className="trip-detail-page">
        <section className="trip-detail-error">
          <h1>여행 정보를 불러오지 못했습니다.</h1>
          <p>{errorMessage}</p>
          <button type="button" onClick={onBackToMain}>메인으로 돌아가기</button>
        </section>
      </main>
    )
  }

  return (
    <main className="trip-detail-page">
      <TripPlanningWorkspace
        currentUser={user}
        onBackToMain={onBackToMain}
        onLogout={onLogout}
        latestGeneration={latestGeneration}
        manualPrompt={manualPrompt}
        aiRequestJson={aiRequestJson}
        manualResponseJson={manualResponseJson}
        manualStatus={manualStatus}
        manualMessage={manualMessage}
        onAiRequestLoad={handleLoadAiRequest}
        onManualPromptLoad={handleLoadManualPrompt}
        onManualResponseChange={setManualResponseJson}
        onManualResponseSubmit={handleSubmitManualResponse}
        trip={trip}
      />
    </main>
  )
}

function TripPlanningWorkspace({
  trip,
  currentUser,
  latestGeneration,
  manualPrompt,
  aiRequestJson,
  manualResponseJson,
  manualStatus,
  manualMessage,
  onBackToMain,
  onLogout,
  onAiRequestLoad,
  onManualPromptLoad,
  onManualResponseChange,
  onManualResponseSubmit,
}: {
  trip: TripDetail
  currentUser: AuthUser | null
  latestGeneration: ItineraryGenerationDetailResponse | null
  manualPrompt: string
  aiRequestJson: string
  manualResponseJson: string
  manualStatus: AsyncStatus
  manualMessage: string
  onBackToMain: () => void
  onLogout: () => void
  onAiRequestLoad: () => void
  onManualPromptLoad: () => void
  onManualResponseChange: (value: string) => void
  onManualResponseSubmit: () => void
}) {
  const latestItinerary = trip.itineraries[0] ?? null
  const dayOptions = latestItinerary?.days.map((day) => day.day) ?? []
  const [activeDay, setActiveDay] = useState(1)
  const [selectedPlaceId, setSelectedPlaceId] = useState('')
  const resolvedActiveDay = dayOptions.includes(activeDay) ? activeDay : dayOptions[0] ?? 1

  const allPlaces = useMemo(() => toItineraryPlaces(latestItinerary), [latestItinerary])
  const activePlaces = useMemo(() => allPlaces.filter((place) => place.day === resolvedActiveDay), [resolvedActiveDay, allPlaces])
  const selectedPlace = activePlaces.find((place) => place.id === selectedPlaceId) ?? activePlaces[0] ?? null

  function handleDayChange(day: number) {
    const firstPlace = allPlaces.find((place) => place.day === day)
    setActiveDay(day)
    setSelectedPlaceId(firstPlace?.id ?? '')
  }

  return (
    <section className="planning-board" aria-label="여행 상세 플래닝 보드">
      <PlanningHeader trip={trip} members={trip.members} onBackToMain={onBackToMain} onLogout={onLogout} />
      {trip.planningProfile && <PlanningProfileSummary profile={trip.planningProfile} />}
      {latestItinerary ? (
        <div className="planning-layout">
          <ItinerarySidebar
            activeDay={resolvedActiveDay}
            dayOptions={dayOptions}
            places={activePlaces}
            selectedPlaceId={selectedPlace?.id ?? ''}
            onDayChange={handleDayChange}
            onSelectPlace={setSelectedPlaceId}
          />
          <MapStage
            activeDay={resolvedActiveDay}
            places={activePlaces}
            selectedPlace={selectedPlace}
            onSelectPlace={setSelectedPlaceId}
            onClosePlace={() => setSelectedPlaceId('')}
          />
          <TripChatPanel members={trip.members} currentUser={currentUser} />
        </div>
      ) : (
        <section className="planning-empty-state">
          <TripGenerationRecoveryPanel
            aiRequestJson={aiRequestJson}
            generation={latestGeneration}
            manualMessage={manualMessage}
            manualPrompt={manualPrompt}
            manualResponseJson={manualResponseJson}
            manualStatus={manualStatus}
            onAiRequestLoad={onAiRequestLoad}
            onManualPromptLoad={onManualPromptLoad}
            onManualResponseChange={onManualResponseChange}
            onManualResponseSubmit={onManualResponseSubmit}
          />
          <h2>저장된 일정이 아직 없습니다.</h2>
          <p>manual handoff에서 ChatGPT 응답 JSON을 제출하면 이 화면에 실제 일정이 표시됩니다.</p>
        </section>
      )}
    </section>
  )
}

function TripGenerationRecoveryPanel({
  aiRequestJson,
  generation,
  manualMessage,
  manualPrompt,
  manualResponseJson,
  manualStatus,
  onAiRequestLoad,
  onManualPromptLoad,
  onManualResponseChange,
  onManualResponseSubmit,
}: {
  aiRequestJson: string
  generation: ItineraryGenerationDetailResponse | null
  manualMessage: string
  manualPrompt: string
  manualResponseJson: string
  manualStatus: AsyncStatus
  onAiRequestLoad: () => void
  onManualPromptLoad: () => void
  onManualResponseChange: (value: string) => void
  onManualResponseSubmit: () => void
}) {
  if (!generation) {
    return null
  }

  const isReady = generation.status === 'READY_FOR_PLANNING'
  const isFailed = generation.status === 'FAILED'

  return (
    <section className="trip-generation-recovery" aria-live="polite">
      <div>
        <span>Generation</span>
        <strong>{generation.status}</strong>
        <p>
          {isFailed
            ? generation.failureReason ?? '?쇱젙 ?앹꽦???ㅽ뙣?덉뒿?덈떎.'
            : `?꾨낫 ${generation.candidateCount}媛쒕? ?뺤씤?덉뒿?덈떎.`}
        </p>
      </div>
      <div className="trip-generation-actions">
        <button type="button" onClick={onManualPromptLoad} disabled={!isReady || manualStatus === 'loading'}>
          Prompt
        </button>
        <button type="button" onClick={onAiRequestLoad} disabled={!isReady || manualStatus === 'loading'}>
          AI request
        </button>
      </div>
      {manualMessage && <p className={`trip-generation-message ${manualStatus}`}>{manualMessage}</p>}
      {manualPrompt && <textarea readOnly value={manualPrompt} />}
      {aiRequestJson && <textarea readOnly value={aiRequestJson} />}
      <textarea
        placeholder="ChatGPT response JSON"
        value={manualResponseJson}
        onChange={(event) => onManualResponseChange(event.target.value)}
      />
      <button
        className="trip-generation-submit"
        type="button"
        onClick={onManualResponseSubmit}
        disabled={!isReady || manualStatus === 'loading' || !manualResponseJson.trim()}
      >
        Submit response
      </button>
    </section>
  )
}

function PlanningProfileSummary({ profile }: { profile: TripPlanningProfile }) {
  const accommodationTitle = profile.accommodationMode === 'PLACE_SEARCH'
    ? profile.accommodationName ?? '선택된 숙소'
    : '숙소 미정'
  const accommodationDescription = profile.accommodationMode === 'PLACE_SEARCH'
    ? profile.accommodationFormattedAddress ?? 'Google Places에서 선택한 숙소'
    : accommodationAreaLabel(profile.accommodationArea)

  return (
    <section className="planning-profile-summary" aria-label="저장된 여행 설정">
      <article>
        <span>숙소</span>
        <strong>{accommodationTitle}</strong>
        <p>{accommodationDescription}</p>
      </article>
      <article>
        <span>하루 일정 시간</span>
        <strong>{formatTime(profile.dailyStartTime)} ~ {formatTime(profile.dailyEndTime)}</strong>
        <p>일정을 배치할 수 있는 하루 기준 시간입니다.</p>
      </article>
      <article>
        <span>꼭 가고 싶은 곳</span>
        <strong>{profile.mustVisitPlaces.length}개 선택</strong>
        <p>{profile.mustVisitPlaces.map((place) => place.name).join(' · ') || '선택한 장소 없음'}</p>
      </article>
    </section>
  )
}

function PlanningHeader({
  trip,
  members,
  onBackToMain,
  onLogout,
}: {
  trip: TripDetail
  members: TripMember[]
  onBackToMain: () => void
  onLogout: () => void
}) {
  return (
    <header className="planning-header">
      <button className="icon-back-button" type="button" onClick={onBackToMain} aria-label="메인으로 돌아가기">
        <span aria-hidden="true">←</span>
      </button>
      <div className="planning-title-block">
        <span className="planning-kicker">PlanMate itinerary</span>
        <h1>{trip.title}</h1>
        <p>{formatDate(trip.startDate)} - {formatDate(trip.endDate)}</p>
      </div>
      <div className="planning-header-actions">
        <div className="member-avatar-stack" aria-label="참여자 목록">
          <span className="member-count-icon" aria-hidden="true">팀</span>
          {members.slice(0, 3).map((member) => (
            <MemberAvatar member={member} key={member.userId} />
          ))}
          <span className="member-total">{members.length}명</span>
        </div>
        <button className="outline-action" type="button" disabled>초대 준비 중</button>
        <button className="ghost-action" type="button" onClick={onLogout}>로그아웃</button>
      </div>
    </header>
  )
}

function ItinerarySidebar({
  activeDay,
  dayOptions,
  places,
  selectedPlaceId,
  onDayChange,
  onSelectPlace,
}: {
  activeDay: number
  dayOptions: number[]
  places: ItineraryPlace[]
  selectedPlaceId: string
  onDayChange: (day: number) => void
  onSelectPlace: (placeId: string) => void
}) {
  return (
    <aside className="itinerary-sidebar">
      <div className="sidebar-heading">
        <span>Itinerary</span>
        <h2>Day {activeDay}</h2>
      </div>
      <div className="day-tabs" role="tablist" aria-label="일자 선택">
        {dayOptions.map((day) => (
          <button
            className={day === activeDay ? 'active' : ''}
            key={day}
            type="button"
            onClick={() => onDayChange(day)}
          >
            Day {day}
          </button>
        ))}
      </div>
      <ol className="place-timeline">
        {places.map((place) => (
          <li key={place.id}>
            <button
              className={place.id === selectedPlaceId ? 'active' : ''}
              type="button"
              onClick={() => onSelectPlace(place.id)}
            >
              <span className="timeline-order">{place.order}</span>
              <div>
                <strong>{place.title}</strong>
                <small>{place.time} · {place.duration}</small>
                {place.memo && <p>{place.memo}</p>}
              </div>
            </button>
          </li>
        ))}
      </ol>
    </aside>
  )
}

function MapStage({
  activeDay,
  places,
  selectedPlace,
  onSelectPlace,
  onClosePlace,
}: {
  activeDay: number
  places: ItineraryPlace[]
  selectedPlace: ItineraryPlace | null
  onSelectPlace: (placeId: string) => void
  onClosePlace: () => void
}) {
  return (
    <section className="map-stage" aria-label={`Day ${activeDay} 지도`}>
      <div className="map-canvas">
        {places.map((place) => (
          <button
            aria-label={place.title}
            className={`map-pin ${selectedPlace?.id === place.id ? 'active' : ''}`}
            key={place.id}
            style={{ left: `${place.x}%`, top: `${place.y}%` }}
            type="button"
            onClick={() => onSelectPlace(place.id)}
          >
            <span>{place.order}</span>
          </button>
        ))}
        {selectedPlace && <PlaceInsightCard place={selectedPlace} onClose={onClosePlace} />}
      </div>
    </section>
  )
}

function PlaceInsightCard({ place, onClose }: { place: ItineraryPlace; onClose: () => void }) {
  return (
    <article className="place-insight-card">
      <div>
        <span>{place.category}</span>
        <button type="button" onClick={onClose} aria-label="장소 정보 닫기">×</button>
      </div>
      <h3>{place.title}</h3>
      <p>{place.time} · {place.duration}</p>
      <dl>
        <div>
          <dt>위치</dt>
          <dd>{place.hours}</dd>
        </div>
        <div>
          <dt>평점</dt>
          <dd>{place.rating}</dd>
        </div>
        <div>
          <dt>메모</dt>
          <dd>{place.contentSummary}</dd>
        </div>
      </dl>
    </article>
  )
}

function TripChatPanel({
  members,
  currentUser,
}: {
  members: TripMember[]
  currentUser: AuthUser | null
}) {
  return (
    <aside className="trip-chat-panel">
      <div className="chat-heading">
        <span>Trip room</span>
        <h2>멤버</h2>
      </div>
      <div className="chat-message-list">
        {members.map((member) => (
          <div className="chat-message" key={member.userId}>
            <MemberAvatar member={member} />
            <div>
              <strong>{member.nickname}{currentUser?.id === member.userId ? ' · 나' : ''}</strong>
              <p>{member.role}</p>
            </div>
          </div>
        ))}
      </div>
    </aside>
  )
}

function MemberAvatar({ member }: { member: TripMember }) {
  if (member.profileImageUrl) {
    return <img className="member-avatar" src={member.profileImageUrl} alt={`${member.nickname} 프로필`} />
  }
  return <span className="member-avatar fallback">{member.nickname.slice(0, 1)}</span>
}

function toItineraryPlaces(itinerary: Itinerary | null): ItineraryPlace[] {
  if (!itinerary) {
    return []
  }
  const items = itinerary.days.flatMap((day) => (
    day.items.map((item) => toItineraryPlace(item, day.day))
  ))
  return withMapPositions(items)
}

function toItineraryPlace(item: ItineraryItem, day: number): ItineraryPlace {
  return {
    id: item.id.toString(),
    day,
    order: item.sequence,
    title: item.placeName,
    category: '일정',
    time: item.startTime.slice(0, 5),
    duration: formatDuration(item.durationMinutes),
    memo: item.reason ?? undefined,
    x: 50,
    y: 50,
    hours: `${item.latitude.toFixed(5)}, ${item.longitude.toFixed(5)}`,
    parking: '',
    price: '',
    rating: '저장된 후보 기준',
    contentSummary: item.reason ?? '저장된 일정 항목입니다.',
    photoTip: '',
  }
}

function withMapPositions(places: ItineraryPlace[]) {
  if (places.length === 0) {
    return places
  }
  return places.map((place, index) => ({
    ...place,
    x: 18 + ((index * 23) % 64),
    y: 22 + ((index * 17) % 58),
  }))
}

function formatDuration(minutes: number) {
  if (minutes < 60) {
    return `${minutes}분`
  }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(new Date(`${value}T00:00:00`))
}

function formatTime(value: string) {
  return value.slice(0, 5)
}

function accommodationAreaLabel(value: TripPlanningProfile['accommodationArea']) {
  switch (value) {
    case 'TOURIST_CENTER':
      return '중심 관광지 근처'
    case 'TRANSIT':
      return '대중교통이 편한 곳'
    case 'QUIET':
      return '조용한 지역'
    case 'ANYWHERE':
      return '지역 상관없음'
    default:
      return '선호 숙소 지역 없음'
  }
}

function errorMessageFrom(error: unknown) {
  if (error instanceof ApiError) {
    return error.message
  }
  return '요청 처리 중 오류가 발생했습니다.'
}
