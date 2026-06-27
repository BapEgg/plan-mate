package com.planmate.trip.dto;

import java.util.List;

public record TripDestinationResponse(
        String placeId,
        String displayName,
        String formattedAddress,
        Double latitude,
        Double longitude,
        Double viewportLowLatitude,
        Double viewportLowLongitude,
        Double viewportHighLatitude,
        Double viewportHighLongitude,
        List<String> types,
        String primaryType
) {
}
