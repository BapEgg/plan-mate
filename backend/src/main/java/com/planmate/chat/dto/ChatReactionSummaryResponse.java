package com.planmate.chat.dto;

import com.planmate.chat.entity.ChatReactionType;
import java.util.List;

public record ChatReactionSummaryResponse(
        ChatReactionType reaction,
        int count,
        List<String> memberNames,
        boolean reactedByMe
) {
}
