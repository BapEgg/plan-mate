package com.planmate.trip.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public record MustVisitPlaceSnapshot(
        String placeId,
        String name,
        String formattedAddress,
        Double latitude,
        Double longitude,
        List<String> types,
        String primaryType
) {

    @JsonIgnore
    public boolean isResolved() {
        return placeId != null && !placeId.isBlank() && latitude != null && longitude != null;
    }
}
