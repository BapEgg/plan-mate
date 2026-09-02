package com.planmate.vote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.membership.api.event.MembershipChangeType;
import com.planmate.membership.api.event.TripMembershipChangedEvent;
import com.planmate.proposal.entity.ItineraryProposalEntity;
import com.planmate.proposal.entity.ItineraryProposalStatus;
import com.planmate.proposal.repository.ItineraryProposalRepository;
import com.planmate.revision.service.ItineraryRevisionService;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripActiveMemberReader;
import com.planmate.trip.api.TripRoleChecker;
import com.planmate.vote.entity.BallotChoice;
import com.planmate.vote.entity.ItineraryVoteBallotEntity;
import com.planmate.vote.entity.ItineraryVoteEntity;
import com.planmate.vote.entity.ItineraryVoteVoterEntity;
import com.planmate.vote.entity.VoteStatus;
import com.planmate.vote.exception.VoteErrorCode;
import com.planmate.vote.exception.VoteException;
import com.planmate.vote.repository.ItineraryVoteBallotRepository;
import com.planmate.vote.repository.ItineraryVoteRepository;
import com.planmate.vote.repository.ItineraryVoteVoterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ItineraryVoteServiceTest {

    private static final Long TRIP_ID = 1L;
    private static final Long VOTE_ID = 10L;
    private static final Long PROPOSAL_ID = 20L;
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private TripAccessChecker tripAccessChecker;
    private TripRoleChecker tripRoleChecker;
    private TripActiveMemberReader activeMemberReader;
    private ItineraryRepository itineraryRepository;
    private ItineraryProposalRepository proposalRepository;
    private ItineraryVoteRepository voteRepository;
    private ItineraryVoteVoterRepository voterRepository;
    private ItineraryVoteBallotRepository ballotRepository;
    private ItineraryRevisionService revisionService;
    private ApplicationEventPublisher eventPublisher;
    private ItineraryVoteService service;

    @BeforeEach
    void setUp() {
        tripAccessChecker = mock(TripAccessChecker.class);
        tripRoleChecker = mock(TripRoleChecker.class);
        activeMemberReader = mock(TripActiveMemberReader.class);
        itineraryRepository = mock(ItineraryRepository.class);
        proposalRepository = mock(ItineraryProposalRepository.class);
        voteRepository = mock(ItineraryVoteRepository.class);
        voterRepository = mock(ItineraryVoteVoterRepository.class);
        ballotRepository = mock(ItineraryVoteBallotRepository.class);
        revisionService = mock(ItineraryRevisionService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        service = new ItineraryVoteService(
                tripAccessChecker,
                tripRoleChecker,
                activeMemberReader,
                itineraryRepository,
                proposalRepository,
                voteRepository,
                voterRepository,
                ballotRepository,
                revisionService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                eventPublisher
        );
    }

    @Test
    void lastBallotPassesAndAppliesTheRevisionOnlyOnce() {
        ItineraryVoteEntity vote = vote(NOW.plusSeconds(3600));
        ItineraryProposalEntity proposal = openProposal();
        ItineraryVoteVoterEntity voter = voter(1L);
        ItineraryVoteBallotEntity ballot = ballot(1L, BallotChoice.KEEP_CURRENT);
        arrangeVote(vote, proposal, List.of(voter), List.of(ballot));
        when(voterRepository.findByVoteIdAndUserIdAndValidTrue(VOTE_ID, 1L)).thenReturn(Optional.of(voter));
        when(ballotRepository.findByVoteIdAndUserId(VOTE_ID, 1L)).thenReturn(Optional.of(ballot));
        when(revisionService.applyFromVote(TRIP_ID, PROPOSAL_ID)).thenReturn(mock(ItineraryEntity.class));

        var response = service.cast(1L, TRIP_ID, VOTE_ID, BallotChoice.CHANGE);

        assertThat(response.status()).isEqualTo("PASSED");
        assertThat(response.myChoice()).isEqualTo(BallotChoice.CHANGE);
        verify(revisionService).applyFromVote(TRIP_ID, PROPOSAL_ID);
        assertThatThrownBy(() -> service.cast(1L, TRIP_ID, VOTE_ID, BallotChoice.CHANGE))
                .isInstanceOf(VoteException.class)
                .extracting(error -> ((VoteException) error).errorCode())
                .isEqualTo(VoteErrorCode.VOTE_ALREADY_CLOSED);
        verify(revisionService).applyFromVote(TRIP_ID, PROPOSAL_ID);
    }

    @Test
    void aTieKeepsTheCurrentItinerary() {
        ItineraryVoteEntity vote = vote(NOW.plusSeconds(3600));
        ItineraryProposalEntity proposal = openProposal();
        ItineraryVoteVoterEntity voter1 = voter(1L);
        ItineraryVoteVoterEntity voter2 = voter(2L);
        ItineraryVoteBallotEntity ballot1 = ballot(1L, BallotChoice.KEEP_CURRENT);
        ItineraryVoteBallotEntity ballot2 = ballot(2L, BallotChoice.KEEP_CURRENT);
        arrangeVote(vote, proposal, List.of(voter1, voter2), List.of(ballot1, ballot2));
        when(voterRepository.findByVoteIdAndUserIdAndValidTrue(VOTE_ID, 1L)).thenReturn(Optional.of(voter1));
        when(ballotRepository.findByVoteIdAndUserId(VOTE_ID, 1L)).thenReturn(Optional.of(ballot1));

        var response = service.cast(1L, TRIP_ID, VOTE_ID, BallotChoice.CHANGE);

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.resultReason()).isEqualTo("TIE");
        assertThat(proposal.getStatus()).isEqualTo(ItineraryProposalStatus.REJECTED);
        verify(revisionService, never()).applyFromVote(TRIP_ID, PROPOSAL_ID);
    }

    @Test
    void removingAMemberInvalidatesTheirBallotAndRecalculatesTheResult() {
        ItineraryVoteEntity vote = vote(NOW.plusSeconds(3600));
        ItineraryProposalEntity proposal = openProposal();
        List<ItineraryVoteVoterEntity> voters = List.of(voter(1L), voter(2L), voter(3L));
        List<ItineraryVoteBallotEntity> ballots = List.of(
                ballot(1L, BallotChoice.CHANGE),
                ballot(2L, BallotChoice.CHANGE),
                ballot(3L, BallotChoice.KEEP_CURRENT)
        );
        when(voteRepository.findByTripIdAndStatus(TRIP_ID, VoteStatus.OPEN)).thenReturn(List.of(vote));
        when(voteRepository.findByIdForUpdate(VOTE_ID)).thenReturn(Optional.of(vote));
        when(voterRepository.findByVoteIdAndUserIdAndValidTrue(VOTE_ID, 3L)).thenReturn(Optional.of(voters.get(2)));
        when(ballotRepository.findByVoteIdAndUserId(VOTE_ID, 3L)).thenReturn(Optional.of(ballots.get(2)));
        when(voterRepository.findByVoteIdAndValidTrue(VOTE_ID))
                .thenAnswer(ignored -> voters.stream().filter(ItineraryVoteVoterEntity::isValid).toList());
        when(ballotRepository.findByVoteIdAndValidTrue(VOTE_ID))
                .thenAnswer(ignored -> ballots.stream().filter(ItineraryVoteBallotEntity::isValid).toList());
        when(proposalRepository.findByIdAndTripId(PROPOSAL_ID, TRIP_ID)).thenReturn(Optional.of(proposal));
        when(revisionService.applyFromVote(TRIP_ID, PROPOSAL_ID)).thenReturn(mock(ItineraryEntity.class));

        service.handleMembershipChanged(new TripMembershipChangedEvent(
                TRIP_ID, 3L, MembershipChangeType.REMOVED
        ));

        assertThat(voters.get(2).isValid()).isFalse();
        assertThat(ballots.get(2).isValid()).isFalse();
        assertThat(vote.getStatus()).isEqualTo(VoteStatus.PASSED);
        assertThat(vote.getResultReason()).isEqualTo("MEMBERSHIP_CHANGED");
        verify(revisionService).applyFromVote(TRIP_ID, PROPOSAL_ID);
    }

    @Test
    void deadlineClosesWithInsufficientParticipation() {
        ItineraryVoteEntity vote = vote(NOW.minusSeconds(1));
        ItineraryProposalEntity proposal = openProposal();
        List<ItineraryVoteVoterEntity> voters = List.of(voter(1L), voter(2L), voter(3L));
        List<ItineraryVoteBallotEntity> ballots = List.of(ballot(1L, BallotChoice.CHANGE));
        when(voteRepository.findByStatusAndDeadlineLessThanEqual(VoteStatus.OPEN, NOW)).thenReturn(List.of(vote));
        when(voteRepository.findByIdForUpdate(VOTE_ID)).thenReturn(Optional.of(vote));
        when(voterRepository.findByVoteIdAndValidTrue(VOTE_ID)).thenReturn(voters);
        when(ballotRepository.findByVoteIdAndValidTrue(VOTE_ID)).thenReturn(ballots);
        when(proposalRepository.findByIdAndTripId(PROPOSAL_ID, TRIP_ID)).thenReturn(Optional.of(proposal));

        service.closeExpiredVotes();

        assertThat(vote.getStatus()).isEqualTo(VoteStatus.INSUFFICIENT_PARTICIPATION);
        assertThat(vote.getResultReason()).isEqualTo("MINIMUM_NOT_MET");
        assertThat(proposal.getStatus()).isEqualTo(ItineraryProposalStatus.REJECTED);
        verify(revisionService, never()).applyFromVote(TRIP_ID, PROPOSAL_ID);
    }

    private void arrangeVote(
            ItineraryVoteEntity vote,
            ItineraryProposalEntity proposal,
            List<ItineraryVoteVoterEntity> voters,
            List<ItineraryVoteBallotEntity> ballots
    ) {
        when(voteRepository.findByIdAndTripIdForUpdate(VOTE_ID, TRIP_ID)).thenReturn(Optional.of(vote));
        when(proposalRepository.findByIdAndTripId(PROPOSAL_ID, TRIP_ID)).thenReturn(Optional.of(proposal));
        when(voterRepository.findByVoteIdAndValidTrue(VOTE_ID)).thenReturn(voters);
        when(ballotRepository.findByVoteIdAndValidTrue(VOTE_ID)).thenReturn(ballots);
    }

    private ItineraryVoteEntity vote(Instant deadline) {
        ItineraryVoteEntity vote = ItineraryVoteEntity.open(TRIP_ID, PROPOSAL_ID, 1L, deadline, NOW.minusSeconds(60));
        ReflectionTestUtils.setField(vote, "id", VOTE_ID);
        return vote;
    }

    private ItineraryProposalEntity openProposal() {
        ItineraryProposalEntity proposal = ItineraryProposalEntity.replaceItem(
                TRIP_ID, 100L, 1, 1L, 1, 1000L,
                "replacement-place", "교체 장소", LocalTime.of(11, 0), 60, "fingerprint", NOW.minusSeconds(60)
        );
        ReflectionTestUtils.setField(proposal, "id", PROPOSAL_ID);
        proposal.openVote(NOW.minusSeconds(30));
        return proposal;
    }

    private ItineraryVoteVoterEntity voter(Long userId) {
        ItineraryVoteVoterEntity voter = ItineraryVoteVoterEntity.create(VOTE_ID, userId);
        ReflectionTestUtils.setField(voter, "id", 100L + userId);
        return voter;
    }

    private ItineraryVoteBallotEntity ballot(Long userId, BallotChoice choice) {
        ItineraryVoteBallotEntity ballot = ItineraryVoteBallotEntity.cast(VOTE_ID, userId, choice, NOW.minusSeconds(10));
        ReflectionTestUtils.setField(ballot, "id", 200L + userId);
        return ballot;
    }
}
