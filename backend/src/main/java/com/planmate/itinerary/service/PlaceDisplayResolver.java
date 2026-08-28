package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
import com.planmate.place.api.PlaceDisplay;
import com.planmate.place.api.PlaceDisplayReader;
import com.planmate.place.api.exception.InvalidPlaceIdException;
import com.planmate.place.api.exception.PlaceProviderUnavailableException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlaceDisplayResolver {

    private final PlaceDisplayReader placeDisplayReader;

    public PlaceDisplayResolver(PlaceDisplayReader placeDisplayReader) {
        this.placeDisplayReader = placeDisplayReader;
    }

    public Map<String, ItineraryPlaceDisplayView> resolveListViews(List<String> placeIds) {
        Map<String, ItineraryPlaceDisplayView> result = new LinkedHashMap<>();
        for (String placeId : placeIds) {
            if (!StringUtils.hasText(placeId) || result.containsKey(placeId)) {
                continue;
            }
            result.put(placeId, resolveListView(placeId));
        }
        return result;
    }

    private ItineraryPlaceDisplayView resolveListView(String placeId) {
        try {
            PlaceDisplay display = placeDisplayReader.readDisplay(placeId, "ko");
            return ItineraryPlaceDisplayView.resolved(
                    display.displayName(),
                    display.location(),
                    display.googleMapsUri()
            );
        } catch (InvalidPlaceIdException | PlaceProviderUnavailableException exception) {
            return ItineraryPlaceDisplayView.unresolved();
        }
    }
}
