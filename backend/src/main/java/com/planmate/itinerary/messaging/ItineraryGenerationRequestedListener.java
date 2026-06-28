package com.planmate.itinerary.messaging;

import com.planmate.itinerary.service.ItineraryGenerationWorkerService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.itinerary.generation-worker.enabled", havingValue = "true")
public class ItineraryGenerationRequestedListener {

    private final ItineraryGenerationWorkerService workerService;

    public ItineraryGenerationRequestedListener(ItineraryGenerationWorkerService workerService) {
        this.workerService = workerService;
    }

    @RabbitListener(queues = "${app.itinerary.generation-worker.queue}")
    public void handle(ItineraryGenerationRequestedMessage message) {
        workerService.process(message);
    }
}
