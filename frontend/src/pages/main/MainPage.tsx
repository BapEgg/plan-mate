import { useEffect, useMemo, useRef, useState } from 'react'
import type { ChangeEvent, FormEvent, MouseEvent, RefObject } from 'react'
import type { AuthUser } from '../../api/auth'
import { API_BASE_URL, ApiError } from '../../api/client'
import { deleteTrip, listMyTrips } from '../../api/trips'
import type { TripStatus, TripSummary } from '../../api/trips'
import { clearMyProfileImage, getMe, updateMyNickname, updateMyProfileImage } from '../../api/users'
import type { MeProfile } from '../../api/users'
import { getInboxSummary } from '../../api/inbox'
import { InboxDialog } from './InboxDialog'
import './MainPage.css'
import './MainPageEmphasis.css'

type MainPageProps = {
  accessToken: string
  user: AuthUser | null
  onLogout: () => void
  onCreateTrip: () => void
  onOpenTrip: (tripId: string) => void
}

type AsyncStatus = 'idle' | 'loading' | 'success' | 'error'
type NoticeTone = 'info' | 'success' | 'error'

type DashboardNotice = {
  tone: NoticeTone
  message: string
}

type TripFilter = 'ALL' | TripStatus

const PROFILE_IMAGE_MAX_BYTES = 2_097_152

