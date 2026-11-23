package com.carebridge.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefusePaymentRequest(
        @NotBlank(message = "Please explain why payment was not received or disputed.")
        @Size(min = 5, max = 2000, message = "Statement must be between 5 and 2000 characters.")
        String caretakerStatement
) {
}
