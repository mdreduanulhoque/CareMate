package com.carebridge.feedback;

import com.carebridge.feedback.dto.PublicReviewsSummaryResponse;
import com.carebridge.feedback.dto.ReviewResponse;
import com.carebridge.feedback.dto.SubmitOrEditReviewRequest;
import com.carebridge.security.CareBridgeUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER')")
    public ResponseEntity<List<ReviewResponse>> listPendingReviews(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(feedbackService.listPendingReviews(principal));
    }

    @GetMapping("/authored")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER')")
    public ResponseEntity<List<ReviewResponse>> listAuthoredReviews(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal
    ) {
        return ResponseEntity.ok(feedbackService.listAuthoredReviews(principal));
    }

    @GetMapping("/contract/{contractId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
    public ResponseEntity<List<ReviewResponse>> getContractReviews(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long contractId
    ) {
        return ResponseEntity.ok(feedbackService.getContractReviews(principal, contractId));
    }

    @PostMapping("/{reviewId}/submit")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER')")
    public ResponseEntity<ReviewResponse> submitReview(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody SubmitOrEditReviewRequest request
    ) {
        return ResponseEntity.ok(feedbackService.submitReview(principal, reviewId, request));
    }

    @PutMapping("/{reviewId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER')")
    public ResponseEntity<ReviewResponse> editReview(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long reviewId,
            @Valid @RequestBody SubmitOrEditReviewRequest request
    ) {
        return ResponseEntity.ok(feedbackService.editReview(principal, reviewId, request));
    }

    @GetMapping("/caretaker/{caretakerUserId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'CARETAKER', 'ADMIN')")
    public ResponseEntity<PublicReviewsSummaryResponse> getCaretakerPublicReviews(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long caretakerUserId
    ) {
        return ResponseEntity.ok(feedbackService.getCaretakerPublicReviews(principal, caretakerUserId));
    }

    @GetMapping("/customer/{customerUserId}")
    @PreAuthorize("hasAnyRole('CARETAKER', 'ADMIN')")
    public ResponseEntity<PublicReviewsSummaryResponse> getCustomerReviewsForCaretaker(
            @AuthenticationPrincipal CareBridgeUserPrincipal principal,
            @PathVariable Long customerUserId
    ) {
        return ResponseEntity.ok(feedbackService.getCustomerReviewsForCaretaker(principal, customerUserId));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReviewResponse>> listAllSubmittedReviewsForAdmin() {
        return ResponseEntity.ok(feedbackService.listAllSubmittedReviewsForAdmin());
    }
}
