package com.planmate.trip.domain;

import java.util.List;

public record ResolvedAccommodation(
        String placeId,
        String name,
        String formattedAddress,
        Double latitude,
        Double longitude,
        List<String> types,
        String primaryType
) {
}
