package com.carebridge.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveDisputeRequest(
        @NotBlank(message = "A clear Admin resolution note is mandatory to close a dispute.")
        @Size(min = 5, max = 2000, message = "Resolution note must be between 5 and 2000 characters.")
        String adminResolutionNote
) {
}
