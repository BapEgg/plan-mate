package com.planmate.itinerary.dto;

import com.planmate.place.api.GeoPoint;

public record ItineraryPlaceDisplayView(
        boolean resolved,
        String displayName,
        GeoPoint location,
        String googleMapsUri,
        String fallbackMessage,
        String source
) {
    public static ItineraryPlaceDisplayView resolved(String displayName, GeoPoint location, String googleMapsUri) {
        return new ItineraryPlaceDisplayView(true, displayName, location, googleMapsUri, null, "PROVIDER");
    }

    public static ItineraryPlaceDisplayView saved(String displayName, GeoPoint location) {
        return new ItineraryPlaceDisplayView(true, displayName, location, null, null, "SAVED_SNAPSHOT");
    }

    public static ItineraryPlaceDisplayView unresolved() {
        return new ItineraryPlaceDisplayView(
                false,
                null,
                null,
                null,
                "장소 정보를 불러오지 못했습니다.",
                "UNRESOLVED"
        );
    }
}
