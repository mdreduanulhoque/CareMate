package com.carebridge.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendChatMessageRequest(
        @NotBlank(message = "Message cannot be empty.")
        @Size(max = 2000, message = "Message must be 2000 characters or fewer.")
        String content
) {
}
