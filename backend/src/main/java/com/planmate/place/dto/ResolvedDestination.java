package com.planmate.place.dto;

import java.util.List;

public record ResolvedDestination(
        String placeId,
        String displayName,
        String formattedAddress,
        GeoPoint location,
        GeoViewport viewport,
        List<String> types,
        String primaryType
) {
}
