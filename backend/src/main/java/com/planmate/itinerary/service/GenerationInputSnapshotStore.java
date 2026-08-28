package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.entity.ItineraryGenerationInputEntity;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.repository.ItineraryGenerationInputRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class GenerationInputSnapshotStore {

    private static final int CURRENT_SNAPSHOT_VERSION = 1;

    private final ItineraryGenerationInputRepository repository;

    public GenerationInputSnapshotStore(ItineraryGenerationInputRepository repository) {
        this.repository = repository;
    }

    public void save(Long generationId, GenerationInputSnapshot snapshot, Instant createdAt) {
        repository.save(ItineraryGenerationInputEntity.create(
                generationId,
                CURRENT_SNAPSHOT_VERSION,
                snapshot,
                createdAt
        ));
    }

    public GenerationInputSnapshot getRequired(Long generationId) {
        return repository.findById(generationId)
                .map(ItineraryGenerationInputEntity::getPayload)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_INPUT_NOT_FOUND));
    }
}
