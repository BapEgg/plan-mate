package com.planmate.itinerary.messaging;

import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ItineraryGenerationRecoveryPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ItineraryGenerationWorkerProperties properties;

    public ItineraryGenerationRecoveryPublisher(
            RabbitTemplate rabbitTemplate,
            ItineraryGenerationWorkerProperties properties
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(Long generationId, Long tripId) {
        rabbitTemplate.convertAndSend(
                properties.getExchange(),
                properties.getRoutingKey(),
                new ItineraryGenerationRequestedMessage(generationId, tripId, null)
        );
    }
}
