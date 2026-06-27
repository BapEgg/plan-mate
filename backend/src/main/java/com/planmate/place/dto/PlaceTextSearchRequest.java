package com.planmate.place.dto;

public record PlaceTextSearchRequest(
        String textQuery,
        String languageCode,
        int pageSize,
        PlaceSearchArea searchArea,
        String pageToken
) {
}
