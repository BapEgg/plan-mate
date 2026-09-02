import { useEffect, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import type { AuthUser } from '../../../../api/auth'
import type { ChatMessageChangedPayload, ChatMessageSentPayload, ChatTypingChangedPayload } from '../../../../api/realtime'
import type { TripMember } from '../../../../api/trips'
import { listItineraryVotes } from '../../../../api/votes'
import type { CollaborationView, ItineraryPlace } from '../workspaceTypes'
import { ChatPanel } from './chat/ChatPanel'
import { VotePanel } from './vote/VotePanel'

export function RoomPanel({ accessToken, chatConnected, chatReconnectedAt, chatUnreadCount, className, id, panelRole, ariaLabelledBy, latestChatMessage, latestChatChange, latestChatTyping, members, currentUser, activeDay, baseItineraryId, baseItineraryVersion, destinationPlaceId, selectedPlace, tripId, voteRefreshSignal, onChatRead, onRevisionApplied, sendChatTyping }: {
  accessToken: string
  chatConnected: boolean
  chatReconnectedAt: number
  chatUnreadCount: number
  className?: string
  id?: string
  panelRole?: string
  ariaLabelledBy?: string
  latestChatMessage: ChatMessageSentPayload | null
  latestChatChange: ChatMessageChangedPayload | null
  latestChatTyping: ChatTypingChangedPayload | null
  members: TripMember[]
  currentUser: AuthUser | null
  activeDay: number
  baseItineraryId: number | null
  baseItineraryVersion: number | null
  destinationPlaceId: string | null
  selectedPlace: ItineraryPlace | null
  tripId: string
  voteRefreshSignal: number
  onChatRead: () => void
  onRevisionApplied: () => void
  sendChatTyping: (state: 'STARTED' | 'HEARTBEAT' | 'STOPPED', clientSessionId: string, clientEventId: string) => boolean
}) {
  const [view, setView] = useState<CollaborationView>('CHAT')
  const [pendingVoteCount, setPendingVoteCount] = useState(0)
  const tabRefs = useRef<Partial<Record<CollaborationView, HTMLButtonElement | null>>>({})

  function selectView(nextView: CollaborationView) {
    setView(nextView)
    tabRefs.current[nextView]?.focus()
  }

  function handleTabKeyDown(event: KeyboardEvent<HTMLButtonElement>, currentView: CollaborationView) {
    if (!['ArrowRight', 'ArrowLeft', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const nextView = event.key === 'ArrowRight' || event.key === 'End'
      ? 'VOTE'
      : event.key === 'ArrowLeft' || event.key === 'Home'
        ? 'CHAT'
        : currentView
    selectView(nextView)
  }

  useEffect(() => {
    let active = true
    listItineraryVotes(accessToken, tripId)
      .then((votes) => {
        if (!active) return
        setPendingVoteCount(votes.filter((vote) => (
          vote.status === 'OPEN' && vote.eligibleByMe && vote.myChoice === null
        )).length)
      })
      .catch(() => {
        // The vote panel owns its error UI; a badge failure must not break chat.
      })
    return () => { active = false }
  }, [accessToken, tripId, voteRefreshSignal])

  return (
    <aside
      aria-label={ariaLabelledBy ? undefined : '여행방 협업'}
      aria-labelledby={ariaLabelledBy}
      className={`trip-chat-panel ${className ?? ''}`}
      id={id}
      role={panelRole}
    >
      <div className="trip-chat-heading">
        <div><span className="section-kicker">함께 정하는 여행</span><h2>여행방</h2></div>
        <span className="room-connection-state"><i aria-hidden="true" />{members.length}명 참여</span>
      </div>
      <div className="collaboration-tabs" role="tablist" aria-label="여행방 기능">
        <button
          aria-controls="collaboration-panel-chat"
          aria-selected={view === 'CHAT'}
          className={view === 'CHAT' ? 'active' : ''}
          id="collaboration-tab-chat"
          ref={(element) => { tabRefs.current.CHAT = element }}
          role="tab"
          tabIndex={view === 'CHAT' ? 0 : -1}
          type="button"
          onClick={() => setView('CHAT')}
          onKeyDown={(event) => handleTabKeyDown(event, 'CHAT')}
        >
          대화{chatUnreadCount > 0 && <span className="chat-unread-badge">{chatUnreadCount > 9 ? '9+' : chatUnreadCount}</span>}
        </button>
        <button
          aria-controls="collaboration-panel-vote"
          aria-selected={view === 'VOTE'}
          className={view === 'VOTE' ? 'active' : ''}
          id="collaboration-tab-vote"
          ref={(element) => { tabRefs.current.VOTE = element }}
          role="tab"
          tabIndex={view === 'VOTE' ? 0 : -1}
          type="button"
          onClick={() => setView('VOTE')}
          onKeyDown={(event) => handleTabKeyDown(event, 'VOTE')}
        >
          투표{pendingVoteCount > 0 && <span className="chat-unread-badge">{pendingVoteCount > 9 ? '9+' : pendingVoteCount}</span>}
        </button>
      </div>
      {view === 'CHAT' ? (
        <div className="collaboration-tab-panel" id="collaboration-panel-chat" role="tabpanel" aria-labelledby="collaboration-tab-chat">
          <ChatPanel
            accessToken={accessToken}
            chatConnected={chatConnected}
            chatReconnectedAt={chatReconnectedAt}
            currentUser={currentUser}
            latestChatChange={latestChatChange}
            latestChatMessage={latestChatMessage}
            latestChatTyping={latestChatTyping}
            members={members}
            onChatRead={onChatRead}
            tripId={tripId}
            sendChatTyping={sendChatTyping}
          />
        </div>
      ) : (
        <div className="collaboration-tab-panel" id="collaboration-panel-vote" role="tabpanel" aria-labelledby="collaboration-tab-vote">
          <VotePanel
            accessToken={accessToken}
            key={selectedPlace?.id ?? 'no-selected-place'}
            members={members}
            activeDay={activeDay}
            baseItineraryId={baseItineraryId}
            baseItineraryVersion={baseItineraryVersion}
            currentUser={currentUser}
            destinationPlaceId={destinationPlaceId}
            refreshSignal={voteRefreshSignal}
            selectedPlace={selectedPlace}
            tripId={tripId}
            onPendingCountChange={setPendingVoteCount}
            onRevisionApplied={onRevisionApplied}
          />
        </div>
      )}
    </aside>
  )
}
