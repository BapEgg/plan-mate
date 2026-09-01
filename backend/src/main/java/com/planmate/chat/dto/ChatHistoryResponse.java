package com.planmate.chat.dto;

import java.util.List;

public record ChatHistoryResponse(List<ChatMessageResponse> messages, String nextCursor) {
}
