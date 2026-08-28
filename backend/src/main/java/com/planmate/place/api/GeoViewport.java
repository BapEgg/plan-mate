package com.planmate.place.api;

public record GeoViewport(
        GeoPoint low,
        GeoPoint high
) {

    public boolean contains(GeoPoint point) {
        if (point == null || low == null || high == null) {
            return false;
        }
        return point.latitude() >= low.latitude()
                && point.latitude() <= high.latitude()
                && point.longitude() >= low.longitude()
                && point.longitude() <= high.longitude();
    }

    public GeoPoint center() {
        if (low == null || high == null) {
            return null;
        }
        return new GeoPoint(
                (low.latitude() + high.latitude()) / 2,
                (low.longitude() + high.longitude()) / 2
        );
    }
}
