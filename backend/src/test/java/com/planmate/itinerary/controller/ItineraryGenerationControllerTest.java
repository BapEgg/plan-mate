package com.planmate.itinerary.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.service.ItineraryGenerationService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ItineraryGenerationControllerTest {

    private final ItineraryGenerationService generationService = Mockito.mock(ItineraryGenerationService.class);
    private final ItineraryGenerationController controller = new ItineraryGenerationController(generationService);

    @Test
    void latestReturnsTheMostRecentGeneration() {
        ItineraryGenerationDetailResponse generation = generation(1412L);
        given(generationService.getLatest(2588L, 1527L)).willReturn(Optional.of(generation));

        ResponseEntity<ItineraryGenerationDetailResponse> response = controller.getLatest(
                new AuthenticatedUser(2588L, "USER"),
                1527L
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(generation);
        verify(generationService).getLatest(2588L, 1527L);
    }

    @Test
    void latestReturnsNoContentWhenTheTripHasNoGeneration() {
        given(generationService.getLatest(2588L, 1527L)).willReturn(Optional.empty());

        ResponseEntity<ItineraryGenerationDetailResponse> response = controller.getLatest(
                new AuthenticatedUser(2588L, "USER"),
                1527L
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    private ItineraryGenerationDetailResponse generation(Long generationId) {
        Instant now = Instant.parse("2026-08-31T03:19:15Z");
        return new ItineraryGenerationDetailResponse(
                generationId.toString(),
                "1527",
                ItineraryGenerationStatus.COMPLETED,
                "itinerary-plan-v2",
                120,
                null,
                now,
                now
        );
    }
}
