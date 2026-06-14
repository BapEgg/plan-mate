import { useEffect, useMemo, useState } from 'react'
import type { ChangeEvent, FormEvent } from 'react'
import type { AuthUser } from '../../api/auth'
import { API_BASE_URL, ApiError } from '../../api/client'
import { createTrip, listMyTrips } from '../../api/trips'
import type { CreateTripRequest, TripStatus, TripSummary } from '../../api/trips'
import { clearMyProfileImage, getMe, updateMyNickname, updateMyProfileImage } from '../../api/users'
import type { MeProfile } from '../../api/users'
import './MainPage.css'

type MainPageProps = {
  accessToken: string
  user: AuthUser | null
  onLogout: () => void
  onOpenTrip: (tripId: string) => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'
type NoticeTone = 'info' | 'success' | 'error'

type DashboardNotice = {
  tone: NoticeTone
  message: string
}

type TripStats = {
  total: number
  planning: number
  upcoming: number
  completed: number
}

const PROFILE_IMAGE_MAX_BYTES = 2_097_152

export function MainPage({ accessToken, user, onLogout, onOpenTrip }: MainPageProps) {
  const [profile, setProfile] = useState<MeProfile | null>(null)
  const [profileStatus, setProfileStatus] = useState<AsyncStatus>('idle')
  const [nicknameSaveStatus, setNicknameSaveStatus] = useState<AsyncStatus>('idle')
  const [trips, setTrips] = useState<TripSummary[]>([])
  const [tripsStatus, setTripsStatus] = useState<AsyncStatus>('idle')
  const [createStatus, setCreateStatus] = useState<AsyncStatus>('idle')
  const [notice, setNotice] = useState<DashboardNotice | null>(null)
  const [createPanelOpen, setCreatePanelOpen] = useState(false)

  const displayName = profile?.nickname ?? user?.nickname ?? '여행자'

  const tripStats = useMemo<TripStats>(() => ({
    total: trips.length,
    planning: trips.filter((trip) => trip.status === 'PLANNING').length,
    upcoming: trips.filter((trip) => trip.status === 'UPCOMING').length,
    completed: trips.filter((trip) => trip.status === 'COMPLETED').length,
  }), [trips])

  useEffect(() => {
    if (!accessToken) {
      return
    }

    let ignore = false
    const timeoutId = window.setTimeout(() => {
      setProfileStatus('loading')

      void getMe(accessToken)
        .then((response) => {
          if (ignore) {
            return
          }
          setProfile(response)
          setProfileStatus('success')
        })
        .catch((error: unknown) => {
          if (ignore) {
            return
          }
          setProfileStatus('error')
          setNotice({ tone: 'error', message: toUserMessage(error) })
        })
    }, 0)

    return () => {
      ignore = true
      window.clearTimeout(timeoutId)
    }
  }, [accessToken])

  useEffect(() => {
    if (!accessToken) {
      return
    }

    let ignore = false
    const timeoutId = window.setTimeout(() => {
      setTripsStatus('loading')

      void listMyTrips(accessToken)
        .then((response) => {
          if (ignore) {
            return
          }
          setTrips(response)
          setTripsStatus('success')
        })
        .catch((error: unknown) => {
          if (ignore) {
            return
          }


          setTripsStatus('error')
          setNotice({ tone: 'error', message: toUserMessage(error) })
        })
    }, 0)

    return () => {
      ignore = true
      window.clearTimeout(timeoutId)
    }
  }, [accessToken])

  async function handleSaveNickname(nickname: string) {
    if (!accessToken || !profile) {
      return
    }

    setNicknameSaveStatus('loading')
    setNotice(null)

    try {
      const updated = await updateMyNickname(accessToken, { nickname })
      setProfile(updated)
      setNicknameSaveStatus('success')
      setNotice({ tone: 'success', message: '닉네임을 저장했습니다.' })
    } catch (error: unknown) {

      setNicknameSaveStatus('error')
      setNotice({ tone: 'error', message: toUserMessage(error) })
    }
  }

  async function handleChangeProfileImage(image: File | null) {
    if (!accessToken || !profile) {
      return
    }

    try {
      const updated = image
        ? await updateMyProfileImage(accessToken, image)
        : await clearMyProfileImage(accessToken)
      setProfile(updated)
      setNotice({ tone: 'success', message: image ? '프로필 이미지를 변경했습니다.' : '프로필 이미지를 삭제했습니다.' })
    } catch (error: unknown) {
      setNotice({ tone: 'error', message: toUserMessage(error) })
    }
  }

  async function handleCreateTrip(payload: CreateTripRequest) {
    if (!accessToken) {
      return
    }

    setCreateStatus('loading')
    setNotice(null)

    try {
      const created = await createTrip(accessToken, payload)
      setTrips((currentTrips) => [created, ...currentTrips])
      setCreateStatus('success')
      setCreatePanelOpen(false)
      setNotice({ tone: 'success', message: '새 여행을 생성했습니다.' })
    } catch (error: unknown) {

      setCreateStatus('error')
      setNotice({ tone: 'error', message: toUserMessage(error) })
    }
  }

  function handleReloadTrips() {
    if (!accessToken) {
      return
    }
    setTripsStatus('loading')
    void listMyTrips(accessToken)
      .then((response) => {
        setTrips(response)
        setTripsStatus('success')
        setNotice({ tone: 'success', message: '여행 목록을 다시 불러왔습니다.' })
      })
      .catch((error: unknown) => {
        setTripsStatus('error')
        setNotice({ tone: 'error', message: toUserMessage(error) })
      })
  }

  return (
    <main className="dashboard-page">
      <div className="dashboard-map-grid" aria-hidden="true" />
      <MainHeader displayName={displayName} onLogout={onLogout} />

      <section className="dashboard-shell" aria-label="메인 대시보드">
        <DashboardHero
          displayName={displayName}
          tripStats={tripStats}
          onCreateTrip={() => setCreatePanelOpen(true)}
        />

        {notice && <DashboardNoticeBanner notice={notice} onClose={() => setNotice(null)} />}

        <div className="dashboard-content-grid">
          <ProfileCard
            profile={profile}
            fallbackUser={user}
            status={profileStatus}
            saveStatus={nicknameSaveStatus}
            profileImageUrl={profile?.profileImageUrl ?? null}
            onSaveNickname={handleSaveNickname}
            onChangeProfileImage={handleChangeProfileImage}
            onImageError={(message) => setNotice({ tone: 'error', message })}
          />
          <TripDashboard
            trips={trips}
            status={tripsStatus}
            onCreateTrip={() => setCreatePanelOpen(true)}
            onOpenTrip={onOpenTrip}
            onReloadTrips={handleReloadTrips}
          />
        </div>
      </section>

      {createPanelOpen && (
        <CreateTripPanel
          status={createStatus}
          onClose={() => setCreatePanelOpen(false)}
          onSubmit={handleCreateTrip}
        />
      )}
    </main>
  )
}


function MainHeader({ displayName, onLogout }: { displayName: string; onLogout: () => void }) {
  return (
    <nav className="dashboard-nav" aria-label="메인 내비게이션">
      <div className="dashboard-brand">
        <span className="dashboard-brand-mark" aria-hidden="true">PM</span>
        <strong>PlanMate</strong>
      </div>
      <div className="dashboard-user-menu">
        <span>{displayName}</span>
        <button className="ghost-button" type="button" onClick={onLogout}>
          로그아웃
        </button>
      </div>
    </nav>
  )
}

function DashboardHero({
  displayName,
  tripStats,
  onCreateTrip,
}: {
  displayName: string
  tripStats: TripStats
  onCreateTrip: () => void
}) {
  return (
    <section className="dashboard-hero">
      <div>
        <p className="eyebrow">Travel command center</p>
        <h1>{displayName}님, 실행 가능한 여행 계획을 시작하세요.</h1>
        <p>
          프로필을 확인하고, 내 여행 목록을 관리하고, 새 여행을 만든 뒤 상세 화면으로 넘어갈 수 있는
          메인 대시보드입니다.
        </p>
        <div className="main-actions">
          <button className="primary-action" type="button" onClick={onCreateTrip}>
            새 여행 만들기
          </button>
          <a className="secondary-link-action" href="#my-trips">
            내 여행 보기
          </a>
        </div>
      </div>
      <div className="trip-stat-board" aria-label="여행 요약">
        <StatCard label="전체 여행" value={tripStats.total} />
        <StatCard label="계획중" value={tripStats.planning} />
        <StatCard label="예정" value={tripStats.upcoming} />
        <StatCard label="완료" value={tripStats.completed} />
      </div>
    </section>
  )
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="stat-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function DashboardNoticeBanner({
  notice,
  onClose,
}: {
  notice: DashboardNotice
  onClose: () => void
}) {
  return (
    <div className={`dashboard-notice ${notice.tone}`} role="status">
      <p>{notice.message}</p>
      <button type="button" onClick={onClose} aria-label="알림 닫기">
        닫기
      </button>
    </div>
  )
}

