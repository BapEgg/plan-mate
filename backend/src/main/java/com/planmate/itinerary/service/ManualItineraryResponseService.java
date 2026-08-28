package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.api.validation.AiItineraryValidationReport;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.AiItineraryValidationException;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.metrics.AiItineraryValidationMetrics;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.trip.api.TripAccessChecker;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ManualItineraryResponseService {

    private final TripAccessChecker tripAccessChecker;
    private final GenerationInputSnapshotStore generationInputSnapshotStore;
    private final GenerationCandidateSnapshotStore generationCandidateSnapshotStore;
    private final AiItineraryDraftValidationService aiItineraryDraftValidationService;
    private final AiItineraryDraftNormalizer aiItineraryDraftNormalizer;
    private final ItineraryGenerationRepository generationRepository;
    private final ManualItineraryResponsePersistenceService persistenceService;
    private final AiItineraryValidationMetrics validationMetrics;

    public ManualItineraryResponseService(
            TripAccessChecker tripAccessChecker,
            GenerationInputSnapshotStore generationInputSnapshotStore,
            GenerationCandidateSnapshotStore generationCandidateSnapshotStore,
            AiItineraryDraftValidationService aiItineraryDraftValidationService,
            AiItineraryDraftNormalizer aiItineraryDraftNormalizer,
            ItineraryGenerationRepository generationRepository,
            ManualItineraryResponsePersistenceService persistenceService,
            AiItineraryValidationMetrics validationMetrics
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.generationInputSnapshotStore = generationInputSnapshotStore;
        this.generationCandidateSnapshotStore = generationCandidateSnapshotStore;
        this.aiItineraryDraftValidationService = aiItineraryDraftValidationService;
        this.aiItineraryDraftNormalizer = aiItineraryDraftNormalizer;
        this.generationRepository = generationRepository;
        this.persistenceService = persistenceService;
        this.validationMetrics = validationMetrics;
    }

    public void submit(Long userId, Long tripId, Long generationId, AiItineraryDraft draft) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryGenerationEntity generation = getGeneration(tripId, generationId);

        if (generation.getStatus() == ItineraryGenerationStatus.READY_FOR_PLANNING) {
            GenerationInputSnapshot snapshot = generationInputSnapshotStore.getRequired(generationId);
            List<GenerationCandidateSnapshot> candidates = generationCandidateSnapshotStore.findAllByGenerationId(generationId);
            AiItineraryValidationReport report = aiItineraryDraftValidationService.validate(
                    generationId,
                    generation.getPromptVersion(),
                    snapshot,
                    candidates,
                    draft
            );
            validationMetrics.recordSubmit(report);
            throwIfValidationFailed(report);
            NormalizedAiItineraryDraft normalizedDraft = aiItineraryDraftNormalizer.normalize(generationId, draft);
            persistenceService.persistOrReplay(tripId, generationId, snapshot, draft, normalizedDraft);
            return;
        }
        if (generation.getStatus() == ItineraryGenerationStatus.COMPLETED) {
            persistenceService.persistOrReplay(tripId, generationId, null, draft, null);
            return;
        }
        throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
    }

    public AiItineraryValidationReport validate(Long userId, Long tripId, Long generationId, AiItineraryDraft draft) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryGenerationEntity generation = getGeneration(tripId, generationId);
        if (generation.getStatus() != ItineraryGenerationStatus.READY_FOR_PLANNING) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
        }

        GenerationInputSnapshot snapshot = generationInputSnapshotStore.getRequired(generationId);
        List<GenerationCandidateSnapshot> candidates = generationCandidateSnapshotStore.findAllByGenerationId(generationId);
        AiItineraryValidationReport report = aiItineraryDraftValidationService.validate(
                generationId,
                generation.getPromptVersion(),
                snapshot,
                candidates,
                draft
        );
        validationMetrics.recordValidate(report);
        return report;
    }

    private ItineraryGenerationEntity getGeneration(Long tripId, Long generationId) {
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTripId().equals(tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        return generation;
    }

    private void throwIfValidationFailed(AiItineraryValidationReport report) {
        if (report.hasErrors()) {
            throw new AiItineraryValidationException(report);
        }
    }
}
