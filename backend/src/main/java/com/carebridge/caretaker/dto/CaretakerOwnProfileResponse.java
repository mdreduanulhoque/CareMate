package com.carebridge.caretaker.dto;

import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.RateType;
import com.carebridge.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CaretakerOwnProfileResponse(
        Long profileId,
        Long userId,
        String fullName,
        String mobileNumber,
        String email,
        String primaryQualification,
        String locationArea,
        String profileImageUrl,
        String bio,
        BigDecimal rateAmount,
        RateType rateType,
        boolean available,
        BigDecimal avgRating,
        int completedHiresCount,
        BigDecimal hireAgainPercentage,
        List<PublicCertificationBadge> verifiedCertifications,
        Instant updatedAt
) {
    public static CaretakerOwnProfileResponse from(
            CaretakerProfile profile,
            List<PublicCertificationBadge> verifiedCertifications
    ) {
        User user = profile.getUser();
        return new CaretakerOwnProfileResponse(
                profile.getId(),
                user.getId(),
                user.getFullName(),
                user.getMobileNumber(),
                user.getEmail(),
                profile.getPrimaryQualification(),
                profile.getLocationArea(),
                profile.getProfileImageUrl(),
                profile.getBio(),
                profile.getRateAmount(),
                profile.getRateType(),
                profile.isAvailable(),
                profile.getAvgRating(),
                profile.getCompletedHiresCount(),
                profile.getHireAgainPercentage(),
                verifiedCertifications,
                profile.getUpdatedAt()
        );
    }
}
