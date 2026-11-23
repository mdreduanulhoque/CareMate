package com.carebridge.feedback;

import com.carebridge.common.TransactionCommitHooks;
import com.carebridge.user.Role;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled maintenance worker that permanently locks submitted reviews once their 7-day edit window
 * expires and triggers asynchronous rating aggregate recalculations on the worker pool.
 */
@Component
public class ReviewLockMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewLockMaintenanceScheduler.class);

    private final ReviewRepository reviewRepository;
    private final FeedbackAggregateService feedbackAggregateService;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService scheduledExecutor;
    private final boolean schedulingEnabled;
    private final long intervalMs;

    private ScheduledFuture<?> scheduledTask;

    public ReviewLockMaintenanceScheduler(
            ReviewRepository reviewRepository,
            FeedbackAggregateService feedbackAggregateService,
            TransactionTemplate transactionTemplate,
            @Qualifier("careBridgeScheduledExecutor") ScheduledExecutorService scheduledExecutor,
            @Value("${carebridge.scheduling.enabled:true}") boolean schedulingEnabled,
            @Value("${carebridge.scheduler.review-lock-ms:900000}") long intervalMs
    ) {
        this.reviewRepository = reviewRepository;
        this.feedbackAggregateService = feedbackAggregateService;
        this.transactionTemplate = transactionTemplate;
        this.scheduledExecutor = scheduledExecutor;
        this.schedulingEnabled = schedulingEnabled;
        this.intervalMs = Math.max(10_000L, intervalMs);
    }

    @PostConstruct
    void startSchedule() {
        if (!schedulingEnabled) {
            return;
        }
        scheduledTask = scheduledExecutor.scheduleWithFixedDelay(
                () -> {
                    try {
                        runReviewLockMaintenance(Instant.now());
                    } catch (RuntimeException ex) {
                        log.error("Review lock maintenance sweep encountered an unexpected error", ex);
                    }
                },
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    @PreDestroy
    void stopSchedule() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
    }

    public int runReviewLockMaintenance(Instant now) {
        LockResult result = transactionTemplate.execute(status -> {
            List<Review> expired = reviewRepository.findExpiredUnlockedSubmittedReviews(now);
            if (expired.isEmpty()) {
                return new LockResult(0, Set.of());
            }

            Set<Long> affectedCaretakerIds = new HashSet<>();
            for (Review review : expired) {
                review.markLocked();
                if (review.getReviewerRole() == Role.CUSTOMER) {
                    affectedCaretakerIds.add(review.getReviewee().getId());
                }
            }
            reviewRepository.saveAllAndFlush(expired);

            Set<Long> idsForAsync = Set.copyOf(affectedCaretakerIds);
            TransactionCommitHooks.runAfterCommit(() -> {
                for (Long caretakerUserId : idsForAsync) {
                    feedbackAggregateService.recalculateCaretakerMetricsAsync(caretakerUserId);
                }
            });

            return new LockResult(expired.size(), idsForAsync);
        });

        return result != null ? result.lockedCount() : 0;
    }

    private record LockResult(int lockedCount, Set<Long> affectedCaretakerIds) {
    }
}
