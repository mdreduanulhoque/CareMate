package com.carebridge.feedback;

import com.carebridge.booking.HireContract;
import com.carebridge.booking.HireContractRepository;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.common.ForbiddenException;
import com.carebridge.common.NotFoundException;
import com.carebridge.common.TransactionCommitHooks;
import com.carebridge.feedback.dto.PublicReviewsSummaryResponse;
import com.carebridge.feedback.dto.ReviewResponse;
import com.carebridge.feedback.dto.SubmitOrEditReviewRequest;
import com.carebridge.notification.RealtimeEventDispatcher;
import com.carebridge.notification.dto.UserNotificationEvent;
import com.carebridge.security.CareBridgeUserPrincipal;
import com.carebridge.user.Role;
import com.carebridge.user.User;
import com.carebridge.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class FeedbackService {

    private final ReviewRepository reviewRepository;
    private final HireContractRepository hireContractRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final UserRepository userRepository;
    private final FeedbackAggregateService feedbackAggregateService;
    private final RealtimeEventDispatcher realtimeEventDispatcher;

    public FeedbackService(
            ReviewRepository reviewRepository,
            HireContractRepository hireContractRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            UserRepository userRepository,
            FeedbackAggregateService feedbackAggregateService,
            RealtimeEventDispatcher realtimeEventDispatcher
    ) {
        this.reviewRepository = reviewRepository;
        this.hireContractRepository = hireContractRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.userRepository = userRepository;
        this.feedbackAggregateService = feedbackAggregateService;
        this.realtimeEventDispatcher = realtimeEventDispatcher;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listPendingReviews(CareBridgeUserPrincipal requester) {
        requireVerifiedParticipantRole(requester);
        Instant now = Instant.now();
        return reviewRepository.findPendingByReviewerId(requester.getUserId()).stream()
                .map(review -> ReviewResponse.from(review, requester.getUserId(), now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listAuthoredReviews(CareBridgeUserPrincipal requester) {
        requireVerifiedParticipantRole(requester);
        Instant now = Instant.now();
        return reviewRepository.findAllByReviewerId(requester.getUserId()).stream()
                .map(review -> ReviewResponse.from(review, requester.getUserId(), now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getContractReviews(CareBridgeUserPrincipal requester, Long contractId) {
        HireContract contract = hireContractRepository.findByIdWithUsers(contractId)
                .orElseThrow(() -> new NotFoundException("Hire contract not found."));

        boolean isAdmin = requester.getRole() == Role.ADMIN;
        boolean isCustomer = requester.getRole() == Role.CUSTOMER
                && contract.getCustomer().getId().equals(requester.getUserId());
        boolean isApprovedCaretaker = requester.getRole() == Role.CARETAKER
                && contract.getCaretaker().getId().equals(requester.getUserId())
                && caretakerProfileRepository.existsByUserId(requester.getUserId());

        if (!isAdmin && !isCustomer && !isApprovedCaretaker) {
            throw new ForbiddenException("You do not have permission to view feedback for this contract.");
        }

        Instant now = Instant.now();
        return reviewRepository.findAllByContractId(contractId).stream()
                .filter(review -> review.isSubmitted() || review.getReviewer().getId().equals(requester.getUserId()))
                .map(review -> ReviewResponse.from(review, requester.getUserId(), now))
                .toList();
    }

    @Transactional
    public ReviewResponse submitReview(
            CareBridgeUserPrincipal requester,
            Long reviewId,
            SubmitOrEditReviewRequest request
    ) {
        requireVerifiedParticipantRole(requester);
        Review review = reviewRepository.findByIdWithUsersAndContract(reviewId)
                .orElseThrow(() -> new NotFoundException("Feedback form not found."));

        if (!review.getReviewer().getId().equals(requester.getUserId())) {
            throw new ForbiddenException("You can only submit your own feedback form.");
        }

        Instant now = Instant.now();
        review.submitInitial(
                request.rating(),
                normalizeOptionalComment(request.comment()),
                request.wouldEngageAgain(),
                now
        );
        Review saved = reviewRepository.saveAndFlush(review);

        if (saved.getReviewerRole() == Role.CUSTOMER) {
            feedbackAggregateService.recalculateCaretakerMetrics(saved.getReviewee().getId());
        }

        Long revieweeId = saved.getReviewee().getId();
        User reviewer = saved.getReviewer();
        UserNotificationEvent notification = UserNotificationEvent.forContract(
                "REVIEW_RECEIVED",
                saved.getContract().getId(),
                "New " + saved.getRating() + "-Star Review Received",
                reviewer.getFullName() + " left feedback for contract #" + saved.getContract().getId() + ".",
                reviewer.getId(),
                reviewer.getFullName()
        );
        TransactionCommitHooks.runAfterCommit(
                () -> realtimeEventDispatcher.publishUserNotification(revieweeId, notification)
        );

        return ReviewResponse.from(saved, requester.getUserId(), now);
    }

    @Transactional
    public ReviewResponse editReview(
            CareBridgeUserPrincipal requester,
            Long reviewId,
            SubmitOrEditReviewRequest request
    ) {
        return editReviewAt(requester, reviewId, request, Instant.now());
    }

    @Transactional
    public ReviewResponse editReviewAt(
            CareBridgeUserPrincipal requester,
            Long reviewId,
            SubmitOrEditReviewRequest request,
            Instant now
    ) {
        requireVerifiedParticipantRole(requester);
        Review review = reviewRepository.findByIdWithUsersAndContract(reviewId)
                .orElseThrow(() -> new NotFoundException("Review not found."));

        if (!review.getReviewer().getId().equals(requester.getUserId())) {
            throw new ForbiddenException("You can only edit reviews you authored.");
        }

        review.editSubmitted(
                request.rating(),
                normalizeOptionalComment(request.comment()),
                request.wouldEngageAgain(),
                now
        );
        Review saved = reviewRepository.saveAndFlush(review);

        if (saved.getReviewerRole() == Role.CUSTOMER) {
            feedbackAggregateService.recalculateCaretakerMetrics(saved.getReviewee().getId());
        }

        return ReviewResponse.from(saved, requester.getUserId(), now);
    }

    @Transactional(readOnly = true)
    public PublicReviewsSummaryResponse getCaretakerPublicReviews(
            CareBridgeUserPrincipal requester,
            Long caretakerUserId
    ) {
        if (requester.getRole() == Role.CARETAKER
                && !caretakerProfileRepository.existsByUserId(requester.getUserId())) {
            throw new ForbiddenException("Unverified caretakers cannot view Hunters Feed reviews until approval.");
        }

        User caretaker = userRepository.findById(caretakerUserId)
                .filter(u -> u.getRole() == Role.CARETAKER && caretakerProfileRepository.existsByUserId(u.getId()))
                .orElseThrow(() -> new NotFoundException("Verified caretaker profile not found."));

        Instant now = Instant.now();
        List<ReviewResponse> reviews = reviewRepository
                .findSubmittedForRevieweeByReviewerRole(caretaker.getId(), Role.CUSTOMER)
                .stream()
                .map(r -> ReviewResponse.from(r, requester.getUserId(), now))
                .toList();

        Map<Long, FeedbackAggregateService.ReviewSummaryStats> statsMap =
                feedbackAggregateService.summarizeCaretakerReviewMetrics(List.of(caretaker.getId()));
        FeedbackAggregateService.ReviewSummaryStats stats = statsMap.getOrDefault(
                caretaker.getId(),
                FeedbackAggregateService.ReviewSummaryStats.EMPTY
        );

        return new PublicReviewsSummaryResponse(
                caretaker.getId(),
                caretaker.getFullName(),
                stats.totalReviews(),
                stats.avgRating(),
                stats.wouldEngageAgainPercentage(),
                reviews
        );
    }

    @Transactional(readOnly = true)
    public PublicReviewsSummaryResponse getCustomerReviewsForCaretaker(
            CareBridgeUserPrincipal requester,
            Long customerUserId
    ) {
        if (requester.getRole() == Role.CARETAKER
                && !caretakerProfileRepository.existsByUserId(requester.getUserId())) {
            throw new ForbiddenException("Your caretaker account must be approved before viewing customer reviews.");
        }
        if (requester.getRole() != Role.CARETAKER && requester.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Only approved caretakers and admins can view customer review histories.");
        }

        User customer = userRepository.findById(customerUserId)
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> new NotFoundException("Customer profile not found."));

        Instant now = Instant.now();
        List<ReviewResponse> reviews = reviewRepository
                .findSubmittedForRevieweeByReviewerRole(customer.getId(), Role.CARETAKER)
                .stream()
                .map(r -> ReviewResponse.from(r, requester.getUserId(), now))
                .toList();

        Map<Long, FeedbackAggregateService.ReviewSummaryStats> statsMap =
                feedbackAggregateService.summarizeCustomerReviewMetrics(List.of(customer.getId()));
        FeedbackAggregateService.ReviewSummaryStats stats = statsMap.getOrDefault(
                customer.getId(),
                FeedbackAggregateService.ReviewSummaryStats.EMPTY
        );

        return new PublicReviewsSummaryResponse(
                customer.getId(),
                customer.getFullName(),
                stats.totalReviews(),
                stats.avgRating(),
                stats.wouldEngageAgainPercentage(),
                reviews
        );
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listAllSubmittedReviewsForAdmin() {
        Instant now = Instant.now();
        return reviewRepository.findAllSubmittedWithUsers().stream()
                .map(review -> ReviewResponse.from(review, null, now))
                .toList();
    }

    private void requireVerifiedParticipantRole(CareBridgeUserPrincipal requester) {
        if (requester.getRole() == Role.CUSTOMER) {
            return;
        }
        if (requester.getRole() == Role.CARETAKER
                && caretakerProfileRepository.existsByUserId(requester.getUserId())) {
            return;
        }
        throw new ForbiddenException("Only customers and approved caretakers can submit or edit feedback.");
    }

    private String normalizeOptionalComment(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
