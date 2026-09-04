import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { ApiError } from '../../../../api/client'
import { createOwnerTransferRequest, removeTripMember } from '../../../../api/membership'
import { sendTripInvitation } from '../../../../api/invitations'
import type { OwnerTransferRequest } from '../../../../api/membership'
import type { TripMember } from '../../../../api/trips'

type MembershipManageDrawerProps = {
  accessToken: string
  tripId: string
  members: TripMember[]
  currentUserId: number
  onClose: () => void
  onMembershipChanged: () => void
}

/**
 * OWNER 전용 관리 drawer: 내보내기, 내부 초대(exact email), 방장 이전 요청. spec §5.1
 * "초대 취소·내보내기·방장 이전은 별도 관리 drawer에서 수행한다."
 */
export function MembershipManageDrawer({
  accessToken,
  tripId,
  members,
  currentUserId,
  onClose,
  onMembershipChanged,
}: MembershipManageDrawerProps) {
  const dialogRef = useRef<HTMLDivElement | null>(null)
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteStatus, setInviteStatus] = useState<'idle' | 'loading' | 'success' | 'error'>('idle')
  const [inviteMessage, setInviteMessage] = useState('')
  const [removingUserId, setRemovingUserId] = useState<number | null>(null)
  const [transferTargetUserId, setTransferTargetUserId] = useState('')
  const [transferStatus, setTransferStatus] = useState<'idle' | 'loading' | 'error'>('idle')
  const [transferMessage, setTransferMessage] = useState('')
  const [pendingTransfer, setPendingTransfer] = useState<OwnerTransferRequest | null>(null)

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.requestAnimationFrame(() => dialogRef.current?.focus())

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose()
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) {
        return
      }
      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])',
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
    }
  }, [onClose])

  const removableMembers = members.filter((member) => member.role === 'MEMBER')

  async function handleRemove(userId: number) {
    setRemovingUserId(userId)
    try {
      await removeTripMember(accessToken, tripId, userId)
      onMembershipChanged()
    } catch (error: unknown) {
      setInviteMessage(errorMessageFrom(error))
      setInviteStatus('error')
    } finally {
      setRemovingUserId(null)
    }
  }

  async function handleInvite(event: FormEvent) {
    event.preventDefault()
    const email = inviteEmail.trim()
    if (!email) return
    setInviteStatus('loading')
    setInviteMessage('')
    try {
      await sendTripInvitation(accessToken, tripId, { inviteeEmail: email })
      setInviteStatus('success')
      setInviteMessage('초대를 보냈습니다.')
      setInviteEmail('')
    } catch (error: unknown) {
      setInviteStatus('error')
      setInviteMessage(errorMessageFrom(error))
    }
  }

  async function handleTransferRequest(event: FormEvent) {
    event.preventDefault()
    const targetUserId = Number(transferTargetUserId)
    if (!Number.isFinite(targetUserId) || targetUserId <= 0) return
    setTransferStatus('loading')
    setTransferMessage('')
    try {
      const created = await createOwnerTransferRequest(accessToken, tripId, targetUserId)
      setPendingTransfer(created)
      setTransferStatus('idle')
    } catch (error: unknown) {
      setTransferStatus('error')
      setTransferMessage(errorMessageFrom(error))
    }
  }

  return createPortal(
    <div className="membership-drawer-backdrop" onClick={onClose}>
      <div
        aria-label="여행방 관리"
        aria-modal="true"
        className="membership-drawer"
        onClick={(event) => event.stopPropagation()}
        ref={dialogRef}
        role="dialog"
        tabIndex={-1}
      >
        <div className="membership-drawer-heading">
          <h2>여행방 관리</h2>
          <button aria-label="관리 창 닫기" className="membership-drawer-close" onClick={onClose} type="button">✕</button>
        </div>

        <section aria-label="참여자 내보내기">
          <h3>참여자</h3>
          {removableMembers.length === 0 ? (
            <p className="membership-drawer-empty">내보낼 수 있는 참여자가 없습니다.</p>
          ) : (
            <ul className="membership-drawer-member-list">
              {removableMembers.map((member) => (
                <li key={member.userId}>
                  <span>{member.nickname}</span>
                  <button
                    disabled={removingUserId === member.userId}
                    onClick={() => void handleRemove(member.userId)}
                    type="button"
                  >
                    {removingUserId === member.userId ? '내보내는 중…' : '내보내기'}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-label="초대 보내기">
          <h3>초대 보내기</h3>
          <form onSubmit={(event) => void handleInvite(event)}>
            <label htmlFor="membership-invite-email">가입된 계정의 이메일</label>
            <input
              autoComplete="email"
              id="membership-invite-email"
              name="inviteeEmail"
              onChange={(event) => setInviteEmail(event.target.value)}
              placeholder="friend@example.com"
              spellCheck={false}
              type="email"
              value={inviteEmail}
            />
            <button disabled={inviteStatus === 'loading'} type="submit">
              {inviteStatus === 'loading' ? '보내는 중…' : '초대 보내기'}
            </button>
          </form>
          {inviteMessage && (
            <p className={inviteStatus === 'error' ? 'membership-drawer-error' : 'membership-drawer-success'} role="status">
              {inviteMessage}
            </p>
          )}
        </section>

        <section aria-label="방장 이전">
          <h3>방장 이전</h3>
          {pendingTransfer ? (
            <p className="membership-drawer-empty">
              방장 이전 요청을 보냈습니다. 상대가 수락하면 즉시 적용됩니다.
            </p>
          ) : (
            <form onSubmit={(event) => void handleTransferRequest(event)}>
              <label htmlFor="membership-transfer-target">새 방장으로 지정할 참여자</label>
              <select
                id="membership-transfer-target"
                name="ownerTransferTarget"
                onChange={(event) => setTransferTargetUserId(event.target.value)}
                value={transferTargetUserId}
              >
                <option value="">참여자를 선택해 주세요</option>
                {removableMembers.filter((member) => member.userId !== currentUserId).map((member) => (
                  <option key={member.userId} value={member.userId}>{member.nickname}</option>
                ))}
              </select>
              <button disabled={transferStatus === 'loading' || !transferTargetUserId} type="submit">
                {transferStatus === 'loading' ? '요청하는 중…' : '방장 이전 요청'}
              </button>
            </form>
          )}
          {transferMessage && <p className="membership-drawer-error" role="status">{transferMessage}</p>}
        </section>
      </div>
    </div>,
    document.body,
  )
}

function errorMessageFrom(error: unknown) {
  return error instanceof ApiError ? error.message : '요청 처리 중 오류가 발생했습니다.'
}
