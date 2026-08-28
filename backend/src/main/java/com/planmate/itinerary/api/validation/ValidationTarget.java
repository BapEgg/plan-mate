package com.planmate.itinerary.api.validation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValidationTarget(
        String path,
        Integer day,
        Integer sequence,
        String placeId
) {
}
