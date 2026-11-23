package com.carebridge.booking.dto;

import com.carebridge.booking.ContractStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Privacy-safe caretaker daily schedule summary (zero customer PII).
 */
public record CaretakerDayScheduleResponse(
        Long caretakerUserId,
        LocalDate dhakaDate,
        int bookingsCount,
        int maxBookingsPerDay,
        boolean dailyCapReached,
        List<BusySlot> busySlots
) {
    public record BusySlot(
            Instant scheduledStartTime,
            Instant scheduledEndTime,
            ContractStatus status
    ) {
    }
}