export function MainPage({ accessToken, user, onLogout, onCreateTrip, onOpenTrip }: MainPageProps) {
  const [profile, setProfile] = useState<MeProfile | null>(null)
  const [profileStatus, setProfileStatus] = useState<AsyncStatus>('idle')
  const [nicknameSaveStatus, setNicknameSaveStatus] = useState<AsyncStatus>('idle')
  const [trips, setTrips] = useState<TripSummary[]>([])
  const [tripsStatus, setTripsStatus] = useState<AsyncStatus>('idle')
  const [notice, setNotice] = useState<DashboardNotice | null>(null)
  const [tripFilter, setTripFilter] = useState<TripFilter>('ALL')
  const [isDeleteMode, setIsDeleteMode] = useState(false)
  const [selectedTripIds, setSelectedTripIds] = useState<string[]>([])
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false)
  const [tripDeleteStatus, setTripDeleteStatus] = useState<AsyncStatus>('idle')
  const [isProfileOpen, setIsProfileOpen] = useState(false)
  const profileDialogRef = useRef<HTMLElement>(null)
  const profileButtonRef = useRef<HTMLButtonElement>(null)
  const deleteDialogRef = useRef<HTMLDialogElement>(null)
  const [isInboxOpen, setIsInboxOpen] = useState(false)
  const [inboxCount, setInboxCount] = useState(0)
  const inboxButtonRef = useRef<HTMLButtonElement>(null)

  const displayName = profile?.nickname ?? user?.nickname ?? '여행자'
  const tripCounts = useMemo(() => ({
    ALL: trips.length,
    PLANNING: trips.filter((trip) => trip.status === 'PLANNING').length,
    UPCOMING: trips.filter((trip) => trip.status === 'UPCOMING').length,
    COMPLETED: trips.filter((trip) => trip.status === 'COMPLETED').length,
  }), [trips])
  const featuredTrip = useMemo(() => [...trips].sort(compareTripsForFeature)[0] ?? null, [trips])
  const filteredTrips = useMemo(
    () => tripFilter === 'ALL' ? trips : trips.filter((trip) => trip.status === tripFilter),
    [tripFilter, trips],
  )
  const selectedTrips = useMemo(
    () => trips.filter((trip) => selectedTripIds.includes(trip.id)),
    [selectedTripIds, trips],
  )
  const featuredHeading = featuredTripSectionCopy(featuredTrip)

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
    void getInboxSummary(accessToken)
      .then((summary) => {
        if (!ignore) {
          setInboxCount(summary.tripInvitationCount + summary.friendRequestCount + summary.ownerTransferRequestCount)
        }
      })
      .catch(() => {
        // 봉투 badge는 부가 정보다 — 실패해도 나머지 대시보드를 막지 않는다.
      })
    return () => {
      ignore = true
    }
  }, [accessToken, isInboxOpen])

  useEffect(() => {
    if (!isProfileOpen) {
      return
    }

    const profileButton = profileButtonRef.current
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.requestAnimationFrame(() => profileDialogRef.current?.focus())

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsProfileOpen(false)
        return
      }

      if (event.key !== 'Tab' || !profileDialogRef.current) {
        return
      }

      const focusable = Array.from(
        profileDialogRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), input:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
        ),
      )
      const first = focusable[0]
      const last = focusable.at(-1)

      if (!first || !last) {
        event.preventDefault()
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
      window.requestAnimationFrame(() => profileButton?.focus())
    }
  }, [isProfileOpen])

  useEffect(() => {
    const dialog = deleteDialogRef.current
    if (!dialog) {
      return
    }

    if (isDeleteConfirmOpen && !dialog.open) {
      dialog.showModal()
    }

    if (!isDeleteConfirmOpen && dialog.open) {
      dialog.close()
    }
  }, [isDeleteConfirmOpen])

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

  function handleToggleDeleteMode() {
    if (tripDeleteStatus === 'loading') {
      return
    }

    setIsDeleteMode((current) => {
      if (current) {
        setSelectedTripIds([])
      }
      return !current
    })
    setIsDeleteConfirmOpen(false)
    setTripDeleteStatus('idle')
  }

  function handleToggleTripSelection(tripId: string) {
    setSelectedTripIds((current) => current.includes(tripId)
      ? current.filter((selectedId) => selectedId !== tripId)
      : [...current, tripId])
  }

  function handleTripFilterChange(filter: TripFilter) {
    setTripFilter(filter)
    if (isDeleteMode) {
      setSelectedTripIds([])
    }
  }

  async function handleDeleteSelectedTrips() {
    if (!accessToken || selectedTrips.length === 0 || tripDeleteStatus === 'loading') {
      return
    }

    setTripDeleteStatus('loading')
    setNotice(null)

    const results = await Promise.allSettled(
      selectedTrips.map((trip) => deleteTrip(accessToken, trip.id)),
    )
    const deletedIds = selectedTrips
      .filter((_, index) => results[index].status === 'fulfilled')
      .map((trip) => trip.id)
    const failedIds = selectedTrips
      .filter((_, index) => results[index].status === 'rejected')
      .map((trip) => trip.id)

    if (deletedIds.length > 0) {
      setTrips((current) => current.filter((trip) => !deletedIds.includes(trip.id)))
    }

    setIsDeleteConfirmOpen(false)

    if (failedIds.length === 0) {
      setSelectedTripIds([])
      setIsDeleteMode(false)
      setTripDeleteStatus('success')
      setNotice({ tone: 'success', message: `여행 ${deletedIds.length}개를 삭제했습니다.` })
    } else {
      setSelectedTripIds(failedIds)
      setTripDeleteStatus('error')
      setNotice({
        tone: 'error',
        message: `${deletedIds.length}개는 삭제했고 ${failedIds.length}개는 삭제하지 못했습니다. 다시 시도해 주세요.`,
      })
    }
  }

  return (
    <main className="dashboard-page">
      <a className="dashboard-skip-link" href="#my-trips">여행 목록으로 바로가기</a>
      <div className="dashboard-map-grid" aria-hidden="true" />
      <div className="dashboard-app-shell" aria-hidden={isProfileOpen || undefined}>
        <MainHeader
          displayName={displayName}
          profileImageUrl={profile?.profileImageUrl ?? null}
          profileButtonRef={profileButtonRef}
          isProfileOpen={isProfileOpen}
          onCreateTrip={onCreateTrip}
          onOpenProfile={() => setIsProfileOpen(true)}
          inboxButtonRef={inboxButtonRef}
          inboxCount={inboxCount}
          onOpenInbox={() => setIsInboxOpen(true)}
        />

        <section className="dashboard-shell" aria-labelledby="dashboard-title">
          <header className="dashboard-intro">
            <h1 id="dashboard-title"><strong translate="no">PlanMate</strong></h1>
          </header>

          {notice && <DashboardNoticeBanner notice={notice} onClose={() => setNotice(null)} />}

          <section className="dashboard-focus-grid" aria-labelledby="featured-trip-title">
            <div className="dashboard-section-heading">
              <h2 id="featured-trip-title">{featuredHeading.title}</h2>
              {featuredHeading.badge && (
                <span className={`departure-countdown ${featuredHeading.tone}`}>{featuredHeading.badge}</span>
              )}
            </div>
            <FeaturedTrip
              trip={featuredTrip}
              status={tripsStatus}
              onOpenTrip={onOpenTrip}
              onCreateTrip={onCreateTrip}
              onReloadTrips={handleReloadTrips}
            />
          </section>

          <TripDashboard
            trips={filteredTrips}
            totalTripCount={trips.length}
            counts={tripCounts}
            activeFilter={tripFilter}
            status={tripsStatus}
            isDeleteMode={isDeleteMode}
            selectedTripIds={selectedTripIds}
            onFilterChange={handleTripFilterChange}
            onCreateTrip={onCreateTrip}
            onOpenTrip={onOpenTrip}
            onReloadTrips={handleReloadTrips}
            onToggleDeleteMode={handleToggleDeleteMode}
            onToggleTripSelection={handleToggleTripSelection}
            onRequestDeleteConfirmation={() => setIsDeleteConfirmOpen(true)}
          />
        </section>
      </div>

      {isInboxOpen && accessToken && (
        <InboxDialog
          accessToken={accessToken}
          onClose={() => setIsInboxOpen(false)}
          onTripInvitationAccepted={() => handleReloadTrips()}
          triggerRef={inboxButtonRef}
        />
      )}

      {isProfileOpen && (
        <div
          className="profile-drawer-layer"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setIsProfileOpen(false)
            }
          }}
        >
          <aside
            className="profile-drawer"
            ref={profileDialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="profile-title"
            tabIndex={-1}
          >
            <button
              className="profile-drawer-close"
              type="button"
              aria-label="프로필 닫기"
              onClick={() => setIsProfileOpen(false)}
            >
              <span aria-hidden="true">×</span>
            </button>
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
            <button className="profile-logout" type="button" onClick={onLogout}>
              로그아웃
            </button>
          </aside>
        </div>
      )}

      <dialog
        className="trip-delete-dialog"
        ref={deleteDialogRef}
        aria-labelledby="trip-delete-title"
        onCancel={(event) => {
          if (tripDeleteStatus === 'loading') {
            event.preventDefault()
            return
          }
          setIsDeleteConfirmOpen(false)
        }}
        onClick={(event) => {
          if (event.target === event.currentTarget && tripDeleteStatus !== 'loading') {
            setIsDeleteConfirmOpen(false)
          }
        }}
      >
        <div className="trip-delete-dialog-content">
          <p>여행 삭제</p>
          <h2 id="trip-delete-title">선택한 여행 {selectedTrips.length}개를 삭제할까요?</h2>
          <ul className="trip-delete-list">
            {selectedTrips.slice(0, 3).map((trip) => <li key={trip.id}>{trip.title}</li>)}
            {selectedTrips.length > 3 && <li>외 {selectedTrips.length - 3}개</li>}
          </ul>
          <strong>선택한 여행과 저장된 일정은 삭제 후 되돌릴 수 없습니다.</strong>
          <div className="trip-delete-dialog-actions">
            <button type="button" onClick={() => setIsDeleteConfirmOpen(false)} disabled={tripDeleteStatus === 'loading'}>
              취소
            </button>
            <button className="danger" type="button" onClick={() => void handleDeleteSelectedTrips()} disabled={tripDeleteStatus === 'loading'}>
              {tripDeleteStatus === 'loading' ? '삭제 중…' : `${selectedTrips.length}개 삭제`}
            </button>
          </div>
        </div>
      </dialog>
    </main>
  )
}


