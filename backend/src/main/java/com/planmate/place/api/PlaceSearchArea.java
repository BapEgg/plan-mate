package com.planmate.place.api;

public record PlaceSearchArea(
        GeoPoint center,
        GeoViewport viewport
) {

    public static PlaceSearchArea of(GeoPoint center, GeoViewport viewport) {
        return new PlaceSearchArea(center, viewport);
    }

    public static PlaceSearchArea circle(GeoPoint center) {
        return new PlaceSearchArea(center, null);
    }
}
