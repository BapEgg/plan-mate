import { useEffect, useMemo, useState } from 'react'
import type { AuthUser } from '../../api/auth'
import { API_BASE_URL, ApiError } from '../../api/client'
import { getTripDetail } from '../../api/trips'
import type { TripDetail, TripMember } from '../../api/trips'
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

type VoteProposal = {
  title: string
  description: string
  agreeCount: number
  disagreeCount: number
}

const DAY_OPTIONS = [1, 2, 3]

const PLANNED_PLACES_PREVIEW: ItineraryPlace[] = [
  {
    id: 'day1-1',
    day: 1,
    order: 1,
    title: '성산일출봉',
    category: '관광',
    time: '09:00',
    duration: '2시간',
    x: 67,
    y: 58,
    hours: '07:00 - 20:00, 계절별 변동 확인 필요',
    parking: '전용 주차장 있음, 성수기에는 주변 공영주차장 후보 필요',
    price: '성인 입장료 확인 예정',
    rating: '평점/리뷰 API 연동 예정',
    contentSummary: '일출 시간, 등반 난이도, 혼잡 시간대 블로그 요약을 제공할 예정입니다.',
    photoTip: '정상 전망, 해안 산책로, 일출 역광 구도 정보를 요약할 예정입니다.',
  },
  {
    id: 'day1-2',
    day: 1,
    order: 2,
    title: '섭지코지',
    category: '관광',
    time: '12:00',
    duration: '1.5시간',
    x: 71,
    y: 61,
    hours: '상시 개방 여부 확인 예정',
    parking: '입구 주차장과 도보 거리 비교 예정',
    price: '무료 또는 주차 비용 확인 예정',
    rating: '외부 장소 API 연동 예정',
    contentSummary: '바람, 산책 동선, 사진 명소 관련 후기를 요약할 예정입니다.',
    photoTip: '등대 방향, 해안 절벽, 말 조형물 주변 포즈 정보를 제공할 예정입니다.',
  },
  {
    id: 'day1-3',
    day: 1,
    order: 3,
    title: '해녀의 집',
    category: '식당',
    time: '14:00',
    duration: '1시간',
    memo: '해산물 정식 예약 완료',
    x: 63,
    y: 67,
    hours: '11:00 - 20:00, 브레이크 타임 확인 필요',
    parking: '식당 앞 주차 가능 여부 확인 필요',
    price: '1인 18,000 - 30,000원 예상',
    rating: '평점/리뷰 API 연동 예정',
    contentSummary: '대표 메뉴, 웨이팅, 회전율을 요약할 예정입니다.',
    photoTip: '해산물 한상 구도와 바다 배경 테이블샷 정보를 제공할 예정입니다.',
  },
  {
    id: 'day1-4',
    day: 1,
    order: 4,
    title: '우도',
    category: '관광',
    time: '16:00',
    duration: '3시간',
    x: 80,
    y: 54,
    hours: '배 운항 시간 확인 필요',
    parking: '선착장 주차장과 차량 반입 여부 확인 필요',
    price: '왕복 승선료 확인 예정',
    rating: '콘텐츠 기반 만족도 요약 예정',
    contentSummary: '배 시간, 이동수단, 체류 시간 관련 후기를 요약할 예정입니다.',
    photoTip: '검멀레 해변, 등대, 전기차 인증샷 정보를 제공할 예정입니다.',
  },
  {
    id: 'day2-1',
    day: 2,
    order: 1,
    title: '협재해수욕장',
    category: '관광',
    time: '10:00',
    duration: '2시간',
    x: 24,
    y: 50,
    hours: '상시 개방, 안전 통제 여부 확인 필요',
    parking: '해변 공영주차장 추천 예정',
    price: '무료',
    rating: '외부 장소 API 연동 예정',
    contentSummary: '물때, 날씨, 혼잡도 관련 후기를 요약할 예정입니다.',
    photoTip: '비양도 배경, 낮은 파도, 백사장 구도 정보를 제공할 예정입니다.',
  },
  {
    id: 'day2-2',
    day: 2,
    order: 2,
    title: '오설록 티뮤지엄',
    category: '카페',
    time: '13:30',
    duration: '1.5시간',
    x: 39,
    y: 58,
    hours: '09:00 - 18:00',
    parking: '전용 주차장 있음',
    price: '음료/디저트 7,000 - 15,000원 예상',
    rating: '평점/리뷰 API 연동 예정',
    contentSummary: '대표 메뉴, 굿즈, 대기 시간 후기를 요약할 예정입니다.',
    photoTip: '녹차밭 배경, 아이스크림 손샷, 건물 외관 구도 정보를 제공할 예정입니다.',
  },
  {
    id: 'day3-1',
    day: 3,
    order: 1,
    title: '동문시장',
    category: '식당',
    time: '11:00',
    duration: '2시간',
    x: 52,
    y: 42,
    hours: '매장별 상이',
    parking: '시장 공영주차장 혼잡도 확인 예정',
    price: '1인 10,000 - 25,000원 예상',
    rating: '리뷰/블로그 요약 예정',
    contentSummary: '야시장, 포장 메뉴, 웨이팅 정보를 요약할 예정입니다.',
    photoTip: '시장 입구, 먹거리 손샷, 야시장 네온 구도 정보를 제공할 예정입니다.',
  },
]

