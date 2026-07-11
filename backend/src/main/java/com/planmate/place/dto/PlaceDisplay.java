package com.planmate.place.dto;

public record PlaceDisplay(
        String placeId,
        String displayName,
        GeoPoint location,
        String googleMapsUri
) {
}
