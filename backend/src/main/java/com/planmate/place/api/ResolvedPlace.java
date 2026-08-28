package com.planmate.place.api;

import java.util.List;

public record ResolvedPlace(
        String placeId,
        String displayName,
        String formattedAddress,
        GeoPoint location,
        GeoViewport viewport,
        List<String> types,
        String primaryType
) {

    public ResolvedPlace {
        types = types == null ? List.of() : List.copyOf(types);
    }
}
