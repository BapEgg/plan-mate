package com.planmate.place.api;

public record PlaceTextSearchQuery(
        String textQuery,
        String languageCode,
        int pageSize,
        PlaceSearchArea searchArea,
        String pageToken
) {
}
