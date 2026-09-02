import { useCallback, useEffect, useMemo, useState } from 'react'
import type { AuthUser } from '../../../../../api/auth'
import { ApiError } from '../../../../../api/client'
import { autocompletePlacesInDestination } from '../../../../../api/places'
import type { PlaceAutocompleteItem } from '../../../../../api/places'
import type { TripMember } from '../../../../../api/trips'
import {
  applyItineraryProposal,
  cancelItineraryVote,
  castItineraryBallot,
  createItineraryProposal,
  listItineraryProposals,
  listItineraryVotes,
  openItineraryVote,
} from '../../../../../api/votes'
import type { BallotChoice, ItineraryProposal, ItineraryVote } from '../../../../../api/votes'
import type { ItineraryPlace } from '../../workspaceTypes'

type VotePanelProps = {
  accessToken: string
  activeDay: number
  baseItineraryId: number | null
  baseItineraryVersion: number | null
  currentUser: AuthUser | null
  destinationPlaceId: string | null
  members: TripMember[]
  refreshSignal: number
  selectedPlace: ItineraryPlace | null
  tripId: string
  onPendingCountChange: (count: number) => void
  onRevisionApplied: () => void
}

type AsyncAction = { kind: 'proposal' | 'vote' | 'ballot' | 'apply' | 'cancel'; id: string } | null

