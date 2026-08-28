package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryRequest;
import com.planmate.itinerary.exception.ItineraryException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiItineraryRequestFactoryTest {

    private final AiItineraryRequestFactory factory = new AiItineraryRequestFactory();
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void createsV1AiRequestShapeFromStoredInputSnapshot() {
        AiItineraryRequest request = factory.create(
                ItineraryPromptService.VERSION_V1,
                123L,
                snapshot(),
                List.of()
        );

        assertThat(request.generationId()).isEqualTo("123");
        assertThat(request.tripId()).isEqualTo("45");
        assertThat(request.destination()).satisfies(destination -> {
            assertThat(destination.placeId()).isEqualTo("place-kyoto");
            assertThat(destination.name()).isEqualTo("Kyoto");
            assertThat(destination.formattedAddress()).isEqualTo("Kyoto, Japan");
            assertThat(destination.latitude()).isEqualTo(35.0);
            assertThat(destination.longitude()).isEqualTo(135.0);
            assertThat(destination.types()).containsExactly("locality");
            assertThat(destination.primaryType()).isEqualTo("locality");
        });
        assertThat(request.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(request.companion()).satisfies(companion -> {
            assertThat(companion.count()).isEqualTo(3);
            assertThat(companion.type()).isEqualTo("FRIENDS");
            assertThat(companion.hasChildren()).isFalse();
            assertThat(companion.hasSeniors()).isTrue();
        });
        assertThat(request.budget().currencyCode()).isEqualTo("KRW");
        assertThat(request.budget().includedItems()).containsExactly("FOOD", "LODGING");
        assertThat(request.travelPace()).isEqualTo("BALANCED");
        assertThat(request.interests()).containsExactly("FOOD", "SIGHTSEEING");
        assertThat(request.transportation().primaryMode()).isEqualTo("PUBLIC_TRANSIT");
        assertThat(request.transportation().secondaryModes()).containsExactly("WALK");
        assertThat(request.accommodation()).satisfies(accommodation -> {
            assertThat(accommodation.mode()).isEqualTo("PLACE_SEARCH");
            assertThat(accommodation.preferredArea()).isEqualTo("DOWNTOWN");
            assertThat(accommodation.name()).isEqualTo("Kyoto Hotel");
            assertThat(accommodation.checkInTime()).isEqualTo(LocalTime.of(15, 0));
            assertThat(accommodation.checkOutTime()).isEqualTo(LocalTime.of(11, 0));
        });
        assertThat(request.mustVisitPlaces())
                .extracting(AiItineraryRequest.MustVisitPlace::placeId)
                .containsExactly("must-1");
        assertThat(request.avoidConditions()).containsExactly("LONG_WALK");
        assertThat(request.freeRequest()).isEqualTo("Keep lunch flexible.");
        assertThat(request.dailyWindow()).isNull();
        assertThat(request.candidates()).isEmpty();
        assertThat(request.rules()).isNotEmpty();
    }

    @Test
    void serializesV1WithoutCandidatesAndDailyWindowProperties() {
        AiItineraryRequest request = factory.create(
                ItineraryPromptService.VERSION_V1,
                123L,
                snapshot(),
                List.of()
        );

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.has("candidates")).isFalse();
        assertThat(json.has("dailyWindow")).isFalse();
        assertThat(json.get("rules").size()).isPositive();
    }

    @Test
    void createsV2RequestWithDailyWindowAndRankedCandidates() {
        AiItineraryRequest request = factory.create(
                ItineraryPromptService.VERSION_V2,
                123L,
                snapshot(),
                List.of(
                        candidate(1, "place-1", true),
                        candidate(2, "place-2", false)
                )
        );

        assertThat(request.dailyWindow()).isEqualTo(new AiItineraryRequest.DailyWindow(
                LocalTime.of(8, 0),
                LocalTime.of(20, 0)
        ));
        assertThat(request.rules()).isEmpty();
        assertThat(request.candidates())
                .extracting(AiItineraryRequest.Candidate::placeId)
                .containsExactly("place-1", "place-2");
        assertThat(request.candidates().getFirst()).satisfies(candidate -> {
            assertThat(candidate.rank()).isEqualTo(1);
            assertThat(candidate.displayName()).isEqualTo("Place 1");
            assertThat(candidate.formattedAddress()).isEqualTo("Address 1");
            assertThat(candidate.latitude()).isEqualTo(35.01);
            assertThat(candidate.longitude()).isEqualTo(135.01);
            assertThat(candidate.primaryType()).isEqualTo("museum");
            assertThat(candidate.types()).containsExactly("museum", "point_of_interest");
            assertThat(candidate.openingPeriods()).containsExactly("Mon 09:00-18:00");
            assertThat(candidate.forcedMustVisit()).isTrue();
        });
    }

    @Test
    void serializesV2WithoutInternalCandidateFieldsOrEmptyRules() {
        AiItineraryRequest request = factory.create(
                ItineraryPromptService.VERSION_V2,
                123L,
                snapshot(),
                List.of(candidate(1, "place-1", true))
        );

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.has("dailyWindow")).isTrue();
        assertThat(json.has("candidates")).isTrue();
        assertThat(json.has("rules")).isFalse();
        JsonNode candidate = json.get("candidates").get(0);
        assertThat(candidate.has("rank")).isTrue();
        assertThat(candidate.has("placeId")).isTrue();
        assertThat(candidate.has("displayName")).isTrue();
        assertThat(candidate.has("formattedAddress")).isTrue();
        assertThat(candidate.has("latitude")).isTrue();
        assertThat(candidate.has("longitude")).isTrue();
        assertThat(candidate.has("primaryType")).isTrue();
        assertThat(candidate.has("types")).isTrue();
        assertThat(candidate.has("openingPeriods")).isTrue();
        assertThat(candidate.has("forcedMustVisit")).isTrue();
        assertThat(candidate.has("score")).isFalse();
        assertThat(candidate.has("sourceCategories")).isFalse();
        assertThat(candidate.has("businessStatus")).isFalse();
        assertThat(candidate.has("rating")).isFalse();
        assertThat(candidate.has("userRatingCount")).isFalse();
        assertThat(candidate.has("distanceMeters")).isFalse();
    }

    @Test
    void rejectsV2ReadyRequestWhenCandidateSnapshotsAreMissing() {
        assertThatThrownBy(() -> factory.create(
                ItineraryPromptService.VERSION_V2,
                123L,
                snapshot(),
                List.of()
        ))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation candidate snapshots not found.");
    }

    @Test
    void allowsV1RequestWithoutCandidateSnapshots() {
        AiItineraryRequest request = factory.create(
                ItineraryPromptService.VERSION_V1,
                123L,
                snapshot(),
                List.of()
        );

        assertThat(request.candidates()).isEmpty();
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
                List.of(
                        new GenerationInputSnapshot.MustVisitPlace(
                                "must-1",
                                "Kiyomizu",
                                "Kiyomizu address",
                                35.0,
                                135.0,
                                List.of("tourist_attraction"),
                                "tourist_attraction"
                        ),
                        new GenerationInputSnapshot.MustVisitPlace(
                                "must-2",
                                "Unresolved",
                                null,
                                null,
                                null,
                                List.of(),
                                null
                        )
                ),
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
