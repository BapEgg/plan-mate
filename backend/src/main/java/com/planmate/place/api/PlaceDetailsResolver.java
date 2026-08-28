package com.planmate.place.api;

public interface PlaceDetailsResolver {

    ResolvedPlace resolve(String placeId, String languageCode);
}
