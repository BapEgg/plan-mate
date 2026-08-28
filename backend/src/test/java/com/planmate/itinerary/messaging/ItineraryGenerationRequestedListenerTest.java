package com.planmate.itinerary.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.service.ItineraryGenerationWorkerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationRequestedListenerTest {

    @Mock
    private ItineraryGenerationWorkerService workerService;

    @Test
    void passesRabbitRedeliveryMetadataToWorker() {
        ItineraryGenerationRequestedMessage payload =
                new ItineraryGenerationRequestedMessage(1L, 2L, 3L);
        MessageProperties properties = new MessageProperties();
        properties.setRedelivered(true);
        Message rabbitMessage = new Message(new byte[0], properties);

        new ItineraryGenerationRequestedListener(workerService).handle(payload, rabbitMessage);

        verify(workerService).process(payload, true);
    }

    @Test
    void propagatesFinalWorkerFailureForContainerRejectAndDeadLetterHandling() {
        ItineraryGenerationRequestedMessage payload =
                new ItineraryGenerationRequestedMessage(1L, 2L, 3L);
        MessageProperties properties = new MessageProperties();
        properties.setRedelivered(false);
        Message rabbitMessage = new Message(new byte[0], properties);
        RuntimeException failure = new IllegalStateException("final worker failure");
        doThrow(failure).when(workerService).process(payload, false);

        assertThatThrownBy(() -> new ItineraryGenerationRequestedListener(workerService)
                .handle(payload, rabbitMessage))
                .isSameAs(failure);
    }
}
