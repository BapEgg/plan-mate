package com.planmate.place.controller;

import com.planmate.place.dto.AccommodationAutocompleteRequest;
import com.planmate.place.dto.PlaceInDestinationAutocompleteRequest;
import com.planmate.place.dto.PlaceAutocompleteRequest;
import com.planmate.place.dto.PlaceAutocompleteResponse;
import com.planmate.place.service.GooglePlacesService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final GooglePlacesService googlePlacesService;

    public PlaceController(GooglePlacesService googlePlacesService) {
        this.googlePlacesService = googlePlacesService;
    }

    @PostMapping("/autocomplete")
    public PlaceAutocompleteResponse autocomplete(@Valid @RequestBody PlaceAutocompleteRequest request) {
        return googlePlacesService.autocomplete(
                request.query(),
                request.languageCode()
        );
    }

    @PostMapping("/accommodations/autocomplete")
    public PlaceAutocompleteResponse autocompleteAccommodation(
            @Valid @RequestBody AccommodationAutocompleteRequest request
    ) {
        return googlePlacesService.autocompleteAccommodation(
                request.query(),
                request.destinationPlaceId(),
                request.languageCode()
        );
    }

    @PostMapping("/destination/autocomplete")
    public PlaceAutocompleteResponse autocompletePlaceInDestination(
            @Valid @RequestBody PlaceInDestinationAutocompleteRequest request
    ) {
        return googlePlacesService.autocompleteInDestination(
                request.query(),
                request.destinationPlaceId(),
                request.languageCode()
        );
    }

}
