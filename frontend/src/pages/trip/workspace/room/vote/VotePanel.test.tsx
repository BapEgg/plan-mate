import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ItineraryVote } from '../../../../../api/votes'
import { castItineraryBallot, listItineraryProposals, listItineraryVotes } from '../../../../../api/votes'
import { VotePanel } from './VotePanel'

vi.mock('../../../../../api/places', () => ({
  autocompletePlacesInDestination: vi.fn(),
}))

vi.mock('../../../../../api/votes', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../../../../api/votes')>()
  return {
    ...original,
    applyItineraryProposal: vi.fn(),
    cancelItineraryVote: vi.fn(),
    castItineraryBallot: vi.fn(),
    createItineraryProposal: vi.fn(),
    listItineraryProposals: vi.fn(),
    listItineraryVotes: vi.fn(),
    openItineraryVote: vi.fn(),
  }
})

const members = [
  { userId: 1, nickname: '민준', profileImageUrl: null, role: 'OWNER' as const },
  { userId: 2, nickname: '서윤', profileImageUrl: null, role: 'MEMBER' as const },
]

const selectedPlace = {
  id: '101',
  day: 1,
  order: 1,
  title: '매미성',
  startTime: '10:00',
  duration: '1시간 30분',
  durationMinutes: 90,
  latitude: 34.9,
  longitude: 128.7,
  locationLabel: '거제시',
  googleMapsUri: null,
  placeId: 'old-place',
  resolved: true,
  displaySource: 'PROVIDER' as const,
  source: 'AI_DRAFT' as const,
}

const defaultProps = {
  accessToken: 'token',
  activeDay: 1,
  baseItineraryId: 505,
  baseItineraryVersion: 1,
  currentUser: { id: 1, loginId: 'local1', nickname: '민준', role: 'USER' },
  destinationPlaceId: 'geoje',
  members,
  refreshSignal: 0,
  selectedPlace,
  tripId: '1530',
  onPendingCountChange: vi.fn(),
  onRevisionApplied: vi.fn(),
}

describe('VotePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listItineraryProposals).mockResolvedValue([])
    vi.mocked(listItineraryVotes).mockResolvedValue([])
  })

  it('explains the empty state and opens a selected-place proposal form', async () => {
    const user = userEvent.setup()
    render(<VotePanel {...defaultProps} />)

    expect(await screen.findByText('함께 정할 변경이 없어요.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '장소 변경 제안' }))

    expect(screen.getByRole('textbox', { name: '어디로 바꿀까요?' })).toHaveAttribute('autocomplete', 'off')
    expect(screen.getByDisplayValue('10:00')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이 변경안 준비하기' })).toBeDisabled()
  })

  it('shows an open vote and saves a member ballot', async () => {
    const user = userEvent.setup()
    const vote = openVote()
    vi.mocked(listItineraryVotes).mockResolvedValue([vote])
    vi.mocked(castItineraryBallot).mockResolvedValue({ ...vote, myChoice: 'CHANGE', participationCount: 1, changeCount: 1 })
    render(<VotePanel {...defaultProps} />)

    const changeButton = await screen.findByRole('button', { name: /제안대로 바꾸기/ })
    await user.click(changeButton)

    await waitFor(() => expect(castItineraryBallot).toHaveBeenCalledWith('token', '1530', 'vote-1', 'CHANGE'))
    expect(await screen.findByText('선택을 저장했어요. 마감 전까지 바꿀 수 있습니다.')).toBeInTheDocument()
  })
})

function openVote(): ItineraryVote {
  return {
    voteId: 'vote-1',
    tripId: '1530',
    proposal: {
      proposalId: 'proposal-1',
      tripId: '1530',
      baseItineraryId: 505,
      baseItineraryVersion: 1,
      createdByUserId: 2,
      proposalType: 'REPLACE_ITEM',
      status: 'VOTE_OPEN',
      decisionMode: 'VOTE',
      dayNumber: 1,
      targetItemId: 101,
      replacementPlaceId: 'new-place',
      replacementDisplayName: '바람의 언덕',
      replacementStartTime: '10:30:00',
      replacementDurationMinutes: 90,
      appliedItineraryId: null,
      createdAt: '2026-09-02T00:00:00Z',
      updatedAt: '2026-09-02T00:00:00Z',
    },
    status: 'OPEN',
    eligibleVoterCount: 2,
    minimumParticipationCount: 2,
    participationCount: 0,
    changeCount: 0,
    keepCurrentCount: 0,
    eligibleByMe: true,
    myChoice: null,
    deadline: '2026-09-03T00:00:00Z',
    closedAt: null,
    resultReason: null,
    createdAt: '2026-09-02T00:00:00Z',
  }
}