function MainHeader({
  displayName,
  profileImageUrl,
  profileButtonRef,
  isProfileOpen,
  onCreateTrip,
  onOpenProfile,
  inboxButtonRef,
  inboxCount,
  onOpenInbox,
}: {
  displayName: string
  profileImageUrl: string | null
  profileButtonRef: RefObject<HTMLButtonElement | null>
  isProfileOpen: boolean
  onCreateTrip: () => void
  onOpenProfile: () => void
  inboxButtonRef: RefObject<HTMLButtonElement | null>
  inboxCount: number
  onOpenInbox: () => void
}) {
  return (
    <nav className="dashboard-nav" aria-label="메인 내비게이션">
      <a className="dashboard-brand" href="/main" aria-label="PlanMate 여행 홈">
        <PlanMateMark />
      </a>
      <div className="dashboard-nav-actions">
        <a className="header-create-button" href="/trips/new" onClick={(event) => handleSpaNavigation(event, onCreateTrip)}>
          새 여행 <span aria-hidden="true">+</span>
        </a>
        <button
          aria-label={inboxCount > 0 ? `초대함 열기, 안 읽은 항목 ${inboxCount}개` : '초대함 열기'}
          className="inbox-trigger-button"
          onClick={onOpenInbox}
          ref={inboxButtonRef}
          type="button"
        >
          <span aria-hidden="true">✉</span>
          {inboxCount > 0 && <span className="inbox-trigger-badge">{inboxCount > 9 ? '9+' : inboxCount}</span>}
        </button>
        <button
          className="profile-menu-button"
          ref={profileButtonRef}
          type="button"
          aria-label={`${displayName} 프로필 열기`}
          aria-haspopup="dialog"
          aria-expanded={isProfileOpen}
          onClick={onOpenProfile}
        >
          <ProfileAvatar profileImageUrl={profileImageUrl} nickname={displayName} />
        </button>
      </div>
    </nav>
  )
}

