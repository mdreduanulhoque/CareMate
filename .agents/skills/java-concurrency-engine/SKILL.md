---
name: java-concurrency-engine
description: >-
  Use this skill when implementing or debugging CareMate's multithreaded workflows:
  per-caretaker ReentrantLock booking concurrency, max-2-bookings-per-day enforcement,
  contract auto-completion scheduler, 7-day review lock worker, or async thread pools.
---

# CareMate Java Concurrency & Scheduling Skill

## 1. Per-Caretaker Lock + Transaction Ordering (CRITICAL)
Never acquire and release a `ReentrantLock` *inside* a `@Transactional` method. Under MySQL `REPEATABLE READ`, releasing the lock before the database transaction commits allows a waiting thread to enter and read stale pre-commit booking counts!

**Mandatory Pattern:**
1. Use a non-transactional coordinator service (e.g., `BookingConcurrencyCoordinator`) that manages per-caretaker locks (e.g., `ConcurrentHashMap<Long, ReentrantLock>` or fixed-stripe `ReentrantLock[]` keyed by `Math.floorMod(caretakerId.hashCode(), STRIPE_COUNT)` to guarantee zero unbounded memory growth while isolating caretakers).
2. Acquire the caretaker lock **before** invoking the `@Transactional` booking service method (`HiringTransactionalService`).
3. Release the lock in a `finally` block **after** the `@Transactional` method commits (or rolls back).
4. Pair with JPA `@Version` optimistic locking on `caretaker_profiles` as defense-in-depth across processes.

## 2. Booking Invariants (Non-Overlapping + Max 2/Day)
Enforce BOTH invariants when a Customer creates a hire request AND when a Caretaker accepts a hire request:
1. **Valid Time Window:** `scheduledStartTime` must be strictly before `scheduledEndTime`, and reasonable duration bounds must be enforced.
2. **Non-Overlapping Check:** Against all blocking bookings for the caretaker (`SCHEDULED`, `ACTIVE`), reject if `existing.scheduledStartTime < requested.scheduledEndTime && existing.scheduledEndTime > requested.scheduledStartTime`.
3. **Max 2 Bookings Per Calendar Day (`Asia/Dhaka`):**
   - Convert `[scheduledStartTime, scheduledEndTime)` to `ZonedDateTime` in `ZoneId.of("Asia/Dhaka")`.
   - Enumerate every `LocalDate` touched by the requested shift (handle shifts spanning midnight cleanly).
   - For each touched `LocalDate`, verify the caretaker has `< 2` blocking bookings (`SCHEDULED`, `ACTIVE`) touching that same calendar day.
4. Return HTTP `409 Conflict` with a clear, empathetic user-facing message when an overlap or daily cap is hit.

## 3. Managed Thread Pools & Scheduled Workers
Configure dedicated, explicitly bounded executors in `AsyncAndSchedulingConfig`:
- **`contractLifecycleExecutor` (`ThreadPoolTaskExecutor`):** Core/max pool sizes, bounded queue capacity, named threads (`contract-worker-`), graceful shutdown (`setWaitForTasksToCompleteOnShutdown(true)`).
- **`driveValidationExecutor` (`ThreadPoolTaskExecutor`):** Bounded pool (`drive-check-`) for async Google Drive URL reachability checks.
- **`notificationExecutor` (`ThreadPoolTaskExecutor`):** Bounded pool (`notify-`) for async notification and audit dispatch.
- **`ScheduledExecutorService` / `@Scheduled` Workers:**
  1. **Contract Lifecycle Job (e.g., every 30–60s):**
     - Promotes `SCHEDULED` contracts with `scheduledStartTime <= now` to `ACTIVE`.
     - Finds `ACTIVE` (or overdue `SCHEDULED`) contracts with `scheduledEndTime <= now`, delegates each to `contractLifecycleExecutor` to transition to `COMPLETED_PENDING_SETTLEMENT`, idempotently create both `reviews` rows (`UNIQUE(contract_id, reviewer_id)`), refresh availability, and push WebSocket alerts.
  2. **Review Lock-Window Job (e.g., every 15m):**
     - Marks submitted reviews where `editable_until <= now` and `is_locked = false` as `is_locked = true`.
     - Asynchronously recalculates caretaker/customer rating aggregates when changes occur.

## 4. Idempotency & Concurrency Verification
- Every background worker action must be idempotent (safe if executed twice).
- Always write an automated concurrency test using `ExecutorService` + `CountDownLatch` that fires concurrent booking requests for the same caretaker and asserts:
  - Overlapping concurrent hires yield **exactly 1** success and `N-1` conflicts.
  - 3 non-overlapping same-day concurrent hires yield **exactly 2** successes and **1** daily-cap conflict.
