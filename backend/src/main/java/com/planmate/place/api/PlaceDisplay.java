package com.planmate.place.api;

public record PlaceDisplay(
        String placeId,
        String displayName,
        GeoPoint location,
        String googleMapsUri
) {
}
