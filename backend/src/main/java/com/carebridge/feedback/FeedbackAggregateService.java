package com.carebridge.feedback;

import com.carebridge.booking.ContractStatus;
import com.carebridge.booking.HireContract;
import com.carebridge.booking.HireContractRepository;
import com.carebridge.caretaker.CaretakerProfile;
import com.carebridge.caretaker.CaretakerProfileRepository;
import com.carebridge.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles idempotent dual pending review creation and caretaker/customer rating aggregate recalculation.
 */
@Service
public class FeedbackAggregateService {

    public static final Set<ContractStatus> COMPLETED_STATUSES = EnumSet.of(
            ContractStatus.COMPLETED_PENDING_SETTLEMENT,
            ContractStatus.CLOSED_PAID,
            ContractStatus.PAYMENT_DISPUTED,
            ContractStatus.DISPUTE_RESOLVED
    );

    private static final Logger log = LoggerFactory.getLogger(FeedbackAggregateService.class);

    private final ReviewRepository reviewRepository;
    private final HireContractRepository hireContractRepository;
    private final CaretakerProfileRepository caretakerProfileRepository;
    private final TransactionTemplate transactionTemplate;

    public FeedbackAggregateService(
            ReviewRepository reviewRepository,
            HireContractRepository hireContractRepository,
            CaretakerProfileRepository caretakerProfileRepository,
            TransactionTemplate transactionTemplate
    ) {
        this.reviewRepository = reviewRepository;
        this.hireContractRepository = hireContractRepository;
        this.caretakerProfileRepository = caretakerProfileRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Idempotently ensures both pending feedback forms (Relative -> Caretaker and Caretaker -> Relative)
     * exist for a completed contract. Must run inside an active transaction.
     */
    public boolean ensurePendingReviewsForCompletedContract(HireContract contract) {
        boolean createdAny = false;

        Long contractId = contract.getId();
        Long customerId = contract.getCustomer().getId();
        Long caretakerId = contract.getCaretaker().getId();

        if (!reviewRepository.existsByContractIdAndReviewerId(contractId, customerId)) {
            reviewRepository.save(new Review(
                    contract,
                    contract.getCustomer(),
                    contract.getCaretaker(),
                    Role.CUSTOMER
            ));
            createdAny = true;
        }

        if (!reviewRepository.existsByContractIdAndReviewerId(contractId, caretakerId)) {
            reviewRepository.save(new Review(
                    contract,
                    contract.getCaretaker(),
                    contract.getCustomer(),
                    Role.CARETAKER
            ));
            createdAny = true;
        }

        if (createdAny) {
            reviewRepository.flush();
        }
        return createdAny;
    }

    @Transactional
    public void recalculateCaretakerMetrics(Long caretakerUserId) {
        CaretakerProfile profile = caretakerProfileRepository.findByUserId(caretakerUserId).orElse(null);
        if (profile == null) {
            return;
        }

        int completedHires = (int) hireContractRepository.countByCaretakerIdAndStatusIn(
                caretakerUserId,
                COMPLETED_STATUSES
        );

        Map<Long, ReviewSummaryStats> statsMap = summarizeSubmittedReviews(
                List.of(caretakerUserId),
                Role.CUSTOMER
        );
        ReviewSummaryStats stats = statsMap.getOrDefault(caretakerUserId, ReviewSummaryStats.EMPTY);

        profile.updateReviewMetrics(
                stats.avgRating(),
                completedHires,
                stats.wouldEngageAgainPercentage()
        );
        caretakerProfileRepository.saveAndFlush(profile);
    }

    @Async("contractLifecycleExecutor")
    public void recalculateCaretakerMetricsAsync(Long caretakerUserId) {
        try {
            transactionTemplate.executeWithoutResult(status -> recalculateCaretakerMetrics(caretakerUserId));
        } catch (RuntimeException ex) {
            log.warn("Async caretaker metrics recalculation failed for caretaker {}: {}",
                    caretakerUserId, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Map<Long, ReviewSummaryStats> summarizeCustomerReviewMetrics(Collection<Long> customerIds) {
        return summarizeSubmittedReviews(customerIds, Role.CARETAKER);
    }

    @Transactional(readOnly = true)
    public Map<Long, ReviewSummaryStats> summarizeCaretakerReviewMetrics(Collection<Long> caretakerIds) {
        return summarizeSubmittedReviews(caretakerIds, Role.CUSTOMER);
    }

    private Map<Long, ReviewSummaryStats> summarizeSubmittedReviews(
            Collection<Long> revieweeIds,
            Role reviewerRole
    ) {
        if (revieweeIds == null || revieweeIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object[]> rows = reviewRepository.summarizeSubmittedByRevieweeIds(revieweeIds, reviewerRole);
        Map<Long, ReviewSummaryStats> result = new HashMap<>();
        for (Object[] row : rows) {
            Long revieweeId = (Long) row[0];
            int count = ((Number) row[1]).intValue();
            double avgRaw = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            long yesCount = row[3] != null ? ((Number) row[3]).longValue() : 0L;

            BigDecimal avgRating = count > 0
                    ? BigDecimal.valueOf(avgRaw).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal yesPercentage = count > 0
                    ? BigDecimal.valueOf(yesCount)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            result.put(revieweeId, new ReviewSummaryStats(count, avgRating, yesPercentage));
        }
        return result;
    }

    public record ReviewSummaryStats(
            int totalReviews,
            BigDecimal avgRating,
            BigDecimal wouldEngageAgainPercentage
    ) {
        public static final ReviewSummaryStats EMPTY = new ReviewSummaryStats(
                0,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        );
    }
}
