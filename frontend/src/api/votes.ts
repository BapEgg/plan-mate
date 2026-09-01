/**
 * Type-only stub for WP-E (Proposal + Vote + Revision). No fetch calls yet —
 * see docs/api/collaboration-workspace-api.md §6/§7.
 */

export type VoteStatus = 'OPEN' | 'PASSED' | 'REJECTED' | 'CANCELLED'
export type BallotChoice = 'CHANGE' | 'KEEP_CURRENT'

export type Vote = {
  voteId: string
  proposalId: string
  tripId: string
  baseItineraryId: number
  status: VoteStatus
  eligibleVoterCount: number
  deadline: string
}

export type Ballot = {
  voteId: string
  voterUserId: number
  choice: BallotChoice
  castAt: string
}
