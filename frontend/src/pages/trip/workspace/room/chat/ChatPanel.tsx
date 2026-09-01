import type { AuthUser } from '../../../../../api/auth'
import type { TripMember } from '../../../../../api/trips'
import type { ItineraryPlace } from '../../workspaceTypes'

type ChatPanelProps = {
  members: TripMember[]
  currentUser: AuthUser | null
  activeDay: number
  selectedPlace: ItineraryPlace | null
  demoPreviewEnabled: boolean
}

/**
 * Chat has no save/send API yet (spec §3.2). Production default is an honest
 * disabled state — no fake messages — so it can never be mistaken for real
 * data. The fixed preview only renders under an explicit demo flag
 * (`VITE_WORKSPACE_DEMO_PREVIEW`), matching the same isolation pattern
 * already used for `VITE_ITINERARY_GENERATION_ENABLED`.
 */
export function ChatPanel({ members, currentUser, activeDay, selectedPlace, demoPreviewEnabled }: ChatPanelProps) {
  if (!demoPreviewEnabled) {
    return (
      <div className="trip-chat-disabled" role="status">
        <strong>채팅은 아직 연결되지 않았습니다.</strong>
        <p>대화를 저장하고 실시간으로 주고받는 기능은 준비 중입니다.</p>
      </div>
    )
  }

  const firstMember = members[0]?.nickname ?? '여행 멤버'
  const secondMember = members[1]?.nickname ?? '동행자'
  const currentNickname = currentUser?.nickname ?? members[2]?.nickname ?? '나'

  return (
    <>
      <div className="trip-chat-preview" aria-label="여행방 대화 예시(데모)">
        <div className="chat-date-divider"><span>{activeDay}일차 일정 이야기 · 데모 미리보기</span></div>
        <article className="chat-message-row">
          <span className="preview-avatar blue" aria-hidden="true">{firstMember.slice(0, 1)}</span>
          <div><strong>{firstMember}</strong><p>{activeDay}일차 일정 확인했어요. 이동 중간에 쉬는 시간이 있으면 좋겠어요.</p><time>오후 7:12</time></div>
        </article>
        <article className="chat-message-row">
          <span className="preview-avatar sand" aria-hidden="true">{secondMember.slice(0, 1)}</span>
          <div><strong>{secondMember}</strong><p>{selectedPlace?.title ?? '오후 일정'} 다음에 카페를 넣어보는 건 어때요?</p><time>오후 7:14</time></div>
        </article>
        <article className="chat-message-row mine">
          <div><p>좋아요. 후보를 같이 보고 정해요.</p><time>오후 7:15</time></div>
          <span className="preview-avatar navy" aria-hidden="true">{currentNickname.slice(0, 1)}</span>
        </article>
      </div>
      <div className="trip-chat-composer" aria-label="메시지 보내기">
        <textarea disabled rows={1} aria-label="메시지 입력" placeholder="메시지를 입력하세요…" />
        <button className="ready" type="button" disabled aria-label="메시지 보내기">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <line x1="12" y1="19" x2="12" y2="6" />
            <polyline points="6 12 12 6 18 12" />
          </svg>
        </button>
      </div>
    </>
  )
}