function ProfileCard({
  profile,
  fallbackUser,
  status,
  saveStatus,
  profileImageUrl,
  onSaveNickname,
  onChangeProfileImage,
  onImageError,
}: {
  profile: MeProfile | null
  fallbackUser: AuthUser | null
  status: AsyncStatus
  saveStatus: AsyncStatus
  profileImageUrl: string | null
  onSaveNickname: (nickname: string) => Promise<void>
  onChangeProfileImage: (image: File | null) => Promise<void>
  onImageError: (message: string) => void
}) {
  const [editing, setEditing] = useState(false)
  const nickname = profile?.nickname ?? fallbackUser?.nickname ?? ''

  return (
    <section className="dashboard-card profile-card" aria-labelledby="profile-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">My profile</p>
          <h2 id="profile-title">내 프로필</h2>
        </div>
        <span className={`status-pill ${status}`}>{statusLabel(status)}</span>
      </div>

      {editing ? (
        <NicknameEditForm
          initialNickname={nickname}
          status={saveStatus}
          onCancel={() => setEditing(false)}
          onSubmit={async (nextNickname) => {
            await onSaveNickname(nextNickname)
            setEditing(false)
          }}
        />
      ) : (
        <>
          <div className="profile-identity">
            <ProfileAvatar profileImageUrl={profileImageUrl} nickname={nickname} />
            <div>
              <strong>{nickname || '프로필을 불러오는 중입니다.'}</strong>
              <p>{profile?.email ?? '이메일 정보를 확인 중입니다.'}</p>
            </div>
          </div>

          <ProfileImageControls
            disabled={!profile}
            hasProfileImage={Boolean(profileImageUrl)}
            onChangeProfileImage={onChangeProfileImage}
            onImageError={onImageError}
          />

          <dl className="profile-meta-list">
            <div>
              <dt>로그인 ID</dt>
              <dd>{profile?.loginId ?? fallbackUser?.loginId ?? 'OAuth2 계정'}</dd>
            </div>
            <div>
              <dt>이메일 인증</dt>
              <dd>{profile?.emailVerified ? '완료' : '확인 중'}</dd>
            </div>
            <div>
              <dt>연동 계정</dt>
              <dd>{formatProviders(profile?.linkedProviders)}</dd>
            </div>
          </dl>

          <button
            className="secondary-action full-width-action"
            type="button"
            disabled={!profile}
            onClick={() => setEditing(true)}
          >
            닉네임 수정
          </button>
        </>
      )}
    </section>
  )
}

