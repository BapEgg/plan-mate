package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.entity.ItineraryGenerationInputEntity;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.repository.ItineraryGenerationInputRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class GenerationInputSnapshotStoreTest {

    private final ItineraryGenerationInputRepository repository = Mockito.mock(ItineraryGenerationInputRepository.class);
    private final GenerationInputSnapshotStore store = new GenerationInputSnapshotStore(repository);

    @Test
    void savesCurrentVersionSnapshotEntity() {
        GenerationInputSnapshot snapshot = snapshot();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        store.save(123L, snapshot, createdAt);

        ArgumentCaptor<ItineraryGenerationInputEntity> captor = ArgumentCaptor.forClass(ItineraryGenerationInputEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getGenerationId()).isEqualTo(123L);
        assertThat(captor.getValue().getSnapshotVersion()).isEqualTo(1);
        assertThat(captor.getValue().getPayload()).isSameAs(snapshot);
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void getRequiredReturnsPayload() {
        GenerationInputSnapshot snapshot = snapshot();
        given(repository.findById(123L)).willReturn(Optional.of(ItineraryGenerationInputEntity.create(
                123L,
                1,
                snapshot,
                Instant.parse("2026-01-01T00:00:00Z")
        )));

        assertThat(store.getRequired(123L)).isSameAs(snapshot);
    }

    @Test
    void getRequiredThrowsWhenSnapshotDoesNotExist() {
        given(repository.findById(123L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> store.getRequired(123L))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation input snapshot not found.");
    }

    private GenerationInputSnapshot snapshot() {
        return new GenerationInputSnapshot(
                45L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new GenerationInputSnapshot.Destination("place", "Kyoto", "address", 35.0, 135.0, null, List.of(), "locality"),
                new GenerationInputSnapshot.Companion(2, "FRIENDS", false, 0, null, false, 0),
                new GenerationInputSnapshot.Budget("KRW", 1000L, "BALANCED", List.of()),
                new GenerationInputSnapshot.Preference("BALANCED", List.of()),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of()),
                new GenerationInputSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                List.of(),
                List.of(),
                null
        );
    }
}
