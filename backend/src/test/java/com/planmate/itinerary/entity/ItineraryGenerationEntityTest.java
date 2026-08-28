package com.planmate.itinerary.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import java.time.Instant;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ItineraryGenerationEntityTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createdGenerationCanMoveToCollecting() {
        ItineraryGenerationEntity generation = generation();

        generation.markCollecting(NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
    }

    @Test
    void createdGenerationCannotMoveDirectlyToReady() {
        ItineraryGenerationEntity generation = generation();

        assertThatThrownBy(() -> generation.markReady(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected COLLECTING_CANDIDATES");
    }

    @Test
    void collectingGenerationCanMoveToReady() {
        ItineraryGenerationEntity generation = collectingGeneration();

        generation.markReady(NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
    }

    @Test
    void collectingGenerationCannotMoveDirectlyToCompleted() {
        ItineraryGenerationEntity generation = collectingGeneration();

        assertThatThrownBy(() -> generation.markCompleted(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected READY_FOR_PLANNING");
    }

    @Test
    void readyGenerationCanMoveToCompleted() {
        ItineraryGenerationEntity generation = readyGeneration();

        generation.markCompleted(NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
    }

    @Test
    void completedGenerationCannotMoveToAnotherStatus() {
        ItineraryGenerationEntity generation = completedGeneration();

        assertThatThrownBy(() -> generation.markCollecting(NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> generation.markReady(NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> generation.markCompleted(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createdGenerationCanMoveToFailed() {
        ItineraryGenerationEntity generation = generation();

        generation.markFailed("FAILED", NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.FAILED);
    }

    @Test
    void collectingGenerationCanMoveToFailed() {
        ItineraryGenerationEntity generation = collectingGeneration();

        generation.markFailed("FAILED", NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.FAILED);
    }

    @Test
    void readyGenerationCannotMoveToFailed() {
        ItineraryGenerationEntity generation = readyGeneration();

        assertThatThrownBy(() -> generation.markFailed("FAILED", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected CREATED or COLLECTING_CANDIDATES");
    }

    @Test
    void completedGenerationCannotMoveToFailed() {
        ItineraryGenerationEntity generation = completedGeneration();

        assertThatThrownBy(() -> generation.markFailed("FAILED", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected CREATED or COLLECTING_CANDIDATES");
    }

    @Test
    void createdGenerationClaimStartsVersionAndLease() {
        ItineraryGenerationEntity generation = generation();

        long claimVersion = generation.claimCollection(NOW, Duration.ofMinutes(15), false);

        assertThat(claimVersion).isEqualTo(1L);
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
        assertThat(generation.getCollectionLeaseExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(generation.ownsCollectionClaim(1L)).isTrue();
    }

    @Test
    void freshDuplicateCannotReclaimValidLease() {
        ItineraryGenerationEntity generation = generation();
        generation.claimCollection(NOW, Duration.ofMinutes(15), false);

        long claimVersion = generation.claimCollection(NOW.plusSeconds(30), Duration.ofMinutes(15), false);

        assertThat(claimVersion).isEqualTo(-1L);
        assertThat(generation.getCollectionClaimVersion()).isEqualTo(1L);
    }

    @Test
    void redeliveryReclaimsValidLeaseAndFencesPreviousClaim() {
        ItineraryGenerationEntity generation = generation();
        generation.claimCollection(NOW, Duration.ofMinutes(15), false);

        long claimVersion = generation.claimCollection(NOW.plusSeconds(30), Duration.ofMinutes(15), true);

        assertThat(claimVersion).isEqualTo(2L);
        assertThat(generation.ownsCollectionClaim(1L)).isFalse();
        assertThat(generation.ownsCollectionClaim(2L)).isTrue();
    }

    @Test
    void expiredOrNullLeaseCanBeReclaimedWithoutRabbitRedelivery() {
        ItineraryGenerationEntity generation = generation();
        generation.markCollecting(NOW);

        long firstClaim = generation.claimCollection(NOW, Duration.ofMinutes(15), false);
        long secondClaim = generation.claimCollection(NOW.plus(Duration.ofMinutes(16)), Duration.ofMinutes(15), false);

        assertThat(firstClaim).isEqualTo(1L);
        assertThat(secondClaim).isEqualTo(2L);
    }

    @Test
    void terminalStatusesCannotBeClaimed() {
        assertThat(readyGeneration().claimCollection(NOW, Duration.ofMinutes(15), true)).isEqualTo(-1L);
        assertThat(completedGeneration().claimCollection(NOW, Duration.ofMinutes(15), true)).isEqualTo(-1L);
        ItineraryGenerationEntity failed = generation();
        failed.markFailed("FAILED", NOW);
        assertThat(failed.claimCollection(NOW, Duration.ofMinutes(15), true)).isEqualTo(-1L);
    }

    private ItineraryGenerationEntity generation() {
        return ItineraryGenerationEntity.create(45L, "test", NOW);
    }

    private ItineraryGenerationEntity collectingGeneration() {
        ItineraryGenerationEntity generation = generation();
        generation.markCollecting(NOW);
        return generation;
    }

    private ItineraryGenerationEntity readyGeneration() {
        ItineraryGenerationEntity generation = collectingGeneration();
        generation.markReady(NOW);
        return generation;
    }

    private ItineraryGenerationEntity completedGeneration() {
        ItineraryGenerationEntity generation = readyGeneration();
        generation.markCompleted(NOW);
        return generation;
    }
}
