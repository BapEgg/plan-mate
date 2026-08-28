package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryRequest;
import com.planmate.itinerary.exception.ItineraryException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItineraryPromptServiceTest {

    private final AiItineraryRequestFactory requestFactory = new AiItineraryRequestFactory();
    private final ItineraryPromptService promptService = new ItineraryPromptService(JsonMapper.builder()
            .findAndAddModules()
            .build());

    @Test
    void createsV1PromptWithV1TemplateAndRequestJson() {
        AiItineraryRequest request = requestFactory.create(
                ItineraryPromptService.VERSION_V1,
                123L,
                snapshot(),
                List.of()
        );

        String prompt = promptService.createPrompt(ItineraryPromptService.VERSION_V1, request);

        assertThat(prompt).contains("\"generationId\" : \"123\"");
        assertThat(prompt).contains("\"mustVisitPlaces\"");
        assertThat(prompt).doesNotContain("Use only placeId values included in request.candidates.");
    }

    @Test
    void createsV2PromptWithCandidateJsonAndCandidateRules() {
        AiItineraryRequest request = requestFactory.create(
                ItineraryPromptService.VERSION_V2,
                123L,
                snapshot(),
                List.of(candidate(1, "place-1", true))
        );

        String prompt = promptService.createPrompt(ItineraryPromptService.VERSION_V2, request);

        assertThat(prompt).contains("Use only placeId values included in request.candidates.");
        assertThat(prompt).contains("Do not create, guess, infer, substitute, or complete any placeId outside request.candidates.");
        assertThat(prompt).contains("forcedMustVisit=true");
        assertThat(prompt).contains("request.dailyWindow");
        assertThat(prompt).contains("AiItineraryDraft");
        assertThat(prompt).contains("\"candidates\" : [");
        assertThat(prompt).contains("\"placeId\" : \"place-1\"");
        assertThat(prompt).contains("\"dailyWindow\"");
    }

    @Test
    void rejectsUnsupportedPromptVersion() {
        AiItineraryRequest request = requestFactory.create(
                ItineraryPromptService.VERSION_V1,
                123L,
                snapshot(),
                List.of()
        );

        assertThatThrownBy(() -> promptService.createPrompt("itinerary-plan-v999", request))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary prompt version is not supported.");
    }

    private GenerationInputSnapshot snapshot() {
        return new GenerationInputSnapshot(
                45L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new GenerationInputSnapshot.Destination(
                        "place-kyoto",
                        "Kyoto",
                        "Kyoto, Japan",
                        35.0,
                        135.0,
                        new GenerationInputSnapshot.Viewport(34.8, 134.8, 35.2, 135.2),
                        List.of("locality"),
                        "locality"
                ),
                new GenerationInputSnapshot.Companion(3, "FRIENDS", false, 0, null, true, 1),
                new GenerationInputSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD", "LODGING")),
                new GenerationInputSnapshot.Preference("BALANCED", List.of("FOOD", "SIGHTSEEING")),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new GenerationInputSnapshot.Accommodation(
                        "PLACE_SEARCH",
                        "DOWNTOWN",
                        "hotel-place",
                        "Kyoto Hotel",
                        "Hotel address",
                        35.1,
                        135.1,
                        List.of("lodging"),
                        "lodging",
                        LocalTime.of(15, 0),
                        LocalTime.of(11, 0)
                ),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                List.of(new GenerationInputSnapshot.MustVisitPlace(
                        "must-1",
                        "Kiyomizu",
                        "Kiyomizu address",
                        35.0,
                        135.0,
                        List.of("tourist_attraction"),
                        "tourist_attraction"
                )),
                List.of("LONG_WALK"),
                "Keep lunch flexible."
        );
    }

    private GenerationCandidateSnapshot candidate(int rank, String placeId, boolean forcedMustVisit) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                "Place " + rank,
                "Address " + rank,
                new GenerationCandidateSnapshot.Location(35.0 + rank / 100.0, 135.0 + rank / 100.0),
                "museum",
                List.of("museum", "point_of_interest"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of("MUST_VISIT"),
                forcedMustVisit,
                120.0,
                42.5
        );
    }
}
