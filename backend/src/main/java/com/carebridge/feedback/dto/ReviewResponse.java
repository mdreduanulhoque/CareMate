package com.carebridge.feedback.dto;

import com.carebridge.booking.ContractStatus;
import com.carebridge.booking.HireContract;
import com.carebridge.feedback.Review;
import com.carebridge.user.Role;
import com.carebridge.user.User;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        Long contractId,
        Long reviewerId,
        String reviewerName,
        Role reviewerRole,
        Long revieweeId,
        String revieweeName,
        Integer rating,
        String comment,
        Boolean wouldEngageAgain,
        boolean submitted,
        boolean edited,
        boolean locked,
        boolean canEdit,
        Instant submittedAt,
        Instant updatedAt,
        Instant editableUntil,
        Instant createdAt,
        Instant contractStartTime,
        Instant contractEndTime,
        Instant contractActualEndTime,
        ContractStatus contractStatus
) {
    public static ReviewResponse from(Review review, Long viewerUserId, Instant now) {
        HireContract contract = review.getContract();
        User reviewer = review.getReviewer();
        User reviewee = review.getReviewee();
        boolean isOwner = viewerUserId != null && viewerUserId.equals(reviewer.getId());
        boolean canEdit = isOwner && review.isCurrentlyEditable(now);
        boolean effectivelyLocked = review.isEffectivelyLocked(now);

        return new ReviewResponse(
                review.getId(),
                contract.getId(),
                reviewer.getId(),
                reviewer.getFullName(),
                review.getReviewerRole(),
                reviewee.getId(),
                reviewee.getFullName(),
                review.getRating(),
                review.getComment(),
                review.getWouldEngageAgain(),
                review.isSubmitted(),
                review.isEdited(),
                effectivelyLocked,
                canEdit,
                review.getSubmittedAt(),
                review.getUpdatedAt(),
                review.getEditableUntil(),
                review.getCreatedAt(),
                contract.getScheduledStartTime(),
                contract.getScheduledEndTime(),
                contract.getActualEndTime(),
                contract.getStatus()
        );
    }
}
