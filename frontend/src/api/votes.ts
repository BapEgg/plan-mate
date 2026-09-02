import { bearerHeaders, request } from './client'

export type ProposalStatus = 'READY' | 'VOTE_OPEN' | 'APPLIED' | 'REJECTED' | 'CANCELLED' | 'STALE'
export type ProposalDecisionMode = 'DIRECT' | 'VOTE' | null
export type VoteStatus = 'OPEN' | 'PASSED' | 'REJECTED' | 'INSUFFICIENT_PARTICIPATION' | 'CANCELLED' | 'STALE'
export type BallotChoice = 'CHANGE' | 'KEEP_CURRENT'

export type ItineraryProposal = {
  proposalId: string
  tripId: string
  baseItineraryId: number
  baseItineraryVersion: number
  createdByUserId: number
  proposalType: 'REPLACE_ITEM'
  status: ProposalStatus
  decisionMode: ProposalDecisionMode
  dayNumber: number
  targetItemId: number
  replacementPlaceId: string
  replacementDisplayName: string
  replacementStartTime: string
  replacementDurationMinutes: number
  appliedItineraryId: number | null
  createdAt: string
  updatedAt: string
}

export type ItineraryVote = {
  voteId: string
  tripId: string
  proposal: ItineraryProposal
  status: VoteStatus
  eligibleVoterCount: number
  minimumParticipationCount: number
  participationCount: number
  changeCount: number
  keepCurrentCount: number
  eligibleByMe: boolean
  myChoice: BallotChoice | null
  deadline: string
  closedAt: string | null
  resultReason: string | null
  createdAt: string
}

export type CreateItineraryProposalPayload = {
  baseItineraryId: number
  baseItineraryVersion: number
  dayNumber: number
  targetItemId: number
  replacementPlaceId: string
  replacementStartTime: string
  replacementDurationMinutes: number
}

export function listItineraryProposals(accessToken: string, tripId: string) {
  return request<ItineraryProposal[]>(`/api/trips/${tripId}/itinerary-proposals`, {
    headers: bearerHeaders(accessToken),
  })
}

export function createItineraryProposal(
  accessToken: string,
  tripId: string,
  payload: CreateItineraryProposalPayload,
) {
  return request<ItineraryProposal>(`/api/trips/${tripId}/itinerary-proposals`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify(payload),
  })
}

export function applyItineraryProposal(accessToken: string, tripId: string, proposalId: string) {
  return request<import('./revisions').ItineraryRevision>(
    `/api/trips/${tripId}/itinerary-proposals/${proposalId}/apply`,
    { method: 'POST', headers: bearerHeaders(accessToken) },
  )
}

export function listItineraryVotes(accessToken: string, tripId: string) {
  return request<ItineraryVote[]>(`/api/trips/${tripId}/itinerary-votes`, {
    headers: bearerHeaders(accessToken),
  })
}

export function openItineraryVote(accessToken: string, tripId: string, proposalId: string) {
  return request<ItineraryVote>(`/api/trips/${tripId}/itinerary-votes/proposals/${proposalId}`, {
    method: 'POST',
    headers: bearerHeaders(accessToken),
  })
}

export function castItineraryBallot(
  accessToken: string,
  tripId: string,
  voteId: string,
  choice: BallotChoice,
) {
  return request<ItineraryVote>(`/api/trips/${tripId}/itinerary-votes/${voteId}/ballot`, {
    method: 'PUT',
    headers: bearerHeaders(accessToken),
    body: JSON.stringify({ choice }),
  })
}

export function cancelItineraryVote(accessToken: string, tripId: string, voteId: string) {
  return request<ItineraryVote>(`/api/trips/${tripId}/itinerary-votes/${voteId}`, {
    method: 'DELETE',
    headers: bearerHeaders(accessToken),
  })
}
