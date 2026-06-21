package com.planmate.place.controller;

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

}
