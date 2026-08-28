package com.planmate.common.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class OutboxRetentionCleanupIntegrationTest {

    private static final Instant CUTOFF = Instant.parse("2026-01-08T00:00:00Z");

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private OutboxRetentionCleanupService cleanupService;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    @Test
    void deletesOldestRowsInBoundedBatchesAndKeepsBoundaryAndFreshRows() {
        OutboxEventEntity oldest = event("oldest", CUTOFF.minusSeconds(2));
        OutboxEventEntity old = event("old", CUTOFF.minusSeconds(1));
        OutboxEventEntity boundary = event("boundary", CUTOFF);
        OutboxEventEntity fresh = event("fresh", CUTOFF.plusSeconds(1));
        repository.saveAll(List.of(old, fresh, oldest, boundary));
        entityManager.flush();

        assertThat(cleanupService.deleteBatchBefore(CUTOFF, 1)).isEqualTo(1);
        entityManager.clear();
        assertThat(repository.findById(oldest.getId())).isEmpty();
        assertThat(repository.findById(old.getId())).isPresent();
        assertThat(repository.findById(boundary.getId())).isPresent();
        assertThat(repository.findById(fresh.getId())).isPresent();

        assertThat(cleanupService.deleteBatchBefore(CUTOFF, 1)).isEqualTo(1);
        entityManager.clear();
        assertThat(repository.findById(old.getId())).isEmpty();
        assertThat(repository.findById(boundary.getId())).isPresent();
        assertThat(repository.findById(fresh.getId())).isPresent();

        assertThat(cleanupService.deleteBatchBefore(CUTOFF, 1)).isZero();
    }

    private OutboxEventEntity event(String aggregateId, Instant createdAt) {
        return OutboxEventEntity.create(
                "ItineraryGeneration",
                aggregateId,
                "ItineraryGenerationRequested",
                Map.of("generationId", aggregateId),
                createdAt
        );
    }
}
