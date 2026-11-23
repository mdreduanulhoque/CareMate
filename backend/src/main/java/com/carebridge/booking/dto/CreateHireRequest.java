package com.carebridge.booking.dto;

import com.carebridge.booking.ContractRateType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateHireRequest(
        @NotNull(message = "Please choose a verified caretaker.")
        Long caretakerUserId,

        @NotNull(message = "Please enter the agreed rate in BDT.")
        @DecimalMin(value = "50.00", message = "Agreed rate must be at least 50 BDT.")
        @DecimalMax(value = "1000000.00", message = "Agreed rate exceeds maximum allowed limit.")
        @Digits(integer = 8, fraction = 2, message = "Agreed rate must have at most 2 decimal places.")
        BigDecimal agreedRate,

        @NotNull(message = "Please select a rate unit (hourly, daily, or fixed).")
        ContractRateType rateType,

        @NotNull(message = "Please choose the service start date and time.")
        Instant scheduledStartTime,

        @NotNull(message = "Please choose the service end date and time.")
        Instant scheduledEndTime,

        @Size(max = 1000, message = "Notes must be 1000 characters or fewer.")
        String notes
) {
}
