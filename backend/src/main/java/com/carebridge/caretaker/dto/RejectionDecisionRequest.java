package com.carebridge.caretaker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectionDecisionRequest(
        @NotBlank(message = "A clear rejection reason is mandatory.")
        @Size(min = 5, max = 1000, message = "Rejection reason must be between 5 and 1000 characters.")
        String rejectionReason
) {
}
