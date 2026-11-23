package com.carebridge.booking;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodically scans for scheduled shifts due to start and active/overdue shifts due to complete,
 * handing each contract off to the bounded contractLifecycleExecutor worker pool.
 */
@Component
public class ContractLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContractLifecycleScheduler.class);

    private final HireContractRepository hireContractRepository;
    private final BookingConcurrencyCoordinator bookingCoordinator;
    private final ThreadPoolTaskExecutor contractLifecycleExecutor;
    private final ScheduledExecutorService scheduledExecutor;
    private final boolean schedulingEnabled;
    private final long intervalMs;

    private ScheduledFuture<?> scheduledTask;

    public ContractLifecycleScheduler(
            HireContractRepository hireContractRepository,
            BookingConcurrencyCoordinator bookingCoordinator,
            @Qualifier("contractLifecycleExecutor") ThreadPoolTaskExecutor contractLifecycleExecutor,
            @Qualifier("careBridgeScheduledExecutor") ScheduledExecutorService scheduledExecutor,
            @Value("${carebridge.scheduling.enabled:true}") boolean schedulingEnabled,
            @Value("${carebridge.scheduler.contract-lifecycle-ms:30000}") long intervalMs
    ) {
        this.hireContractRepository = hireContractRepository;
        this.bookingCoordinator = bookingCoordinator;
        this.contractLifecycleExecutor = contractLifecycleExecutor;
        this.scheduledExecutor = scheduledExecutor;
        this.schedulingEnabled = schedulingEnabled;
        this.intervalMs = Math.max(5000L, intervalMs);
    }

    @PostConstruct
    void startSchedule() {
        if (!schedulingEnabled) {
            return;
        }
        scheduledTask = scheduledExecutor.scheduleWithFixedDelay(
                () -> {
                    try {
                        runLifecycleSweep(Instant.now());
                    } catch (RuntimeException ex) {
                        log.error("Contract lifecycle sweep encountered an unexpected error", ex);
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

    public SweepResult runLifecycleSweep(Instant now) {
        List<Long> toActivate = hireContractRepository.findScheduledIdsReadyToActivate(now);
        List<Long> toComplete = hireContractRepository.findContractIdsDueForAutoCompletion(
                ContractStatus.BLOCKING_STATUSES,
                now
        );

        List<CompletableFuture<Void>> workerTasks = new ArrayList<>(toActivate.size() + toComplete.size());

        for (Long contractId : toActivate) {
            workerTasks.add(CompletableFuture.runAsync(() -> {
                try {
                    bookingCoordinator.activateScheduledContract(contractId, now);
                } catch (RuntimeException ex) {
                    log.warn("Failed to auto-activate scheduled contract {}: {}", contractId, ex.getMessage());
                }
            }, contractLifecycleExecutor));
        }

        for (Long contractId : toComplete) {
            workerTasks.add(CompletableFuture.runAsync(() -> {
                try {
                    bookingCoordinator.autoCompleteExpiredContract(contractId, now);
                } catch (RuntimeException ex) {
                    log.warn("Failed to auto-complete expired contract {}: {}", contractId, ex.getMessage());
                }
            }, contractLifecycleExecutor));
        }

        return new SweepResult(toActivate.size(), toComplete.size(), workerTasks);
    }

    public record SweepResult(
            int activationDispatched,
            int completionDispatched,
            List<CompletableFuture<Void>> workerTasks
    ) {
        public void awaitAll() {
            CompletableFuture.allOf(workerTasks.toArray(CompletableFuture[]::new)).join();
        }
    }
}
