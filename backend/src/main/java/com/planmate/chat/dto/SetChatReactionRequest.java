package com.planmate.chat.dto;

import com.planmate.chat.entity.ChatReactionType;

public record SetChatReactionRequest(ChatReactionType reaction) {
}
