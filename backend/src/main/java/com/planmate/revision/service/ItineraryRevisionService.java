package com.planmate.revision.service;

import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.proposal.entity.ItineraryProposalEntity;
import com.planmate.proposal.entity.ItineraryProposalStatus;
import com.planmate.proposal.entity.ProposalDecisionMode;
import com.planmate.proposal.exception.ProposalErrorCode;
import com.planmate.proposal.exception.ProposalException;
import com.planmate.proposal.repository.ItineraryProposalRepository;
import com.planmate.revision.api.event.ItineraryRevisionAppliedEvent;
import com.planmate.trip.api.TripRoleChecker;
import com.planmate.trip.domain.TripLifecycleClock;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryRevisionService {

    private final TripRepository tripRepository;
    private final TripRoleChecker tripRoleChecker;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository dayRepository;
    private final ItineraryItemRepository itemRepository;
    private final ItineraryProposalRepository proposalRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ItineraryRevisionService(
            TripRepository tripRepository,
            TripRoleChecker tripRoleChecker,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository dayRepository,
            ItineraryItemRepository itemRepository,
            ItineraryProposalRepository proposalRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripRepository = tripRepository;
        this.tripRoleChecker = tripRoleChecker;
        this.itineraryRepository = itineraryRepository;
        this.dayRepository = dayRepository;
        this.itemRepository = itemRepository;
        this.proposalRepository = proposalRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ItineraryEntity applyDirect(Long ownerId, Long tripId, Long proposalId) {
        tripRoleChecker.requireOwner(ownerId, tripId);
        return apply(ownerId, tripId, proposalId, false);
    }

    // Runs inside ItineraryVoteService's transaction. Keep this method free of a
    // transactional proxy: expected stale/window exceptions are caught by the vote
    // service so that it can persist a STALE result instead of marking the whole
    // outer transaction rollback-only.
    public ItineraryEntity applyFromVote(Long tripId, Long proposalId) {
        ItineraryProposalEntity proposal = proposalRepository.findByIdAndTripIdForUpdate(proposalId, tripId)
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
        return apply(proposal.getCreatedByUserId(), tripId, proposalId, true);
    }

    private ItineraryEntity apply(Long actorId, Long tripId, Long proposalId, boolean fromVote) {
        Instant now = Instant.now(clock);
        TripEntity trip = tripRepository.findByIdForUpdate(tripId)
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
        ItineraryProposalEntity proposal = proposalRepository.findByIdAndTripIdForUpdate(proposalId, tripId)
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.PROPOSAL_NOT_FOUND));
        if (proposal.getStatus() == ItineraryProposalStatus.APPLIED && proposal.getAppliedItineraryId() != null) {
            return itineraryRepository.findById(proposal.getAppliedItineraryId()).orElseThrow();
        }
        if (fromVote) {
            if (proposal.getDecisionMode() != ProposalDecisionMode.VOTE
                    || proposal.getStatus() != ItineraryProposalStatus.VOTE_OPEN) {
                throw new ProposalException(ProposalErrorCode.PROPOSAL_NOT_READY);
            }
        } else {
            if (proposal.getDecisionMode() == ProposalDecisionMode.VOTE) {
                throw new ProposalException(ProposalErrorCode.PROPOSAL_VOTE_BOUND);
            }
            if (proposal.getStatus() != ItineraryProposalStatus.READY) {
                throw new ProposalException(ProposalErrorCode.PROPOSAL_NOT_READY);
            }
            proposal.selectDirect(now);
        }

        Long currentId = trip.getCurrentItineraryId();
        if (!proposal.getBaseItineraryId().equals(currentId)) {
            throw new ProposalException(ProposalErrorCode.STALE_BASE_VERSION);
        }
        ItineraryEntity base = itineraryRepository.findById(currentId)
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.STALE_BASE_VERSION));
        if (base.getVersion() != proposal.getBaseItineraryVersion()) {
            throw new ProposalException(ProposalErrorCode.STALE_BASE_VERSION);
        }
        enforceMutationWindow(trip, base, proposal, now);

        int nextVersion = itineraryRepository.findMaxVersionByTripId(tripId) + 1;
        ItineraryEntity revision = itineraryRepository.saveAndFlush(ItineraryEntity.createRevision(
                tripId,
                now,
                nextVersion,
                base.getId(),
                proposal.getId(),
                fromVote ? "VOTE" : "DIRECT",
                actorId
        ));
        boolean replaced = false;
        for (ItineraryDayEntity baseDay : base.getDays().stream()
                .sorted(Comparator.comparingInt(ItineraryDayEntity::getDay)).toList()) {
            ItineraryDayEntity revisionDay = dayRepository.save(ItineraryDayEntity.create(
                    revision, baseDay.getDay(), baseDay.getDate()
            ));
            for (ItineraryItemEntity baseItem : baseDay.getItems().stream()
                    .sorted(Comparator.comparingInt(ItineraryItemEntity::getSequence)).toList()) {
                boolean target = baseItem.getId().equals(proposal.getTargetItemId());
                replaced |= target;
                itemRepository.save(ItineraryItemEntity.create(
                        revisionDay,
                        baseItem.getSequence(),
                        target ? proposal.getReplacementPlaceId() : baseItem.getPlaceId(),
                        target ? proposal.getReplacementStartTime() : baseItem.getStartTime(),
                        target ? proposal.getReplacementDurationMinutes() : baseItem.getDurationMinutes(),
                        target ? ItineraryItemCreatedSource.USER_SELECTED : baseItem.getCreatedSource()
                ));
            }
        }
        if (!replaced) {
            throw new ProposalException(ProposalErrorCode.STALE_BASE_VERSION);
        }
        itemRepository.flush();
        int updated = tripRepository.updateCurrentItineraryIdIfMatches(tripId, base.getId(), revision.getId());
        if (updated != 1) {
            throw new ProposalException(ProposalErrorCode.STALE_BASE_VERSION);
        }
        proposal.markApplied(revision.getId(), now);
        eventPublisher.publishEvent(new ItineraryRevisionAppliedEvent(
                tripId, revision.getId(), revision.getVersion(), proposal.getId()
        ));
        return revision;
    }

    private void enforceMutationWindow(
            TripEntity trip,
            ItineraryEntity base,
            ItineraryProposalEntity proposal,
            Instant now
    ) {
        TripLifecycleClock.TripLifecycleState state = TripLifecycleClock.resolve(
                now, trip.getTimezone(), trip.getStartDate(), trip.getEndDate()
        );
        if (state == TripLifecycleClock.TripLifecycleState.COMPLETED) {
            throw new ProposalException(ProposalErrorCode.ITINERARY_WINDOW_CLOSED);
        }
        if (state == TripLifecycleClock.TripLifecycleState.UPCOMING) return;

        ItineraryItemEntity target = base.getDays().stream()
                .flatMap(day -> day.getItems().stream())
                .filter(item -> item.getId().equals(proposal.getTargetItemId()))
                .findFirst()
                .orElseThrow(() -> new ProposalException(ProposalErrorCode.STALE_BASE_VERSION));
        Instant targetStart = ZonedDateTime.of(
                target.getDay().getDate(), target.getStartTime(), ZoneId.of(trip.getTimezone())
        ).toInstant();
        if (!targetStart.isAfter(now)) {
            throw new ProposalException(ProposalErrorCode.ITINERARY_WINDOW_CLOSED);
        }
    }
}