function PlanMateMark() {
  return (
    <svg viewBox="0 0 44 44" aria-hidden="true">
      <rect width="44" height="44" rx="12" fill="#5278bc" />
      <path d="M9 12.5 18.5 9l8 3 8.5-3v22.5l-8.5 3-8-3L9 35V12.5Z" fill="#f8fbfc" />
      <path d="m18.5 9v22.5m8-19.5v22.5" fill="none" stroke="#b8cce8" strokeWidth="1.8" />
      <path d="M12.5 26c4-6 8.5-7.5 13-4.5 2.5 1.7 4.4 1.1 6-1" fill="none" stroke="#5278bc" strokeLinecap="round" strokeWidth="2" />
      <circle cx="31.5" cy="20.5" r="2.7" fill="#c96f5a" />
    </svg>
  )
}

function FeaturedTrip({
  trip,
  status,
  onOpenTrip,
  onCreateTrip,
  onReloadTrips,
}: {
  trip: TripSummary | null
  status: AsyncStatus
  onOpenTrip: (tripId: string) => void
  onCreateTrip: () => void
  onReloadTrips: () => void
}) {
  if (status === 'loading' || status === 'idle') {
    return <FeaturedTripSkeleton />
  }

  if (status === 'error') {
    return (
      <article className="featured-trip-card featured-trip-error">
        <p className="featured-label">이어갈 여행</p>
        <h2>여행을 불러오지 못했어요.</h2>
        <p>로그인 상태를 확인한 뒤 다시 불러와 주세요.</p>
        <button type="button" onClick={onReloadTrips}>다시 불러오기</button>
      </article>
    )
  }

  if (!trip) {
    return (
      <article className="featured-trip-card featured-trip-empty">
        <div>
          <p className="featured-label">여행 보관함</p>
          <h3>아직 여행이 없어요</h3>
          <p>가고 싶은 곳부터 가볍게 정해보세요.</p>
        </div>
        <a className="trip-primary-link" href="/trips/new" onClick={(event) => handleSpaNavigation(event, onCreateTrip)}>새 여행 만들기 <span aria-hidden="true">→</span></a>
      </article>
    )
  }

  return (
    <article className={`featured-trip-card ${trip.status.toLowerCase()} ${featuredTripUrgencyClass(trip)}`}>
      <div className="featured-trip-copy">
        <div className="featured-trip-topline">
          <p className="featured-label">{featuredTripTimingLabel(trip)}</p>
          <span className={`trip-status ${trip.status.toLowerCase()}`}>{tripStatusLabel(trip.status)}</span>
        </div>
        <p className="featured-destination"><strong>{trip.destination}</strong></p>
        <h3>{trip.title}</h3>
        <div className="featured-trip-meta">
          <span><small>날짜</small><strong>{formatDate(trip.startDate)} – {formatDate(trip.endDate)}</strong></span>
          <span><small>기간</small><strong>{durationLabel(trip.startDate, trip.endDate)}</strong></span>
          <span><small>인원</small><strong>{trip.memberCount}명</strong></span>
        </div>
        <a className="trip-primary-link" href={`/trips/${trip.id}`} onClick={(event) => handleSpaNavigation(event, () => onOpenTrip(trip.id))}>
          {tripActionLabel(trip.status)} <span aria-hidden="true">→</span>
        </a>
      </div>
      <div className="featured-travel-stack" aria-hidden="true">
        <div className="featured-ticket-sheet ticket-sheet-back"><i /><i /><i /></div>
        <div className="featured-ticket-sheet ticket-sheet-middle"><i /><i /></div>
        <div className="featured-date-panel">
          <span>{formatDateMonth(trip.startDate)}</span>
          <strong>{formatDateDay(trip.startDate)}</strong>
          <i />
          <span>{formatWeekday(trip.startDate)}</span>
        </div>
      </div>
    </article>
  )
}