const CHAT_MESSAGES_PREVIEW = [
  { id: 1, author: '이영희', avatar: '이', time: '10:30', message: '성산일출봉 일출 보러 가는 거 시간 앞당길까요?' },
  { id: 2, author: '박민수', avatar: '박', time: '10:32', message: '좋아요! 새벽 5시에 출발하면 될 것 같아요' },
  { id: 3, author: '김철수', avatar: '김', time: '11:15', message: '해녀의 집 예약 완료했습니다' },
]

export function TripDetailPage({
  accessToken,
  tripId,
  user,
  onBackToMain,
  onLogout,
}: TripDetailPageProps) {
  const [trip, setTrip] = useState<TripDetail | null>(null)
  const [status, setStatus] = useState<AsyncStatus>('idle')
  const [errorMessage, setErrorMessage] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!accessToken || !tripId) {
      return
    }

    let ignore = false
    const timeoutId = window.setTimeout(() => {
      setStatus('loading')
      setErrorMessage('')

      void getTripDetail(accessToken, tripId)
        .then((response) => {
          if (ignore) {
            return
          }
          setTrip(response)
          setStatus('success')
        })
        .catch((error: unknown) => {
          if (ignore) {
            return
          }
          setStatus('error')
          setErrorMessage(toUserMessage(error))
        })
    }, 0)

    return () => {
      ignore = true
      window.clearTimeout(timeoutId)
    }
  }, [accessToken, tripId, reloadKey])

  return (
    <main className="trip-detail-page">
      {!accessToken && (
        <DetailStateCard
          title="로그인 확인 중"
          description="상세 페이지를 열기 위해 로그인 세션을 확인하고 있습니다."
          actionLabel="메인으로 돌아가기"
          onAction={onBackToMain}
        />
      )}

      {accessToken && status !== 'success' && (
        <DetailStateCard
          title={status === 'error' ? '여행 상세를 불러오지 못했습니다' : '여행 상세를 불러오는 중'}
          description={status === 'error' ? errorMessage : '여행 기본 정보와 참여자 정보를 조회하고 있습니다.'}
          actionLabel={status === 'error' ? '다시 시도' : undefined}
          onAction={status === 'error' ? () => setReloadKey((value) => value + 1) : undefined}
          secondaryActionLabel="메인으로 돌아가기"
          onSecondaryAction={onBackToMain}
        />
      )}

      {accessToken && status === 'success' && trip && (
        <TripPlanningWorkspace
          trip={trip}
          currentUser={user}
          onBackToMain={onBackToMain}
          onLogout={onLogout}
        />
      )}
    </main>
  )
}

