package com.planmate.itinerary.service;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.api.event.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.exception.AiItineraryValidationException;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualItineraryResponsePersistenceService {

    private final AiItineraryDraftValidationService validationService;
    private final AiItineraryDraftNormalizer normalizer;
    private final GenerationCandidateSnapshotStore candidateSnapshotStore;
    private final ItineraryGenerationRepository generationRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ManualItineraryResponsePersistenceService(
            AiItineraryDraftValidationService validationService,
            AiItineraryDraftNormalizer normalizer,
            GenerationCandidateSnapshotStore candidateSnapshotStore,
            ItineraryGenerationRepository generationRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryItemRepository itineraryItemRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.validationService = validationService;
        this.normalizer = normalizer;
        this.candidateSnapshotStore = candidateSnapshotStore;
        this.generationRepository = generationRepository;
        this.itineraryRepository = itineraryRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void persistOrReplay(
            Long tripId,
            Long generationId,
            GenerationInputSnapshot snapshot,
            AiItineraryDraft draft,
            NormalizedAiItineraryDraft normalizedDraft
    ) {
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTripId().equals(tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        if (generation.getStatus() == ItineraryGenerationStatus.COMPLETED) {
            handleCompletedReplay(generationId, draft);
            return;
        }
        if (generation.getStatus() != ItineraryGenerationStatus.READY_FOR_PLANNING) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
        }
        if (snapshot == null || normalizedDraft == null) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
        }
        if (itineraryRepository.findByGeneration_Id(generationId).isPresent()) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_ITINERARY_STATE_INCONSISTENT);
        }

        Instant now = Instant.now(clock);
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        saveItinerary(generation, snapshot, normalizedDraft, now);
        generation.markCompleted(now);
        publishCompletedEvent(tripId, generation, previousStatus, generationId);
    }

    private void handleCompletedReplay(Long generationId, AiItineraryDraft draft) {
        ItineraryEntity itinerary = itineraryRepository.findByGeneration_Id(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_ITINERARY_STATE_INCONSISTENT));
        AiItineraryValidationReport report = validationService.validateStructure(generationId, draft);
        if (report.hasErrors()) {
            throw new AiItineraryValidationException(report);
        }
        NormalizedAiItineraryDraft incomingDraft = normalizer.normalize(generationId, draft);
        NormalizedAiItineraryDraft persistedDraft = normalizer.normalize(itinerary);
        if (!incomingDraft.equals(persistedDraft)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_ALREADY_COMPLETED_WITH_DIFFERENT_DRAFT);
        }
    }

    private void saveItinerary(
            ItineraryGenerationEntity generation,
            GenerationInputSnapshot snapshot,
            NormalizedAiItineraryDraft normalizedDraft,
            Instant now
    ) {
        int nextVersion = itineraryRepository.findMaxVersionByTripId(generation.getTripId()) + 1;
        ItineraryEntity itinerary = itineraryRepository.save(ItineraryEntity.create(generation, now, nextVersion));
        // ADR-0002: 저장 즉시 이 itinerary가 trip의 current pointer가 된다 — 기존
        // "createdAt desc가 항상 최신을 가리킨다"는 동작과 동일하게 유지한다.
        itineraryRepository.markAsCurrentForTrip(generation.getTripId(), itinerary.getId());
        for (NormalizedAiItineraryDraft.Day responseDay : normalizedDraft.days()) {
            ItineraryDayEntity day = itineraryDayRepository.save(ItineraryDayEntity.create(
                    itinerary,
                    responseDay.day(),
                    snapshot.startDate().plusDays(responseDay.day() - 1L)
            ));
            for (NormalizedAiItineraryDraft.Item responseItem : responseDay.items()) {
                itineraryItemRepository.save(ItineraryItemEntity.create(
                        day,
                        responseItem.sequence(),
                        responseItem.placeId(),
                        responseItem.startTime(),
                        responseItem.durationMinutes(),
                        ItineraryItemCreatedSource.AI_DRAFT
                ));
            }
        }
    }

    private void publishCompletedEvent(
            Long tripId,
            ItineraryGenerationEntity generation,
            ItineraryGenerationStatus previousStatus,
            Long generationId
    ) {
        long candidateCount = candidateSnapshotStore.countByGenerationId(generationId);
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
}
