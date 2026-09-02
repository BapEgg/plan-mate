package com.planmate.regeneration.dto;

import com.planmate.regeneration.entity.RegenerationScopeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateItineraryRegenerationRequest(
        @NotNull Long baseItineraryId,
        int expectedItineraryVersion,
        @NotNull @Valid Scope scope,
        @Size(max = 1000) String additionalRequest
) {
    public record Scope(
            @NotNull RegenerationScopeType type,
            Integer dayNumber,
            Long startItemId,
            Long endItemId,
            List<Long> fixedItemIds
    ) {
        public Scope {
            fixedItemIds = fixedItemIds == null ? List.of() : List.copyOf(fixedItemIds);
        }
    }
}