function TripPlanningWorkspace({
  trip,
  currentUser,
  onBackToMain,
  onLogout,
}: {
  trip: TripDetail
  currentUser: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
}) {
  const [activeDay, setActiveDay] = useState(1)
  const [selectedPlaceId, setSelectedPlaceId] = useState('day1-2')
  const [editingPlaceId, setEditingPlaceId] = useState<string | null>(null)

  const activePlaces = useMemo(() => PLANNED_PLACES_PREVIEW.filter((place) => place.day === activeDay), [activeDay])
  const selectedPlace = activePlaces.find((place) => place.id === selectedPlaceId) ?? null
  const editingPlace = PLANNED_PLACES_PREVIEW.find((place) => place.id === editingPlaceId) ?? null
  const activeVote: VoteProposal | null = null
  const requiresInitialSetup = false

  function handleDayChange(day: number) {
    const firstPlace = PLANNED_PLACES_PREVIEW.find((place) => place.day === day)
    setActiveDay(day)
    setSelectedPlaceId(firstPlace?.id ?? '')
    setEditingPlaceId(null)
  }

  return (
    <section className="planning-board" aria-label="여행 상세 플래닝 보드">
      <PlanningHeader trip={trip} members={trip.members} onBackToMain={onBackToMain} onLogout={onLogout} />
      <div className="planning-layout">
        <ItinerarySidebar
          activeDay={activeDay}
          places={activePlaces}
          selectedPlaceId={selectedPlaceId}
          onDayChange={handleDayChange}
          onSelectPlace={setSelectedPlaceId}
        />
        <MapStage
          activeDay={activeDay}
          places={activePlaces}
          selectedPlace={selectedPlace}
          editingPlace={editingPlace}
          onSelectPlace={setSelectedPlaceId}
          onClosePlace={() => setSelectedPlaceId('')}
          onOpenAiEdit={(placeId) => setEditingPlaceId(placeId)}
          onCloseAiEdit={() => setEditingPlaceId(null)}
          activeVote={activeVote}
        />
        <TripChatPanel members={trip.members} currentUser={currentUser} />
      </div>
      {requiresInitialSetup && <InitialSetupFloatingPanel />}
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
        <button className="outline-action" type="button" disabled>
          공유
        </button>
        <button className="solid-action" type="button" disabled>
          저장
        </button>
        <button className="logout-text-button" type="button" onClick={onLogout}>
          로그아웃
        </button>
      </div>
    </header>
  )
}

function ItinerarySidebar({
  activeDay,
  places,
  selectedPlaceId,
  onDayChange,
  onSelectPlace,
}: {
  activeDay: number
  places: ItineraryPlace[]
  selectedPlaceId: string
  onDayChange: (day: number) => void
  onSelectPlace: (placeId: string) => void
}) {
  return (
    <aside className="itinerary-sidebar" aria-label="일차별 일정 목록">
      <div className="sidebar-heading">
        <span>AI route</span>
        <h2>{activeDay}일차 동선</h2>
        <p>마커를 누르면 장소 정보와 일정 수정 요청을 열 수 있습니다.</p>
      </div>
      <div className="day-tab-list" role="tablist" aria-label="일차 선택">
        {DAY_OPTIONS.map((day) => (
          <button
            className={activeDay === day ? 'active' : ''}
            key={day}
            type="button"
            role="tab"
            aria-selected={activeDay === day}
            onClick={() => onDayChange(day)}
          >
            {day}일차
          </button>
        ))}
      </div>
      <button className="add-place-button" type="button" disabled>
        + 장소 추가
      </button>
      <div className="route-summary-card">
        <span>예상 이동</span>
        <strong>4곳 · 약 42km</strong>
        <p>실제 이동 시간은 지도 API 연결 후 교통수단 기준으로 계산됩니다.</p>
      </div>
      <div className="itinerary-card-list">
        {places.map((place) => (
          <button
            className={`itinerary-card ${selectedPlaceId === place.id ? 'active' : ''}`}
            key={place.id}
            type="button"
            onClick={() => onSelectPlace(place.id)}
          >
            <span className="itinerary-order">{place.order}</span>
            <span className="itinerary-content">
              <strong>{place.title}</strong>
              <span className="itinerary-time">{place.time} · {place.duration}</span>
              <span className="category-chip">{place.category}</span>
              {place.memo && <span className="itinerary-memo">{place.memo}</span>}
            </span>
          </button>
        ))}
      </div>
    </aside>
  )
}