export function VotePanel({
  accessToken,
  activeDay,
  baseItineraryId,
  baseItineraryVersion,
  currentUser,
  destinationPlaceId,
  members,
  refreshSignal,
  selectedPlace,
  tripId,
  onPendingCountChange,
  onRevisionApplied,
}: VotePanelProps) {
  const [proposals, setProposals] = useState<ItineraryProposal[]>([])
  const [votes, setVotes] = useState<ItineraryVote[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [composerOpen, setComposerOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [searchResults, setSearchResults] = useState<PlaceAutocompleteItem[]>([])
  const [searching, setSearching] = useState(false)
  const [candidate, setCandidate] = useState<PlaceAutocompleteItem | null>(null)
  const [startTime, setStartTime] = useState(selectedPlace?.startTime ?? '10:00')
  const [durationMinutes, setDurationMinutes] = useState(selectedPlace?.durationMinutes ?? 60)
  const [action, setAction] = useState<AsyncAction>(null)

  const currentMembership = members.find((member) => member.userId === currentUser?.id)
  const isOwner = currentMembership?.role === 'OWNER'
  const openVotes = useMemo(() => votes.filter((vote) => vote.status === 'OPEN'), [votes])
  const recentVotes = useMemo(() => votes.filter((vote) => vote.status !== 'OPEN').slice(0, 3), [votes])
  const readyProposals = useMemo(() => proposals.filter((proposal) => proposal.status === 'READY'), [proposals])

  const load = useCallback(async () => {
    try {
      setError('')
      const [nextProposals, nextVotes] = await Promise.all([
        listItineraryProposals(accessToken, tripId),
        listItineraryVotes(accessToken, tripId),
      ])
      setProposals(nextProposals)
      setVotes(nextVotes)
      onPendingCountChange(nextVotes.filter((vote) => (
        vote.status === 'OPEN' && vote.eligibleByMe && vote.myChoice === null
      )).length)
    } catch (caught: unknown) {
      setError(messageFor(caught))
    } finally {
      setLoading(false)
    }
  }, [accessToken, onPendingCountChange, tripId])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load, refreshSignal])

  async function searchPlace() {
    if (!destinationPlaceId) return
    if (query.trim().length < 2) {
      setError('장소 이름을 2글자 이상 입력해 주세요.')
      return
    }
    setSearching(true)
    setError('')
    try {
      const response = await autocompletePlacesInDestination(accessToken, {
        query: query.trim(),
        destinationPlaceId,
        languageCode: 'ko',
      })
      setSearchResults(response.items.filter((item) => item.placeId !== selectedPlace?.placeId))
      if (response.items.length === 0) setNotice('검색 결과가 없어요. 장소 이름을 조금 다르게 입력해 보세요.')
    } catch (caught: unknown) {
      setError(messageFor(caught))
    } finally {
      setSearching(false)
    }
  }

  async function submitProposal() {
    if (!selectedPlace || !candidate || baseItineraryId === null || baseItineraryVersion === null) return
    setAction({ kind: 'proposal', id: selectedPlace.id })
    setError('')
    setNotice('')
    try {
      const created = await createItineraryProposal(accessToken, tripId, {
        baseItineraryId,
        baseItineraryVersion,
        dayNumber: activeDay,
        targetItemId: Number(selectedPlace.id),
        replacementPlaceId: candidate.placeId,
        replacementStartTime: startTime,
        replacementDurationMinutes: durationMinutes,
      })
      setProposals((current) => [created, ...current.filter((item) => item.proposalId !== created.proposalId)])
      setComposerOpen(false)
      setNotice(`${candidate.mainText}(으)로 바꾸는 안을 준비했어요.`)
    } catch (caught: unknown) {
      setError(messageFor(caught))
    } finally {
      setAction(null)
    }
  }

  async function openVote(proposal: ItineraryProposal) {
    setAction({ kind: 'vote', id: proposal.proposalId })
    setError('')
    try {
      const created = await openItineraryVote(accessToken, tripId, proposal.proposalId)
      const nextVotes = [created, ...votes.filter((vote) => vote.voteId !== created.voteId)]
      setVotes(nextVotes)
      setProposals((current) => current.map((item) => item.proposalId === proposal.proposalId
        ? created.proposal
        : item))
      setNotice('여행 멤버에게 투표를 열었어요.')
      onPendingCountChange(nextVotes.filter((vote) => (
        vote.status === 'OPEN' && vote.eligibleByMe && vote.myChoice === null
      )).length)
    } catch (caught: unknown) {
      setError(messageFor(caught))
    } finally {
      setAction(null)
    }
  }

  async function applyProposal(proposal: ItineraryProposal) {
    setAction({ kind: 'apply', id: proposal.proposalId })
    setError('')
    try {
      await applyItineraryProposal(accessToken, tripId, proposal.proposalId)
      setNotice('새 일정으로 반영했습니다.')
      await load()
      onRevisionApplied()
    } catch (caught: unknown) {
      setError(messageFor(caught))
    } finally {
      setAction(null)
    }
  }

  async function cast(vote: ItineraryVote, choice: BallotChoice) {
    setAction({ kind: 'ballot', id: vote.voteId })
    setError('')
    try {
      const updated = await castItineraryBallot(accessToken, tripId, vote.voteId, choice)
      setVotes((current) => current.map((item) => item.voteId === updated.voteId ? updated : item))
      onPendingCountChange(votes.filter((item) => item.voteId !== updated.voteId
        && item.status === 'OPEN' && item.eligibleByMe && item.myChoice === null).length)
      if (updated.status === 'PASSED') {
        setNotice('투표 결과가 일정에 반영됐어요.')
        onRevisionApplied()
      } else {
        setNotice('선택을 저장했어요. 마감 전까지 바꿀 수 있습니다.')
      }
    } catch (caught: unknown) {
      setError(messageFor(caught))
    } finally {
      setAction(null)
    }
  }

  async function cancel(vote: ItineraryVote) {
    setAction({ kind: 'cancel', id: vote.voteId })
    setError('')
    try {
      const updated = await cancelItineraryVote(accessToken, tripId, vote.voteId)
      setVotes((current) => current.map((item) => item.voteId === updated.voteId ? updated : item))
      setNotice('투표를 취소했습니다.')
      onPendingCountChange(openVotes.filter((item) => item.voteId !== vote.voteId
        && item.eligibleByMe && item.myChoice === null).length)
    } catch (caught: unknown) {
      setError(messageFor(caught))
    } finally {
      setAction(null)
    }
  }

  return (
    <div className="trip-vote-panel">
      <div className="vote-panel-intro">
        <div>
          <span>{activeDay}일차 일정</span>
          <strong>{selectedPlace ? `${selectedPlace.title} 선택됨` : '일정에서 장소를 골라주세요'}</strong>
        </div>
        <button
          className="vote-proposal-trigger"
          disabled={!selectedPlace || !destinationPlaceId || baseItineraryId === null}
          type="button"
          onClick={() => setComposerOpen((open) => !open)}
        >
          {composerOpen ? '닫기' : '장소 변경 제안'}
        </button>
      </div>

      {composerOpen && selectedPlace && (
        <section className="vote-proposal-composer" aria-labelledby="vote-proposal-title">
          <div className="vote-change-preview">
            <span>{selectedPlace.startTime}</span>
            <strong id="vote-proposal-title">{selectedPlace.title}</strong>
            <i aria-hidden="true">→</i>
            <strong>{candidate?.mainText ?? '새 장소 찾기'}</strong>
          </div>
          <form onSubmit={(event) => { event.preventDefault(); void searchPlace() }}>
            <label htmlFor="vote-place-query">어디로 바꿀까요?</label>
            <div className="vote-place-search-row">
              <input
                autoComplete="off"
                id="vote-place-query"
                name="replacementPlaceQuery"
                placeholder="예: 카페 시방리, 매미성…"
                spellCheck={false}
                value={query}
                onChange={(event) => setQuery(event.target.value)}
              />
              <button disabled={searching} type="submit">
                {searching ? '찾는 중…' : '검색'}
              </button>
            </div>
          </form>
          {searchResults.length > 0 && (
            <ul className="vote-place-results" aria-label="장소 검색 결과">
              {searchResults.map((result) => (
                <li key={result.placeId}>
                  <button
                    aria-pressed={candidate?.placeId === result.placeId}
                    type="button"
                    onClick={() => setCandidate(result)}
                  >
                    <strong>{result.mainText}</strong>
                    <span>{result.secondaryText}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div className="vote-time-fields">
            <label>시작 시간<input name="replacementStartTime" type="time" value={startTime} onChange={(event) => setStartTime(event.target.value)} /></label>
            <label>머무는 시간<select name="replacementDurationMinutes" value={durationMinutes} onChange={(event) => setDurationMinutes(Number(event.target.value))}>
              {[30, 45, 60, 75, 90, 120, 150, 180].map((minutes) => <option key={minutes} value={minutes}>{durationLabel(minutes)}</option>)}
            </select></label>
          </div>
          <button
            className="vote-primary-action"
            disabled={!candidate || action?.kind === 'proposal'}
            type="button"
            onClick={() => void submitProposal()}
          >
            {action?.kind === 'proposal' ? '동선을 확인하는 중…' : '이 변경안 준비하기'}
          </button>
          <small>앞뒤 장소까지 자동차로 이어지는지 확인한 뒤 제안됩니다.</small>
        </section>
      )}

      {error && <p className="vote-feedback error" role="alert">{error}</p>}
      {notice && <p className="vote-feedback" role="status">{notice}</p>}

      <div className="vote-panel-content">
        {loading ? (
          <div className="vote-panel-state" role="status">변경안을 불러오고 있어요…</div>
        ) : (
          <>
            {openVotes.map((vote) => (
              <VoteCard
                action={action}
                currentUser={currentUser}
                isOwner={isOwner}
                key={vote.voteId}
                members={members}
                vote={vote}
                onCancel={cancel}
                onCast={cast}
              />
            ))}
            {readyProposals.map((proposal) => (
              <ProposalCard
                action={action}
                isOwner={isOwner}
                key={proposal.proposalId}
                members={members}
                proposal={proposal}
                onApply={applyProposal}
                onOpenVote={openVote}
              />
            ))}
            {openVotes.length === 0 && readyProposals.length === 0 && (
              <div className="vote-panel-state empty">
                <span aria-hidden="true">↗</span>
                <strong>함께 정할 변경이 없어요.</strong>
                <p>왼쪽 일정에서 장소를 고른 뒤, 여기서 다른 장소를 제안할 수 있어요.</p>
              </div>
            )}
            {recentVotes.length > 0 && (
              <section className="vote-recent" aria-labelledby="recent-votes-title">
                <h3 id="recent-votes-title">지난 결정</h3>
                {recentVotes.map((vote) => (
                  <div key={vote.voteId}>
                    <span>{vote.proposal.replacementDisplayName}</span>
                    <strong>{voteResultLabel(vote.status)}</strong>
                  </div>
                ))}
              </section>
            )}
          </>
        )}
      </div>
    </div>
  )
}

function ProposalCard({ action, isOwner, members, proposal, onApply, onOpenVote }: {
  action: AsyncAction
  isOwner: boolean
  members: TripMember[]
  proposal: ItineraryProposal
  onApply: (proposal: ItineraryProposal) => Promise<void>
  onOpenVote: (proposal: ItineraryProposal) => Promise<void>
}) {
  return (
    <article className="vote-proposal-card">
      <span>{nicknameFor(members, proposal.createdByUserId)} 님의 변경안</span>
      <h3>{proposal.dayNumber}일차 · {formatClock(proposal.replacementStartTime)}</h3>
      <strong>{proposal.replacementDisplayName}</strong>
      <p>{durationLabel(proposal.replacementDurationMinutes)} 머무는 일정</p>
      <div className="vote-card-actions">
        {isOwner && <button disabled={action !== null} type="button" onClick={() => void onApply(proposal)}>일정에 반영</button>}
        <button className={isOwner ? 'secondary' : ''} disabled={action !== null} type="button" onClick={() => void onOpenVote(proposal)}>함께 정하기</button>
      </div>
    </article>
  )
}

function VoteCard({ action, currentUser, isOwner, members, vote, onCancel, onCast }: {
  action: AsyncAction
  currentUser: AuthUser | null
  isOwner: boolean
  members: TripMember[]
  vote: ItineraryVote
  onCancel: (vote: ItineraryVote) => Promise<void>
  onCast: (vote: ItineraryVote, choice: BallotChoice) => Promise<void>
}) {
  const canCancel = isOwner || vote.proposal.createdByUserId === currentUser?.id
  return (
    <article className="vote-live-card">
      <header>
        <div><span>투표 중</span><small>{nicknameFor(members, vote.proposal.createdByUserId)} 님 제안</small></div>
        {canCancel && <button disabled={action !== null} type="button" onClick={() => void onCancel(vote)}>취소</button>}
      </header>
      <h3>{vote.proposal.dayNumber}일차를 이렇게 바꿀까요?</h3>
      <div className="vote-destination-slip">
        <span>{formatClock(vote.proposal.replacementStartTime)}</span>
        <strong>{vote.proposal.replacementDisplayName}</strong>
        <small>{durationLabel(vote.proposal.replacementDurationMinutes)}</small>
      </div>
      <div className="vote-live-options">
        <button
          aria-pressed={vote.myChoice === 'CHANGE'}
          disabled={!vote.eligibleByMe || action !== null}
          type="button"
          onClick={() => void onCast(vote, 'CHANGE')}
        ><span>제안대로 바꾸기</span><strong>{vote.changeCount}</strong></button>
        <button
          aria-pressed={vote.myChoice === 'KEEP_CURRENT'}
          disabled={!vote.eligibleByMe || action !== null}
          type="button"
          onClick={() => void onCast(vote, 'KEEP_CURRENT')}
        ><span>현재 일정 유지</span><strong>{vote.keepCurrentCount}</strong></button>
      </div>
      <footer>
        <span>{vote.participationCount}/{vote.eligibleVoterCount}명 참여 · 최소 {vote.minimumParticipationCount}명</span>
        <time dateTime={vote.deadline}>{deadlineLabel(vote.deadline)}</time>
      </footer>
    </article>
  )
}

function nicknameFor(members: TripMember[], userId: number) {
  return members.find((member) => member.userId === userId)?.nickname ?? '여행 멤버'
}

function formatClock(value: string) {
  return value.slice(0, 5)
}

function durationLabel(minutes: number) {
  if (minutes < 60) return `${minutes}분`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours}시간` : `${hours}시간 ${rest}분`
}

function deadlineLabel(value: string) {
  const date = new Date(value)
  return `${new Intl.DateTimeFormat('ko-KR', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)}까지`
}

function voteResultLabel(status: ItineraryVote['status']) {
  if (status === 'PASSED') return '일정에 반영'
  if (status === 'CANCELLED') return '취소됨'
  if (status === 'STALE') return '새 일정으로 종료'
  return '현재 일정 유지'
}

function messageFor(error: unknown) {
  if (!(error instanceof ApiError)) return '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.'
  if (error.code === 'PROPOSAL_ROUTE_NOT_FOUND') return '앞뒤 장소와 자동차 경로가 이어지지 않아요. 다른 장소를 골라주세요.'
  if (error.code === 'PROPOSAL_PLACE_UNRESOLVED') return '장소 위치를 확인하지 못했어요. 검색 결과에서 다시 골라주세요.'
  if (error.code === 'STALE_BASE_VERSION') return '그사이 일정이 바뀌었어요. 최신 일정을 확인한 뒤 다시 제안해 주세요.'
  if (error.code === 'ITINERARY_WINDOW_CLOSED') return '이미 지난 일정은 바꿀 수 없어요.'
  if (error.code === 'VOTE_ALREADY_CLOSED') return '이미 끝난 투표예요. 최신 결과를 다시 불러옵니다.'
  return error.message || '요청을 처리하지 못했어요.'
}
