package com.planmate.itinerary.messaging;

import com.planmate.itinerary.service.ItineraryGenerationWorkerService;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.itinerary.generation-worker.enabled", havingValue = "true")
public class ItineraryGenerationRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationRequestedListener.class);

    private final ItineraryGenerationWorkerService workerService;
    private final ItineraryGenerationWorkerMetrics metrics;
    private final ItineraryGenerationWorkerReliabilityHook reliabilityHook;

    public ItineraryGenerationRequestedListener(
            ItineraryGenerationWorkerService workerService,
            ItineraryGenerationWorkerMetrics metrics,
            ItineraryGenerationWorkerReliabilityHook reliabilityHook
    ) {
        this.workerService = workerService;
        this.metrics = metrics;
        this.reliabilityHook = reliabilityHook;
    }

    @RabbitListener(queues = "${app.itinerary.generation-worker.queue}")
    public void handle(ItineraryGenerationRequestedMessage message, Message rabbitMessage) {
        boolean redelivered = rabbitMessage.getMessageProperties().isRedelivered();
        log.info(
                "Itinerary generation delivery received: generationId={}, tripId={}, redelivered={}",
                message.generationId(),
                message.tripId(),
                redelivered
        );
        metrics.recordDelivery(redelivered);
        reliabilityHook.pauseAfterDeliveryBeforeClaim(message, redelivered);
        workerService.process(message, redelivered);
        reliabilityHook.pauseAfterCommitBeforeAck(message, redelivered);
        log.info(
                "Itinerary generation listener returning for ACK: generationId={}, tripId={}, redelivered={}",
                message.generationId(),
                message.tripId(),
                redelivered
        );
    }
}