function MapStage({
  activeDay,
  places,
  selectedPlace,
  editingPlace,
  onSelectPlace,
  onClosePlace,
  onOpenAiEdit,
  onCloseAiEdit,
  activeVote,
}: {
  activeDay: number
  places: ItineraryPlace[]
  selectedPlace: ItineraryPlace | null
  editingPlace: ItineraryPlace | null
  onSelectPlace: (placeId: string) => void
  onClosePlace: () => void
  onOpenAiEdit: (placeId: string) => void
  onCloseAiEdit: () => void
  activeVote: VoteProposal | null
}) {
  return (
    <section className="map-stage" aria-label={`${activeDay}일차 지도`}>
      <div className="map-tiles" aria-hidden="true" />
      <div className="map-status-pill">
        <span>{activeDay}일차</span>
        <strong>{places.length}개 장소 표시 중</strong>
      </div>
      <div className="map-layer-selector" aria-label="지도 레이어">
        <button type="button" disabled>일정</button>
        <button type="button" disabled>주차</button>
        <button type="button" disabled>혼잡</button>
      </div>
      <div className="map-zoom-control" aria-label="지도 확대 축소">
        <button type="button" disabled>+</button>
        <button type="button" disabled>−</button>
      </div>
      <div className="map-route-line" aria-hidden="true" />
      {places.map((place) => (
        <button
          className={`map-pin ${selectedPlace?.id === place.id ? 'active' : ''}`}
          key={place.id}
          type="button"
          style={{ left: `${place.x}%`, top: `${place.y}%` }}
          onClick={() => onSelectPlace(place.id)}
          aria-label={`${place.order}번째 장소 ${place.title}`}
        >
          <span>{place.order}</span>
          <small>{place.title}</small>
        </button>
      ))}
      {selectedPlace && (
        <PlaceFloatingCard
          place={selectedPlace}
          onClose={onClosePlace}
          onOpenAiEdit={() => onOpenAiEdit(selectedPlace.id)}
        />
      )}
      {editingPlace && <AiEditFloatingPanel place={editingPlace} onClose={onCloseAiEdit} />}
      {activeVote && <VoteFloatingPanel proposal={activeVote} />}
    </section>
  )
}

function PlaceFloatingCard({
  place,
  onClose,
  onOpenAiEdit,
}: {
  place: ItineraryPlace
  onClose: () => void
  onOpenAiEdit: () => void
}) {
  return (
    <article className="place-floating-card" style={{ left: `${Math.min(Math.max(place.x - 12, 10), 46)}%`, top: `${Math.min(Math.max(place.y - 28, 12), 42)}%` }}>
      <button className="floating-close" type="button" onClick={onClose} aria-label="장소 정보 닫기">×</button>
      <span className="place-index">{place.order}번째 장소</span>
      <h2>{place.title}</h2>
      <p>{place.category} · {place.time} · {place.duration}</p>
      <dl className="place-info-list">
        <div>
          <dt>운영시간</dt>
          <dd>{place.hours}</dd>
        </div>
        <div>
          <dt>주차</dt>
          <dd>{place.parking}</dd>
        </div>
        <div>
          <dt>비용</dt>
          <dd>{place.price}</dd>
        </div>
        <div>
          <dt>평점/후기</dt>
          <dd>{place.rating}</dd>
        </div>
        <div>
          <dt>콘텐츠 요약</dt>
          <dd>{place.contentSummary}</dd>
        </div>
        <div>
          <dt>사진 스팟</dt>
          <dd>{place.photoTip}</dd>
        </div>
      </dl>
      <button className="ai-edit-open-button" type="button" onClick={onOpenAiEdit}>
        이 일정 AI로 수정 요청
      </button>
    </article>
  )
}

function AiEditFloatingPanel({ place, onClose }: { place: ItineraryPlace; onClose: () => void }) {
  return (
    <section className="ai-edit-floating-panel" aria-label="AI 일정 수정 요청">
      <button className="floating-close" type="button" onClick={onClose} aria-label="AI 수정 요청 닫기">×</button>
      <p className="floating-eyebrow">AI edit request</p>
      <h2>{place.title} 수정 요청</h2>
      <p>AI가 짜준 일정 중 선택한 슬롯을 어떻게 바꿀지 입력하는 영역입니다.</p>
      <textarea placeholder="예: 이 장소 대신 반경 2km 안에서 주차 가능한 카페 3개 추천해줘." disabled />
      <div className="radius-chip-row">
        <span>1km</span>
        <span>2km</span>
        <span>3km</span>
      </div>
      <button type="button" disabled>후보 추천 API 연결 예정</button>
    </section>
  )
}

