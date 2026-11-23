package com.carebridge.booking.dto;

import jakarta.validation.constraints.Size;

public record DeclineHireRequest(
        @Size(max = 1000, message = "Decline note must be 1000 characters or fewer.")
        String reason
) {
}
