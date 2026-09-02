import { useState } from 'react'
import { API_BASE_URL, ApiError } from '../../../../api/client'
import { leaveTrip } from '../../../../api/membership'
import type { TripMember } from '../../../../api/trips'
import type { PresenceStatus } from '../../../../api/presence'
import { resolveBackendAssetUrl } from '../workspaceFormatters'
import { MembershipManageDrawer } from './MembershipManageDrawer'

type MembershipSummaryProps = {
  accessToken: string
  tripId: string
  members: TripMember[]
  currentUserId: number | null
  onMembershipChanged: () => void
  onLeftTrip: () => void
  presenceByMember: Record<number, PresenceStatus> | null
}

/** 참여자 avatar stack + popover, 그리고 OWNER 관리 drawer / MEMBER 나가기 진입점. */
export function MembershipSummary({
  accessToken,
  tripId,
  members,
  currentUserId,
  onMembershipChanged,
  onLeftTrip,
  presenceByMember,
}: MembershipSummaryProps) {
  const [manageOpen, setManageOpen] = useState(false)
  const [leaveStatus, setLeaveStatus] = useState<'idle' | 'loading' | 'error'>('idle')
  const [leaveError, setLeaveError] = useState('')
  const visibleMembers = members.slice(0, 5)
  const hiddenMemberCount = Math.max(0, members.length - visibleMembers.length)
  const currentMember = members.find((member) => member.userId === currentUserId) ?? null
  const isOwner = currentMember?.role === 'OWNER'

  async function handleLeave() {
    if (!window.confirm('이 여행방에서 나가시겠습니까?')) {
      return
    }
    setLeaveStatus('loading')
    setLeaveError('')
    try {
      await leaveTrip(accessToken, tripId)
      onLeftTrip()
    } catch (error: unknown) {
      setLeaveStatus('error')
      setLeaveError(error instanceof ApiError ? error.message : '여행방을 나가지 못했습니다.')
    }
  }

  return (
    <>
      <details className="member-roster">
        <summary aria-label={`참여자 ${members.length}명 보기`}>
          <span className="member-avatar-stack" aria-hidden="true">
            {visibleMembers.map((member) => <MemberAvatar member={member} key={member.userId} presence={presenceByMember?.[member.userId] ?? null} />)}
            {hiddenMemberCount > 0 && <span className="member-avatar fallback extra">+{hiddenMemberCount}</span>}
          </span>
          <span className="member-total">{members.length}명</span>
        </summary>
        <div className="member-roster-popover">
          <div className="member-roster-title"><strong>함께하는 사람</strong><span>{members.length}명</span></div>
          <ul>
            {members.map((member) => (
              <li key={member.userId}>
                <MemberAvatar member={member} presence={presenceByMember?.[member.userId] ?? null} />
                <span>
                  <strong>{member.nickname}</strong>
                  <small>{member.role === 'OWNER' ? '방장' : '참여자'} · {presenceCopy(presenceByMember?.[member.userId] ?? null)}</small>
                </span>
              </li>
            ))}
          </ul>
          {isOwner ? (
            <button className="member-roster-action" onClick={() => setManageOpen(true)} type="button">
              여행방 관리
            </button>
          ) : currentMember && (
            <button
              className="member-roster-action"
              disabled={leaveStatus === 'loading'}
              onClick={() => void handleLeave()}
              type="button"
            >
              {leaveStatus === 'loading' ? '나가는 중…' : '여행방 나가기'}
            </button>
          )}
          {leaveError && <p className="membership-drawer-error" role="status">{leaveError}</p>}
        </div>
      </details>
      {manageOpen && currentUserId !== null && (
        <MembershipManageDrawer
          accessToken={accessToken}
          currentUserId={currentUserId}
          members={members}
          onClose={() => setManageOpen(false)}
          onMembershipChanged={() => {
            setManageOpen(false)
            onMembershipChanged()
          }}
          tripId={tripId}
        />
      )}
    </>
  )
}

export function MemberAvatar({ member, presence = null }: { member: TripMember; presence?: PresenceStatus | null }) {
  return (
    <span className="member-avatar-presence" role="img" aria-label={`${member.nickname}, ${presenceCopy(presence)}`} title={`${member.nickname} · ${presenceCopy(presence)}`}>
      {member.profileImageUrl
        ? <img className="member-avatar" src={resolveBackendAssetUrl(API_BASE_URL, member.profileImageUrl)} alt="" width="38" height="38" />
        : <span className="member-avatar fallback" aria-hidden="true">{member.nickname.slice(0, 1)}</span>}
      {presence === 'ONLINE' && <i className="member-online-dot" aria-hidden="true" />}
    </span>
  )
}

function presenceCopy(presence: PresenceStatus | null) {
  if (presence === 'ONLINE') return '접속 중'
  if (presence === 'OFFLINE') return '오프라인'
  return '접속 상태 확인 중'
}
