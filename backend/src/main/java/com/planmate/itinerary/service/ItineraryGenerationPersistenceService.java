package com.planmate.itinerary.service;

import com.planmate.common.outbox.OutboxEventEntity;
import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.realtime.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryGenerationPersistenceService {

    private static final String ITINERARY_GENERATION_AGGREGATE_TYPE = "ITINERARY_GENERATION";
    private static final String ITINERARY_GENERATION_REQUESTED_EVENT_TYPE = "ITINERARY_GENERATION_REQUESTED";

    private final ItineraryGenerationRepository generationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TripRepository tripRepository;
    private final TripPlanningProfileRepository tripPlanningProfileRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ItineraryGenerationPersistenceService(
            ItineraryGenerationRepository generationRepository,
            OutboxEventRepository outboxEventRepository,
            TripRepository tripRepository,
            TripPlanningProfileRepository tripPlanningProfileRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.generationRepository = generationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tripRepository = tripRepository;
        this.tripPlanningProfileRepository = tripPlanningProfileRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
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
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        Instant now = Instant.now(clock);
        generation.markCollecting(now);
        publishStatusChanged(trip.getId(), generation, previousStatus, 0);
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
    public void markReadyForPlanning(Long generationId) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.markReady(Instant.now(clock));
        publishStatusChanged(generation.getTrip().getId(), generation, previousStatus, 0);
    }

    @Transactional
    public void markFailed(Long generationId, String safeReason) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.markFailed(safeReason, Instant.now(clock));
        publishStatusChanged(
                generation.getTrip().getId(),
                generation,
                previousStatus,
                0
        );
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
                0,
                generation.getFailureReason(),
                generation.getCreatedAt(),
                generation.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<ItineraryGenerationDetailResponse> getLatest(Long userId, Long tripId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        return generationRepository.findFirstByTrip_IdOrderByCreatedAtDesc(trip.getId())
                .map(generation -> toDetailResponse(trip, generation));
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
        return new AiRequestContext(generation, profile);
    }

    private ItineraryGenerationEntity findGeneration(Long generationId) {
        return generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
    }

    private ItineraryGenerationDetailResponse toDetailResponse(TripEntity trip, ItineraryGenerationEntity generation) {
        return new ItineraryGenerationDetailResponse(
                generation.getId().toString(),
                trip.getId().toString(),
                generation.getStatus(),
                generation.getPromptVersion(),
                0,
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
            TripPlanningProfileEntity profile
    ) {
    }
}