function ProfileAvatar({ profileImageUrl, nickname }: { profileImageUrl: string | null; nickname: string }) {
  return (
    <div className="profile-avatar" aria-label="프로필 이미지">
      {profileImageUrl ? (
        <img src={resolveBackendAssetUrl(profileImageUrl)} alt="" />
      ) : (
        <span aria-hidden="true">{nickname.slice(0, 1) || 'P'}</span>
      )}
    </div>
  )
}

function ProfileImageControls({
  disabled,
  hasProfileImage,
  onChangeProfileImage,
  onImageError,
}: {
  disabled: boolean
  hasProfileImage: boolean
  onChangeProfileImage: (image: File | null) => Promise<void>
  onImageError: (message: string) => void
}) {
  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''

    if (!file) {
      return
    }

    if (!file.type.startsWith('image/')) {
      onImageError('이미지 파일만 선택할 수 있습니다.')
      return
    }

    if (file.size > PROFILE_IMAGE_MAX_BYTES) {
      onImageError('프로필 이미지는 1.5MB 이하 파일만 사용할 수 있습니다.')
      return
    }

    void onChangeProfileImage(file)
  }

  return (
    <div className="profile-image-controls">
      <label className={`profile-image-action ${disabled ? 'disabled' : ''}`}>
        이미지 변경
        <input type="file" accept="image/*" disabled={disabled} onChange={handleFileChange} />
      </label>
      <button
        className="profile-image-remove"
        type="button"
        disabled={disabled || !hasProfileImage}
        onClick={() => void onChangeProfileImage(null)}
      >
        기본 이미지
      </button>
      <p>실시간 채팅, 지도 마커, 여행 멤버 표시에서 재사용할 프로필 이미지입니다.</p>
    </div>
  )
}

