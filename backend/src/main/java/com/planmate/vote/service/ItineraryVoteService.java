package com.planmate.vote.service;

import com.planmate.membership.api.event.MembershipChangeType;
import com.planmate.membership.api.event.TripMembershipChangedEvent;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.proposal.api.event.ItineraryProposalChangedEvent;
import com.planmate.proposal.dto.ItineraryProposalResponse;
import com.planmate.proposal.entity.ItineraryProposalEntity;
import com.planmate.proposal.entity.ItineraryProposalStatus;
import com.planmate.proposal.exception.ProposalErrorCode;
import com.planmate.proposal.exception.ProposalException;
import com.planmate.proposal.repository.ItineraryProposalRepository;
import com.planmate.revision.service.ItineraryRevisionService;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripActiveMemberReader;
import com.planmate.trip.api.TripRoleChecker;
import com.planmate.vote.api.event.ItineraryVoteChangedEvent;
import com.planmate.vote.dto.ItineraryVoteResponse;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryVoteService {

    private static final Duration DEFAULT_DURATION = Duration.ofHours(24);

    private final TripAccessChecker tripAccessChecker;
    private final TripRoleChecker tripRoleChecker;
    private final TripActiveMemberReader activeMemberReader;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryProposalRepository proposalRepository;
    private final ItineraryVoteRepository voteRepository;
    private final ItineraryVoteVoterRepository voterRepository;
    private final ItineraryVoteBallotRepository ballotRepository;
    private final ItineraryRevisionService revisionService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ItineraryVoteService(
            TripAccessChecker tripAccessChecker,
            TripRoleChecker tripRoleChecker,
            TripActiveMemberReader activeMemberReader,
            ItineraryRepository itineraryRepository,
            ItineraryProposalRepository proposalRepository,
            ItineraryVoteRepository voteRepository,
            ItineraryVoteVoterRepository voterRepository,
            ItineraryVoteBallotRepository ballotRepository,
            ItineraryRevisionService revisionService,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.tripRoleChecker = tripRoleChecker;
        this.activeMemberReader = activeMemberReader;
        this.itineraryRepository = itineraryRepository;
        this.proposalRepository = proposalRepository;
        this.voteRepository = voteRepository;
        this.voterRepository = voterRepository;
        this.ballotRepository = ballotRepository;
        this.revisionService = revisionService;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ItineraryVoteResponse open(Long userId, Long tripId, Long proposalId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryProposalEntity proposal = proposalRepository.findByIdAndTripIdForUpdate(proposalId, tripId)
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
        ItineraryVoteEntity existing = voteRepository.findByProposalId(proposalId).orElse(null);
        if (existing != null) {
            return response(existing, proposal, userId);
        }
        if (proposal.getStatus() != ItineraryProposalStatus.READY) {
            throw new ProposalException(ProposalErrorCode.PROPOSAL_NOT_READY);
        }
        var current = itineraryRepository.findCurrentByTripId(tripId)
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.STALE_BASE_VERSION));
        if (!current.getId().equals(proposal.getBaseItineraryId())
                || current.getVersion() != proposal.getBaseItineraryVersion()) {
            throw new ProposalException(ProposalErrorCode.STALE_BASE_VERSION);
        }

        Instant now = Instant.now(clock);
        ItineraryVoteEntity vote = voteRepository.saveAndFlush(ItineraryVoteEntity.open(
                tripId, proposalId, userId, now.plus(DEFAULT_DURATION), now
        ));
        List<Long> voterIds = activeMemberReader.activeMemberIds(tripId);
        if (voterIds.isEmpty()) {
            throw new VoteException(VoteErrorCode.NOT_ELIGIBLE_VOTER);
        }
        voterRepository.saveAll(voterIds.stream()
                .map(voterId -> ItineraryVoteVoterEntity.create(vote.getId(), voterId))
                .toList());
        proposal.openVote(now);
        eventPublisher.publishEvent(new ItineraryProposalChangedEvent(
                tripId, proposalId, proposal.getStatus().name()
        ));
        publishVote(vote);
        return response(vote, proposal, userId);
    }

    @Transactional
    public ItineraryVoteResponse cast(
            Long userId,
            Long tripId,
            Long voteId,
            BallotChoice choice
    ) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryVoteEntity vote = lockedVote(tripId, voteId);
        Instant now = Instant.now(clock);
        if (vote.isOpen() && !now.isBefore(vote.getDeadline())) {
            finalizeVote(vote, now, "DEADLINE");
        }
        if (!vote.isOpen()) {
            throw new VoteException(VoteErrorCode.VOTE_ALREADY_CLOSED);
        }
        voterRepository.findByVoteIdAndUserIdAndValidTrue(voteId, userId)
                .orElseThrow(() -> new VoteException(VoteErrorCode.NOT_ELIGIBLE_VOTER));

        ItineraryVoteBallotEntity ballot = ballotRepository.findByVoteIdAndUserId(voteId, userId)
                .orElseGet(() -> ItineraryVoteBallotEntity.cast(voteId, userId, choice, now));
        if (ballot.getId() == null) {
            ballotRepository.save(ballot);
        } else {
            ballot.change(choice, now);
        }

        if (allValidVotersParticipated(voteId)) {
            finalizeVote(vote, now, "ALL_VOTED");
        } else {
            publishVote(vote);
        }
        ItineraryProposalEntity proposal = requiredProposal(vote);
        return response(vote, proposal, userId);
    }

    @Transactional
    public ItineraryVoteResponse cancel(Long userId, Long tripId, Long voteId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryVoteEntity vote = lockedVote(tripId, voteId);
        if (!vote.isOpen()) {
            throw new VoteException(VoteErrorCode.VOTE_ALREADY_CLOSED);
        }
        ItineraryProposalEntity proposal = requiredProposal(vote);
        if (!proposal.getCreatedByUserId().equals(userId)) {
            try {
                tripRoleChecker.requireOwner(userId, tripId);
            } catch (RuntimeException exception) {
                throw new VoteException(VoteErrorCode.VOTE_CANCEL_FORBIDDEN);
            }
        }
        Instant now = Instant.now(clock);
        vote.close(VoteStatus.CANCELLED, "CANCELLED_BY_USER", now);
        proposal.markCancelled(now);
        eventPublisher.publishEvent(new ItineraryProposalChangedEvent(
                tripId, proposal.getId(), proposal.getStatus().name()
        ));
        publishVote(vote);
        return response(vote, proposal, userId);
    }

    @Transactional
    public List<ItineraryVoteResponse> list(Long userId, Long tripId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        Instant now = Instant.now(clock);
        List<ItineraryVoteEntity> votes = voteRepository.findByTripIdOrderByStatusAscDeadlineAscIdAsc(tripId);
        for (ItineraryVoteEntity candidate : votes) {
            if (candidate.isOpen() && !now.isBefore(candidate.getDeadline())) {
                ItineraryVoteEntity locked = voteRepository.findByIdForUpdate(candidate.getId()).orElseThrow();
                finalizeVote(locked, now, "DEADLINE");
            }
        }
        return votes.stream().map(vote -> response(vote, requiredProposal(vote), userId)).toList();
    }

    @EventListener
    @Transactional
    public void handleMembershipChanged(TripMembershipChangedEvent event) {
        if (event.affectedUserId() == null
                || (event.changeType() != MembershipChangeType.REMOVED
                && event.changeType() != MembershipChangeType.LEFT)) {
            return;
        }
        Instant now = Instant.now(clock);
        for (ItineraryVoteEntity candidate : voteRepository.findByTripIdAndStatus(event.tripId(), VoteStatus.OPEN)) {
            ItineraryVoteEntity vote = voteRepository.findByIdForUpdate(candidate.getId()).orElseThrow();
            voterRepository.findByVoteIdAndUserIdAndValidTrue(vote.getId(), event.affectedUserId())
                    .ifPresent(voter -> voter.invalidate(now));
            ballotRepository.findByVoteIdAndUserId(vote.getId(), event.affectedUserId())
                    .ifPresent(ItineraryVoteBallotEntity::invalidate);
            if (allValidVotersParticipated(vote.getId())) {
                finalizeVote(vote, now, "MEMBERSHIP_CHANGED");
            } else {
                publishVote(vote);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.itinerary.vote.close-interval:PT30S}")
    @Transactional
    public void closeExpiredVotes() {
        Instant now = Instant.now(clock);
        for (ItineraryVoteEntity candidate : voteRepository
                .findByStatusAndDeadlineLessThanEqual(VoteStatus.OPEN, now)) {
            ItineraryVoteEntity vote = voteRepository.findByIdForUpdate(candidate.getId()).orElseThrow();
            finalizeVote(vote, now, "DEADLINE");
        }
    }

    private void finalizeVote(ItineraryVoteEntity vote, Instant now, String trigger) {
        if (!vote.isOpen()) return;
        int eligible = voterRepository.findByVoteIdAndValidTrue(vote.getId()).size();
        List<ItineraryVoteBallotEntity> ballots = ballotRepository.findByVoteIdAndValidTrue(vote.getId());
        int participation = ballots.size();
        int minimum = ItineraryVoteResponse.minimumParticipation(eligible);
        long change = ballots.stream().filter(ballot -> ballot.getChoice() == BallotChoice.CHANGE).count();
        long keep = ballots.stream().filter(ballot -> ballot.getChoice() == BallotChoice.KEEP_CURRENT).count();
        ItineraryProposalEntity proposal = requiredProposal(vote);

        if (participation < minimum) {
            vote.close(VoteStatus.INSUFFICIENT_PARTICIPATION, "MINIMUM_NOT_MET", now);
            proposal.markRejected(now);
        } else if (change <= keep) {
            vote.close(VoteStatus.REJECTED, change == keep ? "TIE" : "KEEP_CURRENT_WON", now);
            proposal.markRejected(now);
        } else {
            try {
                revisionService.applyFromVote(vote.getTripId(), proposal.getId());
                vote.close(VoteStatus.PASSED, trigger, now);
            } catch (ProposalException exception) {
                if (exception.errorCode() != ProposalErrorCode.STALE_BASE_VERSION
                        && exception.errorCode() != ProposalErrorCode.ITINERARY_WINDOW_CLOSED) {
                    throw exception;
                }
                vote.close(VoteStatus.STALE, exception.code(), now);
                proposal.markStale(now);
            }
        }
        eventPublisher.publishEvent(new ItineraryProposalChangedEvent(
                vote.getTripId(), proposal.getId(), proposal.getStatus().name()
        ));
        publishVote(vote);
    }

    private boolean allValidVotersParticipated(Long voteId) {
        int eligible = voterRepository.findByVoteIdAndValidTrue(voteId).size();
        int participated = ballotRepository.findByVoteIdAndValidTrue(voteId).size();
        return eligible == participated;
    }

    private ItineraryVoteEntity lockedVote(Long tripId, Long voteId) {
        return voteRepository.findByIdAndTripIdForUpdate(voteId, tripId)
                .orElseThrow(() -> new VoteException(VoteErrorCode.VOTE_NOT_FOUND));
    }

    private ItineraryProposalEntity requiredProposal(ItineraryVoteEntity vote) {
        return proposalRepository.findByIdAndTripId(vote.getProposalId(), vote.getTripId())
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
    }

    private ItineraryVoteResponse response(
            ItineraryVoteEntity vote,
            ItineraryProposalEntity proposal,
            Long userId
    ) {
        List<ItineraryVoteVoterEntity> voters = voterRepository.findByVoteIdAndValidTrue(vote.getId());
        List<ItineraryVoteBallotEntity> ballots = ballotRepository.findByVoteIdAndValidTrue(vote.getId());
        BallotChoice myChoice = ballots.stream()
                .filter(ballot -> ballot.getUserId().equals(userId))
                .map(ItineraryVoteBallotEntity::getChoice)
                .findFirst().orElse(null);
        int change = (int) ballots.stream().filter(ballot -> ballot.getChoice() == BallotChoice.CHANGE).count();
        int keep = ballots.size() - change;
        return new ItineraryVoteResponse(
                vote.getId().toString(),
                vote.getTripId().toString(),
                ItineraryProposalResponse.from(proposal),
                vote.getStatus().name(),
                voters.size(),
                ItineraryVoteResponse.minimumParticipation(voters.size()),
                ballots.size(),
                change,
                keep,
                voters.stream().anyMatch(voter -> voter.getUserId().equals(userId)),
                myChoice,
                vote.getDeadline(),
                vote.getClosedAt(),
                vote.getResultReason(),
                vote.getCreatedAt()
        );
    }

    private void publishVote(ItineraryVoteEntity vote) {
        eventPublisher.publishEvent(new ItineraryVoteChangedEvent(
                vote.getTripId(), vote.getId(), vote.getProposalId(), vote.getStatus().name()
        ));
    }
}
