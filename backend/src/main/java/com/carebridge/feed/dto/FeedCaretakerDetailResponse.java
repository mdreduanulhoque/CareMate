package com.carebridge.feed.dto;

import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.RateType;
import com.carebridge.caretaker.dto.PublicCertificationBadge;
import com.carebridge.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Public caretaker detail DTO shown before contact unlock.
 * Phone number is strictly omitted until Phase 4 click-to-reveal contact unlock.
 */
public record FeedCaretakerDetailResponse(
        Long profileId,
        Long caretakerUserId,
        String fullName,
        String profileImageUrl,
        String primaryQualification,
        String locationArea,
        String bio,
        BigDecimal rateAmount,
        RateType rateType,
        boolean available,
        BigDecimal avgRating,
        int completedHiresCount,
        BigDecimal hireAgainPercentage,
        List<PublicCertificationBadge> verifiedCertifications,
        Instant memberSince,
        boolean contactUnlocked
) {
    public static FeedCaretakerDetailResponse from(
            CaretakerProfile profile,
            List<PublicCertificationBadge> verifiedCertifications,
            boolean contactUnlocked
    ) {
        User user = profile.getUser();
        return new FeedCaretakerDetailResponse(
                profile.getId(),
                user.getId(),
                user.getFullName(),
                profile.getProfileImageUrl(),
                profile.getPrimaryQualification(),
                profile.getLocationArea(),
                profile.getBio(),
                profile.getRateAmount(),
                profile.getRateType(),
                profile.isAvailable(),
                profile.getAvgRating(),
                profile.getCompletedHiresCount(),
                profile.getHireAgainPercentage(),
                verifiedCertifications,
                profile.getCreatedAt(),
                contactUnlocked
        );
    }
}
