package com.planmate.itinerary.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.messaging.ItineraryGenerationRecoveryPublisher;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StaleItineraryGenerationRecoverySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ItineraryGenerationRepository generationRepository;

    @Mock
    private ItineraryGenerationRecoveryPublisher recoveryPublisher;

    private ItineraryGenerationWorkerProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private StaleItineraryGenerationRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new ItineraryGenerationWorkerProperties();
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new StaleItineraryGenerationRecoveryScheduler(
                generationRepository,
                recoveryPublisher,
                properties,
                new ItineraryGenerationWorkerMetrics(meterRegistry),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void publishesEveryExpiredCollectionReturnedByScan() {
        ItineraryGenerationEntity first = generation(1L, 11L);
        ItineraryGenerationEntity second = generation(2L, 22L);
        given(generationRepository.findStaleCollections(
                ItineraryGenerationStatus.COLLECTING_CANDIDATES,
                NOW,
                PageRequest.of(0, 50)
        )).willReturn(List.of(first, second));

        scheduler.recoverStaleCollections();

        verify(recoveryPublisher).publish(1L, 11L);
        verify(recoveryPublisher).publish(2L, 22L);
        org.assertj.core.api.Assertions.assertThat(
                meterRegistry.get("planmate.itinerary.generation.worker.recovery.publish").counter().count()
        ).isEqualTo(2.0);
    }

    @Test
    void publishesNothingWhenNoCollectionLeaseIsExpired() {
        given(generationRepository.findStaleCollections(
                ItineraryGenerationStatus.COLLECTING_CANDIDATES,
                NOW,
                PageRequest.of(0, 50)
        )).willReturn(List.of());

        scheduler.recoverStaleCollections();

        verifyNoInteractions(recoveryPublisher);
    }

    @Test
    void publisherFailureDoesNotMutateGenerationAndDoesNotStopBatch() {
        ItineraryGenerationEntity first = generation(1L, 11L);
        ItineraryGenerationEntity second = generation(2L, 22L);
        given(generationRepository.findStaleCollections(
                ItineraryGenerationStatus.COLLECTING_CANDIDATES,
                NOW,
                PageRequest.of(0, 50)
        )).willReturn(List.of(first, second));
        org.mockito.Mockito.doThrow(new IllegalStateException("rabbit unavailable"))
                .when(recoveryPublisher).publish(1L, 11L);

        scheduler.recoverStaleCollections();

        verify(recoveryPublisher).publish(2L, 22L);
        verify(generationRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    private ItineraryGenerationEntity generation(Long generationId, Long tripId) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(tripId, "v2", NOW);
        ReflectionTestUtils.setField(generation, "id", generationId);
        return generation;
    }
}
