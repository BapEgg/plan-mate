import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { ApiError } from '../../api/client'
import {
  acceptTripInvitation,
  declineTripInvitation,
  listMyTripInvitations,
} from '../../api/invitations'
import type { TripInvitation } from '../../api/invitations'
import {
  acceptFriendRequest,
  declineFriendRequest,
  listFriendRequests,
} from '../../api/friends'
import type { FriendRequest } from '../../api/friends'
import {
  acceptOwnerTransferRequest,
  declineOwnerTransferRequest,
  listMyOwnerTransferRequests,
} from '../../api/membership'
import type { OwnerTransferRequest } from '../../api/membership'
import './InboxDialog.css'

type InboxTab = 'TRIP' | 'FRIEND' | 'OWNER'

type InboxDialogProps = {
  accessToken: string
  triggerRef: React.RefObject<HTMLButtonElement | null>
  onClose: () => void
  onTripInvitationAccepted: (tripId: string) => void
}

/** spec §5.1: 초대함은 메인페이지 봉투 button과 `여행 초대 | 친구 요청 | 방장 요청` tab으로 제공한다. */
export function InboxDialog({ accessToken, triggerRef, onClose, onTripInvitationAccepted }: InboxDialogProps) {
  const dialogRef = useRef<HTMLDivElement | null>(null)
  const [activeTab, setActiveTab] = useState<InboxTab>('TRIP')
  const [tripInvitations, setTripInvitations] = useState<TripInvitation[]>([])
  const [friendRequests, setFriendRequests] = useState<FriendRequest[]>([])
  const [ownerTransferRequests, setOwnerTransferRequests] = useState<OwnerTransferRequest[]>([])
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [errorMessage, setErrorMessage] = useState('')
  const [busyId, setBusyId] = useState<number | null>(null)

  useEffect(() => {
    let ignore = false
    Promise.all([
      listMyTripInvitations(accessToken),
      listFriendRequests(accessToken, 'incoming'),
      listMyOwnerTransferRequests(accessToken),
    ])
      .then(([invitations, friends, transfers]) => {
        if (ignore) return
        setTripInvitations(invitations)
        setFriendRequests(friends)
        setOwnerTransferRequests(transfers)
        setStatus('success')
      })
      .catch((error: unknown) => {
        if (ignore) return
        setStatus('error')
        setErrorMessage(error instanceof ApiError ? error.message : '초대함을 불러오지 못했습니다.')
      })
    return () => {
      ignore = true
    }
  }, [accessToken])

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    const trigger = triggerRef.current
    document.body.style.overflow = 'hidden'
    window.requestAnimationFrame(() => dialogRef.current?.focus())

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose()
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) return
      const focusable = Array.from(
        dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), [tabindex]:not([tabindex="-1"])'),
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
      window.requestAnimationFrame(() => trigger?.focus())
    }
  }, [onClose, triggerRef])

  async function handleAcceptInvitation(invitation: TripInvitation) {
    setBusyId(invitation.id)
    try {
      await acceptTripInvitation(accessToken, invitation.id)
      setTripInvitations((current) => current.filter((item) => item.id !== invitation.id))
      onTripInvitationAccepted(invitation.tripId)
    } catch (error: unknown) {
      setErrorMessage(error instanceof ApiError ? error.message : '초대를 수락하지 못했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  async function handleDeclineInvitation(invitation: TripInvitation) {
    setBusyId(invitation.id)
    try {
      await declineTripInvitation(accessToken, invitation.id)
      setTripInvitations((current) => current.filter((item) => item.id !== invitation.id))
    } catch (error: unknown) {
      setErrorMessage(error instanceof ApiError ? error.message : '초대를 거절하지 못했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  async function handleFriendRequestAction(request: FriendRequest, accept: boolean) {
    setBusyId(request.id)
    try {
      if (accept) {
        await acceptFriendRequest(accessToken, request.id)
      } else {
        await declineFriendRequest(accessToken, request.id)
      }
      setFriendRequests((current) => current.filter((item) => item.id !== request.id))
    } catch (error: unknown) {
      setErrorMessage(error instanceof ApiError ? error.message : '친구 요청을 처리하지 못했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  async function handleOwnerTransferAction(request: OwnerTransferRequest, accept: boolean) {
    setBusyId(request.id)
    try {
      if (accept) {
        await acceptOwnerTransferRequest(accessToken, request.id)
      } else {
        await declineOwnerTransferRequest(accessToken, request.id)
      }
      setOwnerTransferRequests((current) => current.filter((item) => item.id !== request.id))
    } catch (error: unknown) {
      setErrorMessage(error instanceof ApiError ? error.message : '방장 이전 요청을 처리하지 못했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  const tabs: Array<[InboxTab, string, number]> = [
    ['TRIP', '여행 초대', tripInvitations.length],
    ['FRIEND', '친구 요청', friendRequests.length],
    ['OWNER', '방장 요청', ownerTransferRequests.length],
  ]

  return createPortal(
    <div className="inbox-dialog-backdrop" onClick={onClose}>
      <div
        aria-label="초대함"
        aria-modal="true"
        className="inbox-dialog"
        onClick={(event) => event.stopPropagation()}
        ref={dialogRef}
        role="dialog"
        tabIndex={-1}
      >
        <div className="inbox-dialog-heading">
          <h2>초대함</h2>
          <button aria-label="초대함 닫기" className="inbox-dialog-close" onClick={onClose} type="button">✕</button>
        </div>

        <div className="inbox-dialog-tabs" role="tablist" aria-label="초대함 종류">
          {tabs.map(([tab, label, count]) => (
            <button
              aria-selected={activeTab === tab}
              className={activeTab === tab ? 'active' : ''}
              key={tab}
              role="tab"
              type="button"
              onClick={() => setActiveTab(tab)}
            >
              {label}{count > 0 ? ` (${count})` : ''}
            </button>
          ))}
        </div>

        {status === 'loading' && <p className="inbox-dialog-empty">불러오는 중…</p>}
        {status === 'error' && <p className="inbox-dialog-error" role="alert">{errorMessage}</p>}

        {status === 'success' && activeTab === 'TRIP' && (
          <ul className="inbox-dialog-list" role="tabpanel">
            {tripInvitations.length === 0 && <li className="inbox-dialog-empty">받은 여행 초대가 없습니다.</li>}
            {tripInvitations.map((invitation) => (
              <li key={invitation.id}>
                <span>여행 #{invitation.tripId} 초대</span>
                <div className="inbox-dialog-actions">
                  <button disabled={busyId === invitation.id} onClick={() => void handleAcceptInvitation(invitation)} type="button">수락</button>
                  <button className="secondary" disabled={busyId === invitation.id} onClick={() => void handleDeclineInvitation(invitation)} type="button">거절</button>
                </div>
              </li>
            ))}
          </ul>
        )}

        {status === 'success' && activeTab === 'FRIEND' && (
          <ul className="inbox-dialog-list" role="tabpanel">
            {friendRequests.length === 0 && <li className="inbox-dialog-empty">받은 친구 요청이 없습니다.</li>}
            {friendRequests.map((request) => (
              <li key={request.id}>
                <span>사용자 #{request.requesterUserId}님의 친구 요청</span>
                <div className="inbox-dialog-actions">
                  <button disabled={busyId === request.id} onClick={() => void handleFriendRequestAction(request, true)} type="button">수락</button>
                  <button className="secondary" disabled={busyId === request.id} onClick={() => void handleFriendRequestAction(request, false)} type="button">거절</button>
                </div>
              </li>
            ))}
          </ul>
        )}

        {status === 'success' && activeTab === 'OWNER' && (
          <ul className="inbox-dialog-list" role="tabpanel">
            {ownerTransferRequests.length === 0 && <li className="inbox-dialog-empty">받은 방장 이전 요청이 없습니다.</li>}
            {ownerTransferRequests.map((request) => (
              <li key={request.id}>
                <span>여행 #{request.tripId} 방장 이전 요청</span>
                <div className="inbox-dialog-actions">
                  <button disabled={busyId === request.id} onClick={() => void handleOwnerTransferAction(request, true)} type="button">수락</button>
                  <button className="secondary" disabled={busyId === request.id} onClick={() => void handleOwnerTransferAction(request, false)} type="button">거절</button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>,
    document.body,
  )
}
