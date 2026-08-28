package com.planmate.common.outbox;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxRetentionCleanupService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxRetentionCleanupService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public int deleteBatchBefore(Instant cutoff, int batchSize) {
        return outboxEventRepository.deleteBatchBefore(cutoff, batchSize);
    }
}