function NicknameEditForm({
  initialNickname,
  status,
  onCancel,
  onSubmit,
}: {
  initialNickname: string
  status: AsyncStatus
  onCancel: () => void
  onSubmit: (nickname: string) => Promise<void>
}) {
  const [nickname, setNickname] = useState(initialNickname)
  const trimmedNickname = nickname.trim()
  const nicknameInvalid = trimmedNickname.length < 2 || trimmedNickname.length > 30

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (nicknameInvalid) {
      return
    }
    await onSubmit(trimmedNickname)
  }

  return (
    <form className="nickname-form" onSubmit={handleSubmit}>
      <label>
        <span>닉네임</span>
        <input
          name="nickname"
          type="text"
          minLength={2}
          maxLength={30}
          value={nickname}
          onChange={(event) => setNickname(event.target.value)}
          aria-invalid={nicknameInvalid}
          required
        />
      </label>
      <p className="form-guide">닉네임은 2자 이상 30자 이하로 입력하세요.</p>
      <div className="form-button-row">
        <button className="primary-action" type="submit" disabled={nicknameInvalid || status === 'loading'}>
          저장
        </button>
        <button className="secondary-action" type="button" onClick={onCancel}>
          취소
        </button>
      </div>
    </form>
  )
}

function TripDashboard({
  trips,
  status,
  onCreateTrip,
  onOpenTrip,
  onReloadTrips,
}: {
  trips: TripSummary[]
  status: AsyncStatus
  onCreateTrip: () => void
  onOpenTrip: (tripId: string) => void
  onReloadTrips: () => void
}) {
  return (
    <section className="dashboard-card trip-dashboard" id="my-trips" aria-labelledby="trips-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">My trips</p>
          <h2 id="trips-title">내 여행 목록</h2>
        </div>
        <button className="compact-action" type="button" onClick={onReloadTrips}>
          새로고침
        </button>
      </div>


      {status === 'loading' && <TripSkeletonList />}
      {status === 'error' && (
        <div className="empty-trip-state">
          <strong>여행 목록을 불러오지 못했습니다.</strong>
          <p>잠시 후 다시 시도하거나 로그인 상태를 확인하세요.</p>
          <button className="secondary-action" type="button" onClick={onReloadTrips}>
            다시 불러오기
          </button>
        </div>
      )}
      {status !== 'loading' && status !== 'error' && trips.length === 0 && (
        <EmptyTripState onCreateTrip={onCreateTrip} />
      )}
      {status !== 'loading' && status !== 'error' && trips.length > 0 && (
        <div className="trip-card-grid">
          {trips.map((trip) => (
            <TripCard key={trip.id} trip={trip} onOpen={() => onOpenTrip(trip.id)} />
          ))}
        </div>
      )}
    </section>
  )
}

function EmptyTripState({ onCreateTrip }: { onCreateTrip: () => void }) {
  return (
    <div className="empty-trip-state">
      <span className="empty-trip-icon" aria-hidden="true">+</span>
      <strong>아직 생성한 여행이 없습니다.</strong>
      <p>첫 여행을 만들면 이 영역에 여행 카드가 표시되고 상세 화면으로 이동할 수 있습니다.</p>
      <button className="primary-action" type="button" onClick={onCreateTrip}>
        첫 여행 만들기
      </button>
    </div>
  )
}