function FeaturedTripSkeleton() {
  return (
    <div className="featured-trip-card featured-trip-skeleton" aria-label="최근 여행을 불러오는 중">
      <span /><strong /><p /><i />
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
    <section className="profile-card" aria-labelledby="profile-title">
      <div className="section-heading">
        <div>
          <p>계정 설정</p>
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
        <img src={resolveBackendAssetUrl(profileImageUrl)} alt="" width="58" height="58" />
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
      onImageError('프로필 이미지는 2MB 이하 파일만 사용할 수 있어요.')
      return
    }

    void onChangeProfileImage(file)
  }

  return (
    <div className="profile-image-controls">
      <label className={`profile-image-action ${disabled ? 'disabled' : ''}`}>
        이미지 변경
        <input name="profileImage" type="file" accept="image/*" disabled={disabled} onChange={handleFileChange} />
      </label>
      <button
        className="profile-image-remove"
        type="button"
        disabled={disabled || !hasProfileImage}
        onClick={() => void onChangeProfileImage(null)}
      >
        기본 이미지
      </button>
      <p>프로필과 여행 멤버 목록에 표시할 이미지예요.</p>
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
          autoComplete="nickname"
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
  totalTripCount,
  counts,
  activeFilter,
  status,
  isDeleteMode,
  selectedTripIds,
  onFilterChange,
  onCreateTrip,
  onOpenTrip,
  onReloadTrips,
  onToggleDeleteMode,
  onToggleTripSelection,
  onRequestDeleteConfirmation,
}: {
  trips: TripSummary[]
  totalTripCount: number
  counts: Record<TripFilter, number>
  activeFilter: TripFilter
  status: AsyncStatus
  isDeleteMode: boolean
  selectedTripIds: string[]
  onFilterChange: (filter: TripFilter) => void
  onCreateTrip: () => void
  onOpenTrip: (tripId: string) => void
  onReloadTrips: () => void
  onToggleDeleteMode: () => void
  onToggleTripSelection: (tripId: string) => void
  onRequestDeleteConfirmation: () => void
}) {
  const filters: Array<{ value: TripFilter; label: string }> = [
    { value: 'ALL', label: '전체' },
    { value: 'PLANNING', label: '여행 중' },
    { value: 'UPCOMING', label: '예정' },
    { value: 'COMPLETED', label: '지난 여행' },
  ]

  return (
    <section className={`trip-dashboard ${isDeleteMode ? 'is-delete-mode' : ''}`} id="my-trips" aria-labelledby="trips-title">
      <div className="section-heading">
        <h2 id="trips-title">전체 여행</h2>
        <div className="trip-dashboard-actions">
          <button className="trip-refresh-button" type="button" onClick={onReloadTrips} disabled={status === 'loading' || isDeleteMode}>
            새로고침
          </button>
          {totalTripCount > 0 && (
            <button
              className={`trip-delete-mode-button ${isDeleteMode ? 'active' : ''}`}
              type="button"
              aria-pressed={isDeleteMode}
              onClick={onToggleDeleteMode}
            >
              {isDeleteMode ? '삭제 취소' : '일정 삭제'}
            </button>
          )}
        </div>
      </div>

      {totalTripCount > 0 && (
        <div className="trip-filter-list" aria-label="여행 상태 필터">
          {filters.map((filter) => (
            <button
              type="button"
              key={filter.value}
              aria-pressed={activeFilter === filter.value}
              onClick={() => onFilterChange(filter.value)}
            >
              {filter.label} <span>{counts[filter.value]}</span>
            </button>
          ))}
        </div>
      )}

      {status === 'loading' && <TripSkeletonList />}
      {status === 'error' && (
        <div className="empty-trip-state">
          <strong>여행 목록을 불러오지 못했어요.</strong>
          <p>로그인 상태를 확인한 뒤 다시 불러와 주세요.</p>
          <button type="button" onClick={onReloadTrips}>
            다시 불러오기
          </button>
        </div>
      )}
      {status !== 'loading' && status !== 'error' && totalTripCount === 0 && (
        <EmptyTripState onCreateTrip={onCreateTrip} />
      )}
      {status !== 'loading' && status !== 'error' && totalTripCount > 0 && trips.length === 0 && (
        <div className="empty-trip-state filtered-empty-state">
          <strong>이 상태의 여행은 아직 없어요.</strong>
          <p>다른 상태를 선택하면 저장된 여행을 확인할 수 있어요.</p>
        </div>
      )}
      {status !== 'loading' && status !== 'error' && trips.length > 0 && (
        <div className="trip-card-grid">
          {trips.map((trip) => (
            <TripCard
              key={trip.id}
              trip={trip}
              isDeleteMode={isDeleteMode}
              isSelected={selectedTripIds.includes(trip.id)}
              onOpen={() => onOpenTrip(trip.id)}
              onToggleSelection={() => onToggleTripSelection(trip.id)}
            />
          ))}
        </div>
      )}

      {isDeleteMode && (
        <aside className="trip-delete-dock" aria-live="polite">
          <div>
            <strong>{selectedTripIds.length}개 선택</strong>
            <span>{selectedTripIds.length > 0 ? '선택한 여행을 확인한 뒤 삭제하세요.' : '삭제할 여행을 눌러 선택하세요.'}</span>
          </div>
          <button type="button" onClick={onToggleDeleteMode}>취소</button>
          <button
            className="danger"
            type="button"
            disabled={selectedTripIds.length === 0}
            onClick={onRequestDeleteConfirmation}
          >
            삭제 확인
          </button>
        </aside>
      )}
    </section>
  )
}

