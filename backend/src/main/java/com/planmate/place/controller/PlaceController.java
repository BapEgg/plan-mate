package com.planmate.place.controller;

import com.planmate.place.api.PlaceAutocompleteQuery;
import com.planmate.place.api.PlaceAutocompleteResult;
import com.planmate.place.controller.dto.AccommodationAutocompleteRequest;
import com.planmate.place.controller.dto.PlaceAutocompleteRequest;
import com.planmate.place.controller.dto.PlaceInDestinationAutocompleteRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final PlaceAutocompleteQuery placeAutocompleteQuery;

    public PlaceController(PlaceAutocompleteQuery placeAutocompleteQuery) {
        this.placeAutocompleteQuery = placeAutocompleteQuery;
    }

    @PostMapping("/autocomplete")
    public PlaceAutocompleteResult autocomplete(@Valid @RequestBody PlaceAutocompleteRequest request) {
        return placeAutocompleteQuery.autocomplete(
                request.query(),
                request.languageCode()
        );
    }

    @PostMapping("/accommodations/autocomplete")
    public PlaceAutocompleteResult autocompleteAccommodation(
            @Valid @RequestBody AccommodationAutocompleteRequest request
    ) {
        return placeAutocompleteQuery.autocompleteAccommodation(
                request.query(),
                request.destinationPlaceId(),
                request.languageCode()
        );
    }

    @PostMapping("/destination/autocomplete")
    public PlaceAutocompleteResult autocompletePlaceInDestination(
            @Valid @RequestBody PlaceInDestinationAutocompleteRequest request
    ) {
        return placeAutocompleteQuery.autocompleteInDestination(
                request.query(),
                request.destinationPlaceId(),
                request.languageCode()
        );
    }

}