function TripCard({ trip, onOpen }: { trip: TripSummary; onOpen: () => void }) {
  return (
    <article className="trip-card">
      <div className="trip-card-topline">
        <span className={`trip-status ${trip.status.toLowerCase()}`}>{tripStatusLabel(trip.status)}</span>
      </div>
      <h3>{trip.title}</h3>
      <p>{trip.destination}</p>
      <div className="trip-card-meta">
        <span>{formatDate(trip.startDate)} - {formatDate(trip.endDate)}</span>
        <span>{durationLabel(trip.startDate, trip.endDate)}</span>
        <span>멤버 {trip.memberCount}명</span>
      </div>
      <button className="trip-open-button" type="button" onClick={onOpen}>
        상세 진입 준비
      </button>
    </article>
  )
}

function TripSkeletonList() {
  return (
    <div className="trip-card-grid" aria-label="여행 목록 로딩 중">
      {[0, 1, 2].map((item) => (
        <div className="trip-skeleton-card" key={item}>
          <span />
          <strong />
          <p />
        </div>
      ))}
    </div>
  )
}

function CreateTripPanel({
  status,
  onClose,
  onSubmit,
}: {
  status: AsyncStatus
  onClose: () => void
  onSubmit: (payload: CreateTripRequest) => Promise<void>
}) {
  const [formError, setFormError] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const payload = {
      title: String(form.get('title') ?? '').trim(),
      destination: String(form.get('destination') ?? '').trim(),
      startDate: String(form.get('startDate') ?? ''),
      endDate: String(form.get('endDate') ?? ''),
    }

    if (!payload.title || !payload.destination || !payload.startDate || !payload.endDate) {
      setFormError('모든 항목을 입력하세요.')
      return
    }

    if (payload.startDate > payload.endDate) {
      setFormError('종료일은 시작일 이후여야 합니다.')
      return
    }

    setFormError('')
    await onSubmit(payload)
  }

  return (
    <div className="create-trip-backdrop" role="presentation">
      <section className="create-trip-panel" role="dialog" aria-modal="true" aria-labelledby="create-trip-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">New trip</p>
            <h2 id="create-trip-title">새 여행 만들기</h2>
          </div>
          <button className="compact-action" type="button" onClick={onClose}>
            닫기
          </button>
        </div>

        <form className="create-trip-form" onSubmit={handleSubmit}>
          <label>
            <span>여행 제목</span>
            <input name="title" type="text" placeholder="예: 강릉 2박 3일" maxLength={60} required />
          </label>
          <label>
            <span>대표 여행지</span>
            <input name="destination" type="text" placeholder="예: 강릉" maxLength={60} required />
          </label>
          <div className="date-input-grid">
            <label>
              <span>시작일</span>
              <input name="startDate" type="date" required />
            </label>
            <label>
              <span>종료일</span>
              <input name="endDate" type="date" required />
            </label>
          </div>
          {formError && <p className="field-error">{formError}</p>}
          <p className="form-guide">
            숙소, 예산, 이동수단, 취향 입력은 다음 단계의 여행 상세 화면에서 확장합니다.
          </p>
          <button className="primary-action" type="submit" disabled={status === 'loading'}>
            여행 카드 생성
          </button>
        </form>
      </section>
    </div>
  )
}




function tripStatusLabel(status: TripStatus) {
  const labels: Record<TripStatus, string> = {
    PLANNING: '계획중',
    UPCOMING: '예정',
    COMPLETED: '완료',
  }
  return labels[status]
}

function statusLabel(status: AsyncStatus) {
  const labels: Record<AsyncStatus, string> = {
    idle: '대기',
    loading: '조회중',
    success: '정상',
    error: '오류',
  }
  return labels[status]
}

function formatProviders(providers?: string[]) {
  if (!providers || providers.length === 0) {
    return '없음'
  }
  return providers.join(', ')
}

function formatDate(value: string) {
  return value.replaceAll('-', '.')
}

function durationLabel(startDate: string, endDate: string) {
  const start = new Date(`${startDate}T00:00:00`)
  const end = new Date(`${endDate}T00:00:00`)
  const diff = Math.floor((end.getTime() - start.getTime()) / 86_400_000) + 1

  if (Number.isNaN(diff) || diff <= 0) {
    return '기간 확인 필요'
  }
  return `${diff}일`
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
      return '로그인이 만료되었습니다. 다시 로그인하세요.'
    }
    return error.message
  }
  return '요청 처리 중 오류가 발생했습니다.'
}