function VoteFloatingPanel({ proposal }: { proposal: VoteProposal }) {
  return (
    <section className="vote-floating-panel" aria-label="변경 투표">
      <p className="floating-eyebrow">Vote</p>
      <h2>{proposal.title}</h2>
      <p>{proposal.description}</p>
      <div className="vote-result-row">
        <span>찬성 {proposal.agreeCount}</span>
        <span>반대 {proposal.disagreeCount}</span>
      </div>
      <div className="vote-button-row">
        <button type="button" disabled>찬성</button>
        <button type="button" disabled>반대</button>
      </div>
    </section>
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
    <aside className="chat-dock" aria-label="팀 채팅">
      <header className="chat-header">
        <div>
          <span aria-hidden="true">팀</span>
          <h2>팀 채팅</h2>
        </div>
        <button type="button" disabled>나가기</button>
      </header>
      <div className="online-member-row" aria-label="현재 접속한 유저">
        {members.map((member) => (
          <MemberAvatar member={member} key={member.userId} />
        ))}
        {currentUser && <span className="current-user-label">{currentUser.nickname} 접속 중</span>}
      </div>
      <div className="chat-message-list">
        {CHAT_MESSAGES_PREVIEW.map((message) => (
          <article className="chat-message" key={message.id}>
            <span className="chat-avatar">{message.avatar}</span>
            <div>
              <p className="chat-meta"><strong>{message.author}</strong><time>{message.time}</time></p>
              <p className="chat-bubble">{message.message}</p>
            </div>
          </article>
        ))}
      </div>
      <form className="chat-input-row">
        <input type="text" placeholder="메시지를 입력하세요..." disabled />
        <button type="button" disabled aria-label="메시지 전송">전송</button>
      </form>
    </aside>
  )
}

function InitialSetupFloatingPanel() {
  return (
    <section className="initial-setup-floating-panel" aria-label="방 생성자 맞춤 질문">
      <p className="floating-eyebrow">Initial setup</p>
      <h2>방 생성자 기본 질문</h2>
      <p>이 영역은 첫 방 생성 시에만 노출됩니다. 상세페이지 기본 화면에는 상시 노출하지 않습니다.</p>
    </section>
  )
}

function MemberAvatar({ member }: { member: TripMember }) {
  return (
    <span className="member-avatar" aria-label={member.nickname}>
      {member.profileImageUrl ? <img src={resolveBackendAssetUrl(member.profileImageUrl)} alt="" /> : member.nickname.slice(0, 1)}
    </span>
  )
}

function DetailStateCard({
  title,
  description,
  actionLabel,
  onAction,
  secondaryActionLabel,
  onSecondaryAction,
}: {
  title: string
  description: string
  actionLabel?: string
  onAction?: () => void
  secondaryActionLabel?: string
  onSecondaryAction?: () => void
}) {
  return (
    <section className="trip-detail-state-card" aria-live="polite">
      <h1>{title}</h1>
      <p>{description}</p>
      <div className="trip-state-actions">
        {actionLabel && onAction && <button type="button" onClick={onAction}>{actionLabel}</button>}
        {secondaryActionLabel && onSecondaryAction && <button type="button" onClick={onSecondaryAction}>{secondaryActionLabel}</button>}
      </div>
    </section>
  )
}

function formatDate(value: string) {
  return value.replaceAll('-', '.')
}

function resolveBackendAssetUrl(path: string) {
  if (path.startsWith('http://') || path.startsWith('https://')) {
    return path
  }
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
}

function toUserMessage(error: unknown) {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return '로그인이 만료되었습니다. 다시 로그인해 주세요.'
    }
    if (error.status === 404) {
      return '여행을 찾을 수 없거나 접근 권한이 없습니다.'
    }
    return error.message
  }
  return '요청 처리 중 오류가 발생했습니다.'
}
