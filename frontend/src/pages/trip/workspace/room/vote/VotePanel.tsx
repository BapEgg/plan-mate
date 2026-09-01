import type { TripMember } from '../../../../../api/trips'
import type { ItineraryPlace } from '../../workspaceTypes'

type VotePanelProps = {
  members: TripMember[]
  activeDay: number
  selectedPlace: ItineraryPlace | null
  demoPreviewEnabled: boolean
}

/**
 * Voting has no proposal/ballot API yet (spec §3.2). Production default is
 * an honest disabled state; the fixed preview only renders under the
 * explicit demo flag. See ChatPanel for the same isolation pattern.
 */
export function VotePanel({ members, activeDay, selectedPlace, demoPreviewEnabled }: VotePanelProps) {
  if (!demoPreviewEnabled) {
    return (
      <div className="trip-vote-disabled" role="status">
        <strong>투표 기능은 준비 중입니다.</strong>
        <p>장소 제안과 투표로 일정을 함께 정하는 기능은 아직 연결되지 않았습니다.</p>
      </div>
    )
  }

  const firstMember = members[0]?.nickname ?? '여행 멤버'

  return (
    <div className="trip-vote-preview" aria-label="여행방 투표 예시(데모)">
      <span className="vote-author">{firstMember} 님이 제안 · 데모 미리보기</span>
      <h3>{activeDay}일차에 카페 시간을 넣을까요?</h3>
      <p>{selectedPlace?.title ?? '선택한 장소'} 다음 일정을 조금 여유롭게 조정하는 제안입니다.</p>
      <div className="vote-preview-options" aria-hidden="true">
        <span><i style={{ width: '72%' }} /><strong>좋아요</strong><small>3</small></span>
        <span><i style={{ width: '28%' }} /><strong>그대로 갈게요</strong><small>1</small></span>
      </div>
    </div>
  )
}
