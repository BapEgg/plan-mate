package com.planmate.itinerary.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.itinerary.dto.AiItineraryDraft;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class FixtureItineraryDraftProviderTest {

    private final FixtureItineraryDraftProvider provider = new FixtureItineraryDraftProvider(
            new ObjectMapper(),
            new DefaultResourceLoader()
    );

    @Test
    void loadsMinimumConditionsFixtureAndUsesTheCurrentGenerationId() {
        AiItineraryDraft draft = provider.load(2001L, 2).orElseThrow();

        assertThat(draft.generationId()).isEqualTo("2001");
        assertThat(draft.days()).hasSize(2);
        assertThat(draft.days().getFirst().items()).hasSize(6);
        assertThat(draft.days().getFirst().items().getFirst().placeId())
                .isEqualTo("ChIJ653o87r7DDUR4SQEgcFz8u4");
    }

    @Test
    void loadsMaximumConditionsFixtureAndUsesTheCurrentGenerationId() {
        AiItineraryDraft draft = provider.load(2002L, 4).orElseThrow();

        assertThat(draft.generationId()).isEqualTo("2002");
        assertThat(draft.days()).hasSize(4);
        assertThat(draft.days().getLast().items()).hasSize(4);
        assertThat(draft.days().getLast().items().getLast().placeId())
                .isEqualTo("ChIJk8x74-yifDURXVhW9zZEvZk");
    }

    @Test
    void leavesUnsupportedTripDurationsReadyForManualHandoff() {
        assertThat(provider.load(2003L, 3)).isEmpty();
    }
}
