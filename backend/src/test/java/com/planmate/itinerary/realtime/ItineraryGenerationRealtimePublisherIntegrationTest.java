package com.planmate.itinerary.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.planmate.common.realtime.RealtimeEventEnvelope;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(classes = {
        ItineraryGenerationRealtimePublisher.class,
        ItineraryGenerationRealtimeEventMapper.class,
        ItineraryGenerationRealtimePublisherIntegrationTest.TestConfig.class
})
class ItineraryGenerationRealtimePublisherIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @jakarta.annotation.Resource
    private ApplicationEventPublisher eventPublisher;

    @jakarta.annotation.Resource
    private TransactionTemplate transactionTemplate;

    @jakarta.annotation.Resource
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(messagingTemplate);
    }

    @Test
    void publishesAfterTransactionCommit() {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event()));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/trips/45/events"),
                org.mockito.ArgumentMatchers.<RealtimeEventEnvelope<ItineraryGenerationStatusChangedPayload>>any()
        );
    }

    @Test
    void doesNotPublishAfterTransactionRollback() {
        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event());
            status.setRollbackOnly();
        });

        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/trips/45/events"),
                org.mockito.ArgumentMatchers.<RealtimeEventEnvelope<ItineraryGenerationStatusChangedPayload>>any()
        );
    }

    private ItineraryGenerationStatusChangedEvent event() {
        return new ItineraryGenerationStatusChangedEvent(
                45L,
                123L,
                ItineraryGenerationStatus.CREATED,
                ItineraryGenerationStatus.COLLECTING_CANDIDATES,
                0,
                null,
                NOW
        );
    }

    @Configuration
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        SimpMessagingTemplate messagingTemplate() {
            return org.mockito.Mockito.mock(SimpMessagingTemplate.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }

        @Bean
        TransactionalEventListenerFactory transactionalEventListenerFactory() {
            return new TransactionalEventListenerFactory();
        }
    }

    private static class TestTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            if (status.isRollbackOnly()) {
                rollback(status);
                return;
            }
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.beforeCommit(false);
                synchronization.beforeCompletion();
            }
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
                synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
            clear();
        }

        @Override
        public void rollback(TransactionStatus status) {
            for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.beforeCompletion();
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            clear();
        }

        private void clear() {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
