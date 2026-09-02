package com.planmate.itinerary.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.itinerary.dto.AiItineraryDraft;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;

class FixtureItineraryDraftProviderTest {

    private final FixtureItineraryDraftProvider provider = new FixtureItineraryDraftProvider(
            new ObjectMapper(),
            new DefaultResourceLoader()
    );

    @Test
    void loadsMinimumConditionsFixtureAndUsesTheCurrentGenerationId() throws IOException {
        AiItineraryDraft draft = provider.load(
                2001L,
                2,
                fixturePlaceIds("fixtures/itinerary/manual-itinerary-1360-min.json")
        ).orElseThrow();

        assertThat(draft.generationId()).isEqualTo("2001");
        assertThat(draft.days()).hasSize(2);
        assertThat(draft.days().getFirst().items()).hasSize(6);
        assertThat(draft.days().getFirst().items().getFirst().placeId())
                .isEqualTo("ChIJ653o87r7DDUR4SQEgcFz8u4");
    }

    @Test
    void loadsMaximumConditionsFixtureAndUsesTheCurrentGenerationId() throws IOException {
        AiItineraryDraft draft = provider.load(
                2002L,
                4,
                fixturePlaceIds("fixtures/itinerary/manual-itinerary-1368-max.json")
        ).orElseThrow();

        assertThat(draft.generationId()).isEqualTo("2002");
        assertThat(draft.days()).hasSize(4);
        assertThat(draft.days().getLast().items()).hasSize(4);
        assertThat(draft.days().getLast().items().getLast().placeId())
                .isEqualTo("ChIJk8x74-yifDURXVhW9zZEvZk");
    }

    @Test
    void prefersGeojeFixtureWhenItsPlacesMatchTheGeneration() throws IOException {
        AiItineraryDraft draft = provider.load(
                2004L,
                4,
                fixturePlaceIds("fixtures/itinerary/manual-itinerary-1413-geoje-max.json")
        ).orElseThrow();

        assertThat(draft.generationId()).isEqualTo("2004");
        assertThat(draft.days()).hasSize(4);
        assertThat(draft.days().getFirst().items().getFirst().placeId())
                .isEqualTo("ChIJrdpOcvUraTURT1ADAw44ZVI");
    }

    @Test
    void doesNotSubmitAnIncompatibleFixture() {
        assertThat(provider.load(2005L, 4, Set.of("unrelated-place"))).isEmpty();
    }

    @Test
    void leavesUnsupportedTripDurationsReadyForManualHandoff() {
        assertThat(provider.load(2003L, 3, Set.of())).isEmpty();
    }

    private Set<String> fixturePlaceIds(String location) throws IOException {
        try (InputStream inputStream = new ClassPathResource(location).getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(inputStream);
            Set<String> placeIds = new LinkedHashSet<>();
            root.path("days").forEach(day -> day.path("items")
                    .forEach(item -> placeIds.add(item.path("placeId").asText())));
            return Set.copyOf(placeIds);
        }
    }
}
