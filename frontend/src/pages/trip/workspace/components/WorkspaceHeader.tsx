import type { MouseEvent } from 'react'
import type { AuthUser } from '../../../../api/auth'
import type { TripDetail, TripPlanningProfile } from '../../../../api/trips'
import { MembershipSummary } from '../membership/MembershipSummary'
import {
  accommodationAreaLabel,
  companionLabel,
  formatDateRange,
  formatTime,
  interestLabel,
  transportLabel,
  travelPaceLabel,
  tripCountdownLabel,
} from '../workspaceFormatters'

export function WorkspaceHeader({
  trip,
  dayCount,
  placeCount,
  accessToken,
  currentUser,
  onBackToMain,
  onLogout,
  onRefresh,
  onMembershipChanged,
  onLeftTrip,
}: {
  trip: TripDetail
  dayCount: number
  placeCount: number
  accessToken: string
  currentUser: AuthUser | null
  onBackToMain: () => void
  onLogout: () => void
  onRefresh: () => void
  onMembershipChanged: () => void
  onLeftTrip: () => void
}) {
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
        <MembershipSummary
          accessToken={accessToken}
          currentUserId={currentUser?.id ?? null}
          members={trip.members}
          onLeftTrip={onLeftTrip}
          onMembershipChanged={onMembershipChanged}
          tripId={trip.id}
        />
        {trip.planningProfile && (
          <details className="trip-condition-disclosure">
            <summary>여행 조건</summary>
            <div className="trip-condition-popover"><PlanningProfileSummary profile={trip.planningProfile} /></div>
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

function handleSpaNavigation(event: MouseEvent<HTMLAnchorElement>, navigate: () => void) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return
  }
  event.preventDefault()
  navigate()
}
