package com.carebridge.payment.dto;

import jakarta.validation.constraints.Size;

public record CustomerPaymentDoneRequest(
        @Size(max = 500, message = "Payment reference note must be 500 characters or fewer.")
        String paymentReferenceNote
) {
}
