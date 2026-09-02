package com.planmate.chat.dto;

import java.util.List;

public record ChatSearchResponse(
        String query,
        List<ChatSearchResultResponse> results,
        String nextCursor,
        boolean hasMore,
        Long searchSnapshotSequence
) {
}
