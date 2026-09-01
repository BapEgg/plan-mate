import { useState } from 'react'
import type { AuthUser } from '../../../../api/auth'
import type { TripMember } from '../../../../api/trips'
import type { CollaborationView, ItineraryPlace } from '../workspaceTypes'
import { ChatPanel } from './chat/ChatPanel'
import { VotePanel } from './vote/VotePanel'

const demoPreviewEnabled = import.meta.env.VITE_WORKSPACE_DEMO_PREVIEW === 'true'

export function RoomPanel({ className, id, panelRole, ariaLabelledBy, members, currentUser, activeDay, selectedPlace }: {
  className?: string
  id?: string
  panelRole?: string
  ariaLabelledBy?: string
  members: TripMember[]
  currentUser: AuthUser | null
  activeDay: number
  selectedPlace: ItineraryPlace | null
}) {
  const [view, setView] = useState<CollaborationView>('CHAT')

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
        <button aria-selected={view === 'CHAT'} className={view === 'CHAT' ? 'active' : ''} role="tab" type="button" onClick={() => setView('CHAT')}>대화</button>
        <button aria-selected={view === 'VOTE'} className={view === 'VOTE' ? 'active' : ''} role="tab" type="button" onClick={() => setView('VOTE')}>투표</button>
      </div>
      {view === 'CHAT' ? (
        <ChatPanel
          members={members}
          currentUser={currentUser}
          activeDay={activeDay}
          selectedPlace={selectedPlace}
          demoPreviewEnabled={demoPreviewEnabled}
        />
      ) : (
        <VotePanel
          members={members}
          activeDay={activeDay}
          selectedPlace={selectedPlace}
          demoPreviewEnabled={demoPreviewEnabled}
        />
      )}
    </aside>
  )
}
