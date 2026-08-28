package com.planmate.itinerary.service;

import com.planmate.common.outbox.OutboxEventEntity;
import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripPlanningSnapshot;
import com.planmate.trip.api.TripPlanningSnapshotReader;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryGenerationPersistenceService {

    private static final String ITINERARY_GENERATION_AGGREGATE_TYPE = "ITINERARY_GENERATION";
    private static final String ITINERARY_GENERATION_REQUESTED_EVENT_TYPE = "ITINERARY_GENERATION_REQUESTED";

    private final ItineraryGenerationRepository generationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TripAccessChecker tripAccessChecker;
    private final TripPlanningSnapshotReader tripPlanningSnapshotReader;
    private final GenerationInputSnapshotMapper generationInputSnapshotMapper;
    private final GenerationInputSnapshotStore generationInputSnapshotStore;
    private final GenerationCandidateSnapshotStore generationCandidateSnapshotStore;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ItineraryGenerationPersistenceService(
            ItineraryGenerationRepository generationRepository,
            OutboxEventRepository outboxEventRepository,
            TripAccessChecker tripAccessChecker,
            TripPlanningSnapshotReader tripPlanningSnapshotReader,
            GenerationInputSnapshotMapper generationInputSnapshotMapper,
            GenerationInputSnapshotStore generationInputSnapshotStore,
            GenerationCandidateSnapshotStore generationCandidateSnapshotStore,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.generationRepository = generationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tripAccessChecker = tripAccessChecker;
        this.tripPlanningSnapshotReader = tripPlanningSnapshotReader;
        this.generationInputSnapshotMapper = generationInputSnapshotMapper;
        this.generationInputSnapshotStore = generationInputSnapshotStore;
        this.generationCandidateSnapshotStore = generationCandidateSnapshotStore;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ItineraryGenerationEntity createGenerationRequest(Long userId, Long tripId, String promptVersion) {
        tripAccessChecker.checkAccessible(userId, tripId);
        TripPlanningSnapshot current = findPlanningSnapshot(tripId);
        ensureDestinationResolved(current);
        GenerationInputSnapshot inputSnapshot = generationInputSnapshotMapper.map(current);
        Instant now = Instant.now(clock);
        ItineraryGenerationEntity generation = generationRepository.save(ItineraryGenerationEntity.create(tripId, promptVersion, now));
        generationInputSnapshotStore.save(generation.getId(), inputSnapshot, now);
        outboxEventRepository.save(OutboxEventEntity.create(
                ITINERARY_GENERATION_AGGREGATE_TYPE,
                generation.getId().toString(),
                ITINERARY_GENERATION_REQUESTED_EVENT_TYPE,
                itineraryGenerationRequestedPayload(generation.getId(), tripId, userId),
                now
        ));
        return generation;
    }

    @Transactional
    public void markCollecting(Long generationId) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        generation.markCollecting(Instant.now(clock));
    }

    @Transactional
    public CollectionClaim claimCollection(
            Long tripId,
            Long generationId,
            boolean redelivered,
            java.time.Duration processingLease
    ) {
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generationBelongsToTrip(generation, tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        Instant now = Instant.now(clock);
        long claimVersion = generation.claimCollection(now, processingLease, redelivered);
        if (claimVersion < 0) {
            return CollectionClaim.notClaimed();
        }
        if (previousStatus == ItineraryGenerationStatus.CREATED) {
            publishStatusChanged(tripId, generation, previousStatus, 0);
        }
        return CollectionClaim.claimed(claimVersion);
    }

    @Transactional
    public boolean markCollectingIfCreated(Long userId, Long tripId, Long generationId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        return claimCollection(tripId, generationId, false, java.time.Duration.ofMinutes(15)).claimed();
    }

    @Transactional(readOnly = true)
    public GenerationCollectionContext loadCollectionContext(Long tripId, Long generationId) {
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generationBelongsToTrip(generation, tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        GenerationInputSnapshot snapshot = generationInputSnapshotStore.getRequired(generationId);
        ensureDestinationResolved(snapshot);
        return new GenerationCollectionContext(generation.getId(), snapshot);
    }

    @Transactional(readOnly = true)
    public GenerationCollectionContext loadCollectionContext(Long userId, Long tripId, Long generationId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        return loadCollectionContext(tripId, generationId);
    }

    @Transactional
    public boolean markFailed(Long generationId, long claimVersion, String safeReason) {
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.ownsCollectionClaim(claimVersion)) {
            return false;
        }
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.markFailed(safeReason, Instant.now(clock));
        long candidateCount = generationCandidateSnapshotStore.countByGenerationId(generationId);
        publishStatusChanged(
                generation.getTripId(),
                generation,
                previousStatus,
                candidateCount
        );
        return true;
    }

    @Transactional
    public void markFailed(Long generationId, String safeReason) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.markFailed(safeReason, Instant.now(clock));
        long candidateCount = generationCandidateSnapshotStore.countByGenerationId(generationId);
        publishStatusChanged(generation.getTripId(), generation, previousStatus, candidateCount);
    }

    @Transactional
    public CandidateSaveResult saveCandidatesAndMarkReady(
            Long generationId,
            long claimVersion,
            List<GenerationCandidateSnapshot> candidates
    ) {
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.ownsCollectionClaim(claimVersion)) {
            return CandidateSaveResult.stale();
        }

        List<GenerationCandidateSnapshot> safeCandidates = candidates == null
                ? List.of()
                : List.copyOf(candidates);
        validateCandidates(safeCandidates);

        ItineraryGenerationStatus previousStatus = generation.getStatus();
        int candidateCount = generationCandidateSnapshotStore.replaceAll(generation, safeCandidates);
        generation.markReady(Instant.now(clock));
        publishStatusChanged(generation.getTripId(), generation, previousStatus, candidateCount);
        return CandidateSaveResult.applied(candidateCount);
    }

    @Transactional
    public int saveCandidatesAndMarkReady(
            Long generationId,
            List<GenerationCandidateSnapshot> candidates
    ) {
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (generation.getStatus() == ItineraryGenerationStatus.READY_FOR_PLANNING) {
            return Math.toIntExact(generationCandidateSnapshotStore.countByGenerationId(generationId));
        }
        return saveCandidatesAndMarkReady(
                generationId,
                generation.getCollectionClaimVersion(),
                candidates
        ).candidateCount();
    }

    @Transactional(readOnly = true)
    public ItineraryGenerationDetailResponse getDetail(Long userId, Long tripId, Long generationId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generationBelongsToTrip(generation, tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        long candidateCount = generationCandidateSnapshotStore.countByGenerationId(generationId);
        return new ItineraryGenerationDetailResponse(
                generation.getId().toString(),
                tripId.toString(),
                generation.getStatus(),
                generation.getPromptVersion(),
                candidateCount,
                generation.getFailureReason(),
                generation.getCreatedAt(),
                generation.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<ItineraryGenerationDetailResponse> getLatest(Long userId, Long tripId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        return generationRepository.findFirstByTripIdOrderByCreatedAtDesc(tripId)
                .map(generation -> toDetailResponse(
                        tripId,
                        generation,
                        generationCandidateSnapshotStore.countByGenerationId(generation.getId())
                ));
    }

    @Transactional(readOnly = true)
    public AiRequestContext loadAiRequestContext(Long userId, Long tripId, Long generationId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generationBelongsToTrip(generation, tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        GenerationInputSnapshot snapshot = generationInputSnapshotStore.getRequired(generationId);
        List<GenerationCandidateSnapshot> candidates = generationCandidateSnapshotStore.findAllByGenerationId(generationId);
        return new AiRequestContext(
                generation.getId(),
                generation.getTripId(),
                generation.getStatus(),
                generation.getPromptVersion(),
                snapshot,
                candidates
        );
    }

    private ItineraryGenerationEntity findGeneration(Long generationId) {
        return generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
    }

    private boolean generationBelongsToTrip(ItineraryGenerationEntity generation, Long tripId) {
        return generation.getTripId().equals(tripId);
    }

    private TripPlanningSnapshot findPlanningSnapshot(Long tripId) {
        return tripPlanningSnapshotReader.findByTripId(tripId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
    }

    private void ensureDestinationResolved(TripPlanningSnapshot snapshot) {
        if (snapshot.destination().latitude() == null || snapshot.destination().longitude() == null) {
            throw new ItineraryException(ItineraryErrorCode.DESTINATION_NOT_RESOLVED);
        }
    }

    private void ensureDestinationResolved(GenerationInputSnapshot snapshot) {
        if (!snapshot.destination().isResolved()) {
            throw new ItineraryException(ItineraryErrorCode.DESTINATION_NOT_RESOLVED);
        }
    }

    private void validateCandidates(List<GenerationCandidateSnapshot> candidates) {
        if (candidates.isEmpty()) {
            throw new ItineraryException(ItineraryErrorCode.NO_RECOMMENDATION_CANDIDATES);
        }
        Set<String> placeIds = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (GenerationCandidateSnapshot candidate : candidates) {
            if (candidate == null) {
                throw invalidCandidates("candidate must not be null");
            }
            if (!candidate.hasPlaceId()) {
                throw invalidCandidates("candidate placeId must not be blank");
            }
            if (!candidate.hasLocation()) {
                throw invalidCandidates("candidate location is required");
            }
            if (candidate.rank() < 1) {
                throw invalidCandidates("candidate rank must start at 1");
            }
            if (!placeIds.add(candidate.placeId().trim())) {
                throw invalidCandidates("candidate placeId must be unique");
            }
            if (!ranks.add(candidate.rank())) {
                throw invalidCandidates("candidate rank must be unique");
            }
        }
        for (int rank = 1; rank <= candidates.size(); rank++) {
            if (!ranks.contains(rank)) {
                throw invalidCandidates("candidate rank must be continuous");
            }
        }
    }

    private IllegalArgumentException invalidCandidates(String message) {
        return new IllegalArgumentException(message);
    }

    private ItineraryGenerationDetailResponse toDetailResponse(
            Long tripId,
            ItineraryGenerationEntity generation,
            long candidateCount
    ) {
        return new ItineraryGenerationDetailResponse(
                generation.getId().toString(),
                tripId.toString(),
                generation.getStatus(),
                generation.getPromptVersion(),
                candidateCount,
                generation.getFailureReason(),
                generation.getCreatedAt(),
                generation.getUpdatedAt()
        );
    }

    private void publishStatusChanged(
            Long tripId,
            ItineraryGenerationEntity generation,
            ItineraryGenerationStatus previousStatus,
            long candidateCount
    ) {
        eventPublisher.publishEvent(new ItineraryGenerationStatusChangedEvent(
                tripId,
                generation.getId(),
                previousStatus,
                generation.getStatus(),
                candidateCount,
                generation.getFailureReason(),
                generation.getUpdatedAt()
        ));
    }

    private Map<String, Object> itineraryGenerationRequestedPayload(Long generationId, Long tripId, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generationId", generationId);
        payload.put("tripId", tripId);
        payload.put("userId", userId);
        return payload;
    }

    public record GenerationCollectionContext(
            Long generationId,
            GenerationInputSnapshot snapshot
    ) {
    }

    public record CollectionClaim(boolean claimed, long claimVersion) {
        static CollectionClaim claimed(long claimVersion) {
            return new CollectionClaim(true, claimVersion);
        }

        static CollectionClaim notClaimed() {
            return new CollectionClaim(false, 0L);
        }
    }

    public record CandidateSaveResult(boolean applied, int candidateCount) {
        static CandidateSaveResult applied(int candidateCount) {
            return new CandidateSaveResult(true, candidateCount);
        }

        static CandidateSaveResult stale() {
            return new CandidateSaveResult(false, 0);
        }
    }

    public record AiRequestContext(
            Long generationId,
            Long tripId,
            ItineraryGenerationStatus status,
            String promptVersion,
            GenerationInputSnapshot inputSnapshot,
            List<GenerationCandidateSnapshot> candidates
    ) {

        public AiRequestContext {
            candidates = candidates == null
                    ? List.of()
                    : List.copyOf(candidates);
        }
    }
}
