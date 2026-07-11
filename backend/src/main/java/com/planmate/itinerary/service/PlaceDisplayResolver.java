package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.ItineraryPlaceDisplayView;
import com.planmate.place.dto.PlaceDisplay;
import com.planmate.place.exception.InvalidPlaceIdException;
import com.planmate.place.exception.PlaceProviderUnavailableException;
import com.planmate.place.service.GooglePlacesService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlaceDisplayResolver {

    private final GooglePlacesService googlePlacesService;

    public PlaceDisplayResolver(GooglePlacesService googlePlacesService) {
        this.googlePlacesService = googlePlacesService;
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
            PlaceDisplay display = googlePlacesService.resolvePlaceDisplay(placeId, "ko");
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
