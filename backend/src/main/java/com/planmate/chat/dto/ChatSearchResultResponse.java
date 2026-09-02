package com.planmate.chat.dto;

import java.time.Instant;
import java.util.List;

public record ChatSearchResultResponse(
        Long messageId,
        Long sequence,
        String senderSnapshot,
        Instant createdAtUtc,
        String snippet,
        List<ChatSearchMatchRangeResponse> matchedRanges
) {
}
