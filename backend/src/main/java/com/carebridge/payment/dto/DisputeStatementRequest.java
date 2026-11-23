package com.carebridge.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputeStatementRequest(
        @NotBlank(message = "Please enter your payment statement or transaction details.")
        @Size(min = 5, max = 2000, message = "Statement must be between 5 and 2000 characters.")
        String statement
) {
}
