package com.carebridge.caretaker.dto;

import com.carebridge.caretaker.RateType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateCaretakerProfileRequest(
        @Size(max = 2000, message = "Bio must be 2000 characters or fewer.")
        String bio,

        @NotBlank(message = "Location / area is required.")
        @Size(min = 2, max = 160, message = "Location / area must be between 2 and 160 characters.")
        String locationArea,

        @NotNull(message = "Service rate in BDT is required.")
        @DecimalMin(value = "50.00", message = "Rate must be at least 50 BDT.")
        @DecimalMax(value = "100000.00", message = "Rate must not exceed 100,000 BDT.")
        BigDecimal rateAmount,

        @NotNull(message = "Rate type (HOURLY or DAILY) is required.")
        RateType rateType,

        boolean available
) {
}
