package com.carebridge.feed.dto;

import com.carebridge.booking.BookingScheduleValidator;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.RateType;
import com.carebridge.user.User;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public Hunters Feed card DTO. Strictly omits mobileNumber and email to protect caretaker privacy
 * until a relative explicitly unlocks contact details.
 */
public record FeedCaretakerCardResponse(
        Long profileId,
        Long caretakerUserId,
        String fullName,
        String profileImageUrl,
        String primaryQualification,
        String locationArea,
        BigDecimal rateAmount,
        RateType rateType,
        boolean available,
        int todayBookingsCount,
        boolean dailyCapReachedToday,
        BigDecimal avgRating,
        int completedHiresCount,
        BigDecimal hireAgainPercentage,
        int verifiedCertificationsCount,
        List<String> verifiedCertificationTitles
) {
    public static FeedCaretakerCardResponse from(
            CaretakerProfile profile,
            List<String> verifiedCertificationTitles,
            int todayBookingsCount
    ) {
        User user = profile.getUser();
        int safeCount = Math.max(0, todayBookingsCount);
        return new FeedCaretakerCardResponse(
                profile.getId(),
                user.getId(),
                user.getFullName(),
                profile.getProfileImageUrl(),
                profile.getPrimaryQualification(),
                profile.getLocationArea(),
                profile.getRateAmount(),
                profile.getRateType(),
                profile.isAvailable(),
                safeCount,
                safeCount >= BookingScheduleValidator.MAX_BOOKINGS_PER_DAY,
                profile.getAvgRating(),
                profile.getCompletedHiresCount(),
                profile.getHireAgainPercentage(),
                verifiedCertificationTitles.size(),
                verifiedCertificationTitles
        );
    }
}
