package com.planmate.itinerary.messaging;

import static org.mockito.Mockito.verify;

import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationRecoveryPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishesCompatibleWorkerMessageWithoutUserAuthorizationData() {
        ItineraryGenerationWorkerProperties properties = new ItineraryGenerationWorkerProperties();
        ItineraryGenerationRecoveryPublisher publisher =
                new ItineraryGenerationRecoveryPublisher(rabbitTemplate, properties);
        ArgumentCaptor<ItineraryGenerationRequestedMessage> messageCaptor =
                ArgumentCaptor.forClass(ItineraryGenerationRequestedMessage.class);

        publisher.publish(1L, 2L);

        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(properties.getExchange()),
                org.mockito.ArgumentMatchers.eq(properties.getRoutingKey()),
                messageCaptor.capture()
        );
        org.assertj.core.api.Assertions.assertThat(messageCaptor.getValue())
                .isEqualTo(new ItineraryGenerationRequestedMessage(1L, 2L, null));
    }
}
