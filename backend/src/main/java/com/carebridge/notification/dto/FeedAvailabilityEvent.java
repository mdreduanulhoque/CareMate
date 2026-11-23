package com.carebridge.notification.dto;

import com.carebridge.booking.BookingScheduleValidator;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.RateType;

import java.math.BigDecimal;
import java.time.Instant;

public record FeedAvailabilityEvent(
        Long caretakerUserId,
        Long profileId,
        boolean available,
        BigDecimal rateAmount,
        RateType rateType,
        String locationArea,
        int todayBookingsCount,
        boolean dailyCapReachedToday,
        Instant updatedAt
) {
    public static FeedAvailabilityEvent from(CaretakerProfile profile, int todayBookingsCount) {
        int safeCount = Math.max(0, todayBookingsCount);
        return new FeedAvailabilityEvent(
                profile.getUser().getId(),
                profile.getId(),
                profile.isAvailable(),
                profile.getRateAmount(),
                profile.getRateType(),
                profile.getLocationArea(),
                safeCount,
                safeCount >= BookingScheduleValidator.MAX_BOOKINGS_PER_DAY,
                profile.getUpdatedAt() != null ? profile.getUpdatedAt() : Instant.now()
        );
    }
}
