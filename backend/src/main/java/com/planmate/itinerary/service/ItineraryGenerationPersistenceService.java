package com.planmate.itinerary.service;

import com.planmate.common.outbox.OutboxEventEntity;
import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.entity.PlaceCandidateEntity;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.PlaceCandidateRepository;
import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.recommendation.domain.CollectedPlaceCandidate;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryGenerationPersistenceService {

    private static final String ITINERARY_GENERATION_AGGREGATE_TYPE = "ITINERARY_GENERATION";
    private static final String ITINERARY_GENERATION_REQUESTED_EVENT_TYPE = "ITINERARY_GENERATION_REQUESTED";

    private final ItineraryGenerationRepository generationRepository;
    private final PlaceCandidateRepository placeCandidateRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TripRepository tripRepository;
    private final TripPlanningProfileRepository tripPlanningProfileRepository;
    private final Clock clock;

    public ItineraryGenerationPersistenceService(
            ItineraryGenerationRepository generationRepository,
            PlaceCandidateRepository placeCandidateRepository,
            OutboxEventRepository outboxEventRepository,
            TripRepository tripRepository,
            TripPlanningProfileRepository tripPlanningProfileRepository,
            Clock clock
    ) {
        this.generationRepository = generationRepository;
        this.placeCandidateRepository = placeCandidateRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tripRepository = tripRepository;
        this.tripPlanningProfileRepository = tripPlanningProfileRepository;
        this.clock = clock;
    }

    @Transactional
    public ItineraryGenerationEntity createGenerationRequest(Long userId, Long tripId, String promptVersion) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        Instant now = Instant.now(clock);
        ItineraryGenerationEntity generation = generationRepository.save(ItineraryGenerationEntity.create(trip, promptVersion, now));
        outboxEventRepository.save(OutboxEventEntity.create(
                ITINERARY_GENERATION_AGGREGATE_TYPE,
                generation.getId().toString(),
                ITINERARY_GENERATION_REQUESTED_EVENT_TYPE,
                itineraryGenerationRequestedPayload(generation.getId(), trip.getId(), userId),
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
    public boolean markCollectingIfCreated(Long userId, Long tripId, Long generationId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        if (generation.getStatus() != ItineraryGenerationStatus.CREATED) {
            return false;
        }
        generation.markCollecting(Instant.now(clock));
        return true;
    }

    @Transactional(readOnly = true)
    public GenerationCollectionContext loadCollectionContext(Long userId, Long tripId, Long generationId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        TripPlanningProfileEntity profile = tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
        return new GenerationCollectionContext(generation.getId(), toResolvedDestination(trip), profile);
    }

    @Transactional
    public void saveCandidatesAndMarkReady(Long generationId, List<CollectedPlaceCandidate> candidates) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        int rank = 1;
        for (CollectedPlaceCandidate candidate : candidates) {
            placeCandidateRepository.save(PlaceCandidateEntity.from(generation, candidate, rank++));
        }
        generation.markReady(Instant.now(clock));
    }

    @Transactional
    public void markFailed(Long generationId, String safeReason) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        generation.markFailed(safeReason, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public ItineraryGenerationDetailResponse getDetail(Long userId, Long tripId, Long generationId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        return new ItineraryGenerationDetailResponse(
                generation.getId().toString(),
                trip.getId().toString(),
                generation.getStatus(),
                generation.getPromptVersion(),
                placeCandidateRepository.countByGeneration_Id(generation.getId()),
                generation.getFailureReason(),
                generation.getCreatedAt(),
                generation.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public AiRequestContext loadAiRequestContext(Long userId, Long tripId, Long generationId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findWithTripById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        TripPlanningProfileEntity profile = tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
        List<PlaceCandidateEntity> candidates = placeCandidateRepository.findByGeneration_IdOrderByRankAsc(generationId);
        return new AiRequestContext(generation, profile, candidates);
    }

    private ItineraryGenerationEntity findGeneration(Long generationId) {
        return generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
    }

    private Map<String, Object> itineraryGenerationRequestedPayload(Long generationId, Long tripId, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generationId", generationId);
        payload.put("tripId", tripId);
        payload.put("userId", userId);
        return payload;
    }

    private ResolvedDestination toResolvedDestination(TripEntity trip) {
        if (trip.getDestinationLatitude() == null || trip.getDestinationLongitude() == null) {
            throw new ItineraryException(ItineraryErrorCode.DESTINATION_NOT_RESOLVED);
        }
        GeoPoint location = new GeoPoint(trip.getDestinationLatitude(), trip.getDestinationLongitude());
        GeoViewport viewport = null;
        if (trip.getDestinationViewportLowLatitude() != null
                && trip.getDestinationViewportLowLongitude() != null
                && trip.getDestinationViewportHighLatitude() != null
                && trip.getDestinationViewportHighLongitude() != null) {
            viewport = new GeoViewport(
                    new GeoPoint(trip.getDestinationViewportLowLatitude(), trip.getDestinationViewportLowLongitude()),
                    new GeoPoint(trip.getDestinationViewportHighLatitude(), trip.getDestinationViewportHighLongitude())
            );
        }
        return new ResolvedDestination(
                trip.getDestinationPlaceId(),
                trip.getDestination(),
                trip.getDestinationFormattedAddress(),
                location,
                viewport,
                trip.getDestinationTypes(),
                trip.getDestinationPrimaryType()
        );
    }

    public record GenerationCollectionContext(
            Long generationId,
            ResolvedDestination destination,
            TripPlanningProfileEntity profile
    ) {
    }

    public record AiRequestContext(
            ItineraryGenerationEntity generation,
            TripPlanningProfileEntity profile,
            List<PlaceCandidateEntity> candidates
    ) {
    }
}
