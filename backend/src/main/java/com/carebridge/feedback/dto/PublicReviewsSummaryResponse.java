package com.carebridge.feedback.dto;

import java.math.BigDecimal;
import java.util.List;

public record PublicReviewsSummaryResponse(
        Long subjectUserId,
        String subjectName,
        int totalReviews,
        BigDecimal avgRating,
        BigDecimal wouldEngageAgainPercentage,
        List<ReviewResponse> reviews
) {
}
