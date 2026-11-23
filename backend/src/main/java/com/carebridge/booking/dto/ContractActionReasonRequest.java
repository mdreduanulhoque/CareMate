package com.carebridge.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContractActionReasonRequest(
        @NotBlank(message = "Please enter a brief reason.")
        @Size(min = 3, max = 1000, message = "Reason must be between 3 and 1000 characters.")
        String reason
) {
}