function EmptyTripState({ onCreateTrip }: { onCreateTrip: () => void }) {
  return (
    <div className="empty-trip-state">
      <strong>아직 여행이 없어요</strong>
      <p>새 여행을 만들면 이곳에 차곡차곡 모여요.</p>
      <a className="trip-primary-link" href="/trips/new" onClick={(event) => handleSpaNavigation(event, onCreateTrip)}>
        새 여행 만들기
      </a>
    </div>
  )
}

function TripCard({
  trip,
  isDeleteMode,
  isSelected,
  onOpen,
  onToggleSelection,
}: {
  trip: TripSummary
  isDeleteMode: boolean
  isSelected: boolean
  onOpen: () => void
  onToggleSelection: () => void
}) {
  return (
    <article className={`trip-card ${trip.status.toLowerCase()} ${isDeleteMode ? 'delete-mode' : ''} ${isSelected ? 'selected-for-delete' : ''}`}>
      {isDeleteMode && (
        <button
          className="trip-card-select-button"
          type="button"
          aria-label={`${trip.title} ${isSelected ? '선택 해제' : '삭제 대상으로 선택'}`}
          aria-pressed={isSelected}
          onClick={onToggleSelection}
        >
          <span aria-hidden="true">{isSelected ? '✓' : ''}</span>
        </button>
      )}
      <div className="trip-card-topline">
        <span className={`trip-status ${trip.status.toLowerCase()}`}>{tripStatusLabel(trip.status)}</span>
        <span className="trip-card-duration"><small>기간</small><strong>{durationLabel(trip.startDate, trip.endDate)}</strong></span>
      </div>
      <h3>{trip.title}</h3>
      <p className="trip-destination">{trip.destination}</p>
      <div className="trip-card-meta">
        <span><small>날짜</small><strong>{formatDate(trip.startDate)} – {formatDate(trip.endDate)}</strong></span>
        <span><small>인원</small><strong>{trip.memberCount}명</strong></span>
      </div>
      <div className="trip-card-footer">
        {!isDeleteMode && (
          <a className="trip-open-button" href={`/trips/${trip.id}`} onClick={(event) => handleSpaNavigation(event, onOpen)}>
            {tripActionLabel(trip.status)} <span aria-hidden="true">→</span>
          </a>
        )}
      </div>
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

function compareTripsForFeature(left: TripSummary, right: TripSummary) {
  const statusOrder: Record<TripStatus, number> = {
    PLANNING: 0,
    UPCOMING: 1,
    COMPLETED: 2,
  }
  const statusDifference = statusOrder[left.status] - statusOrder[right.status]

  if (statusDifference !== 0) {
    return statusDifference
  }

  if (left.status === 'UPCOMING' && right.status === 'UPCOMING') {
    return new Date(left.startDate).getTime() - new Date(right.startDate).getTime()
  }

  if (left.status === 'COMPLETED' && right.status === 'COMPLETED') {
    return new Date(right.endDate).getTime() - new Date(left.endDate).getTime()
  }

  return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
}

function featuredTripSectionCopy(trip: TripSummary | null) {
  if (!trip) return { title: '다가오는 여행', badge: null, tone: 'later' }
  if (trip.status === 'PLANNING') return { title: '지금 여행 중', badge: '여행 중', tone: 'active' }
  if (trip.status === 'COMPLETED') return { title: '최근 다녀온 여행', badge: null, tone: 'later' }
  const daysUntilDeparture = calendarDayDifference(trip.startDate)
  return {
    title: '다가오는 여행',
    badge: daysUntilDeparture === 0 ? 'D-DAY' : `D-${daysUntilDeparture}`,
    tone: departureUrgencyTone(daysUntilDeparture),
  }
}

function featuredTripUrgencyClass(trip: TripSummary) {
  if (trip.status === 'PLANNING') return 'urgency-active'
  if (trip.status !== 'UPCOMING') return 'urgency-later'
  return `urgency-${departureUrgencyTone(calendarDayDifference(trip.startDate))}`
}

function departureUrgencyTone(daysUntilDeparture: number) {
  if (daysUntilDeparture <= 3) return 'urgent'
  if (daysUntilDeparture <= 14) return 'soon'
  if (daysUntilDeparture <= 30) return 'near'
  return 'later'
}

function featuredTripTimingLabel(trip: TripSummary) {
  if (trip.status === 'PLANNING') return '여행 중'
  if (trip.status === 'COMPLETED') return `${formatDate(trip.endDate)}에 다녀왔어요`
  return `${formatDate(trip.startDate)} 출발`
}

function calendarDayDifference(value: string) {
  const today = new Date()
  const todayUtc = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())
  const [year, month, day] = value.split('-').map(Number)
  return Math.max(0, Math.round((Date.UTC(year, month - 1, day) - todayUtc) / 86_400_000))
}

function tripStatusLabel(status: TripStatus) {
  const labels: Record<TripStatus, string> = {
    PLANNING: '여행 중',
    UPCOMING: '예정',
    COMPLETED: '지난 여행',
  }
  return labels[status]
}

function tripActionLabel(status: TripStatus) {
  const labels: Record<TripStatus, string> = {
    PLANNING: '여행 보기',
    UPCOMING: '일정 보기',
    COMPLETED: '다시 보기',
  }
  return labels[status]
}

function formatDateMonth(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { month: 'short' }).format(new Date(`${value}T00:00:00`))
}

function formatDateDay(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { day: '2-digit' }).format(new Date(`${value}T00:00:00`)).replace(/\D/g, '')
}

function formatWeekday(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { weekday: 'short' }).format(new Date(`${value}T00:00:00`))
}

function handleSpaNavigation(event: MouseEvent<HTMLAnchorElement>, navigate: () => void) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return
  }
  event.preventDefault()
  navigate()
}

function statusLabel(status: AsyncStatus) {
  const labels: Record<AsyncStatus, string> = {
    idle: '확인 전',
    loading: '불러오는 중…',
    success: '확인됨',
    error: '확인 필요',
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
  const date = new Date(`${value}T00:00:00`)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(date).replaceAll(' ', '')
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
