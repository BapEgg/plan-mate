package com.planmate.itinerary.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ItineraryTripIdMappingTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void generationCreateStoresTripId() {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(45L, "test", NOW);

        assertThat(generation.getTripId()).isEqualTo(45L);
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.CREATED);
    }

    @Test
    void itineraryCreateUsesGenerationTripId() {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(45L, "test", NOW);
        ReflectionTestUtils.setField(generation, "id", 123L);

        ItineraryEntity itinerary = ItineraryEntity.create(generation, NOW);

        assertThat(itinerary.getTripId()).isEqualTo(generation.getTripId());
        assertThat(itinerary.getGeneration()).isSameAs(generation);
    }
}
