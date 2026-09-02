package com.planmate.itinerary.api;

import java.util.List;
import java.util.Optional;

public interface RegenerationConstraintProvider {
    Optional<Constraint> findByGenerationId(Long generationId);

    record Constraint(
            String scope,
            Integer dayNumber,
            Long startItemId,
            Long endItemId,
            List<Long> fixedItemIds,
            String additionalRequest,
            List<Item> currentItems
    ) {
        public Constraint {
            fixedItemIds = fixedItemIds == null ? List.of() : List.copyOf(fixedItemIds);
            currentItems = currentItems == null ? List.of() : List.copyOf(currentItems);
        }
    }

    record Item(Long itemId, int day, int sequence, String placeId, String startTime, int durationMinutes, String action) {
    }
}
