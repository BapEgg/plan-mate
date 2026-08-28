package com.planmate.itinerary.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.itinerary.generation-worker")
public class ItineraryGenerationWorkerProperties {

    private boolean enabled;
    private int maxAttempts = 3;
    private String exchange = "planmate.itinerary";
    private String queue = "planmate.itinerary.generation.requested";
    private String routingKey = "itinerary.generation.requested";
    private String deadLetterExchange = "planmate.itinerary.dlx";
    private String deadLetterQueue = "planmate.itinerary.generation.requested.dlq";
    private String deadLetterRoutingKey = "itinerary.generation.requested.dlq";
    private Duration processingLease = Duration.ofMinutes(15);
    private boolean staleRecoveryEnabled = true;
    private Duration recoveryScanInterval = Duration.ofMinutes(1);
    private int recoveryBatchSize = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public String getDeadLetterRoutingKey() {
        return deadLetterRoutingKey;
    }

    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
        this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    public Duration getProcessingLease() {
        return processingLease;
    }

    public void setProcessingLease(Duration processingLease) {
        if (processingLease == null || processingLease.isZero() || processingLease.isNegative()) {
            throw new IllegalArgumentException("processingLease must be positive");
        }
        this.processingLease = processingLease;
    }

    public boolean isStaleRecoveryEnabled() {
        return staleRecoveryEnabled;
    }

    public void setStaleRecoveryEnabled(boolean staleRecoveryEnabled) {
        this.staleRecoveryEnabled = staleRecoveryEnabled;
    }

    public Duration getRecoveryScanInterval() {
        return recoveryScanInterval;
    }

    public void setRecoveryScanInterval(Duration recoveryScanInterval) {
        if (recoveryScanInterval == null || recoveryScanInterval.isZero() || recoveryScanInterval.isNegative()) {
            throw new IllegalArgumentException("recoveryScanInterval must be positive");
        }
        this.recoveryScanInterval = recoveryScanInterval;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void setRecoveryBatchSize(int recoveryBatchSize) {
        this.recoveryBatchSize = Math.max(1, recoveryBatchSize);
    }
}
