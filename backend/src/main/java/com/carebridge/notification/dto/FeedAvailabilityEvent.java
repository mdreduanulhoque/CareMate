package com.carebridge.notification.dto;

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
        Instant updatedAt
) {
    public static FeedAvailabilityEvent from(CaretakerProfile profile) {
        return new FeedAvailabilityEvent(
                profile.getUser().getId(),
                profile.getId(),
                profile.isAvailable(),
                profile.getRateAmount(),
                profile.getRateType(),
                profile.getLocationArea(),
                profile.getUpdatedAt() != null ? profile.getUpdatedAt() : Instant.now()
        );
    }
}
