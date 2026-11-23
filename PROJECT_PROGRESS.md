# CareBridge — Implementation Progress & Handoff Tracker

Use this file together with [CareMate.md](./CareMate.md), [AGENTS.md](./AGENTS.md), and the 5 workspace skills in `.agents/skills/` to resume work seamlessly in any new session without losing context.

## Environment & Confirmed Decisions
- **Brand & Root Package:** **CareBridge** (`com.carebridge`)
- **Java / Build:** Java 21 (`C:\Users\Reduan\.jdks\jbr-21.0.11`), Maven Wrapper (`backend/mvnw.cmd`), Spring Boot `3.4.5`
- **Database:** XAMPP MariaDB `10.4.32-MariaDB` (`localhost:3306`, schema `carebridge`), Flyway `9.22.3` (`spring.jpa.hibernate.ddl-auto=validate`, SQL uses standard `BOOLEAN` for MariaDB 10.4 + MySQL 8.0 + H2 compatibility)
- **Frontend:** React 19 + Vite 6 + Tailwind CSS 4 (`frontend/`, Node `v22.22.0`)
- **Untracked Local Config:** `backend/src/main/resources/application-local.properties` (gitignored; template at `application-local.properties.example`)

---

## Completed Phases

### Phase 0 — Governance & Mandatory Skills (Done)
- Created `AGENTS.md` (phase-gated execution, root-cause-only fixes, security/DTO/timezone/mobile rules) and 5 skills in `.agents/skills/`: `spring-boot-backend`, `java-concurrency-engine`, `websocket-realtime`, `mobile-first-react-ui`, `phase-gate-audit`.

### Phase 1 — Scaffolding, Flyway V1 & Core JWT Auth (Done)
- Flyway `V1__create_users_table.sql` (`users`).
- Stateless HS256 JWT auth (`JwtService`, `JwtAuthenticationFilter`, `CareBridgeUserDetailsService`), BCrypt (12), Bangladeshi mobile normalization (`PhoneNumberNormalizer`), protected Admin bootstrap seeder (`AdminBootstrapSeeder`), 4-field Customer signup, Caretaker signup, Mobile+Password login, `/api/auth/me`, `/api/admin/status`.
- Mobile-first React shell (`AppShell` with Section 6.3 role-specific bottom nav), `AuthContext`, `ProtectedRoute`, Login & Signup pages.

### Phase 2 — Caretaker Verification, Re-Application Audit Trail & Async Drive Validator (Done)
- Flyway `V2__create_caretaker_verification_and_profiles.sql` (`caretaker_applications`, `caretaker_profiles` with `@Version`, `caretaker_certifications`).
- Bounded `@Async("driveValidationExecutor")` SSRF-safe Google Drive link pre-validator (`GoogleDriveUrlValidator`, `SsrfSafeGoogleDriveProbeClient`, `AsyncDriveValidationService` running network I/O outside DB transactions).
- Caretaker Application #1, distinct-row Re-Applications (#2+) preserving full Admin audit trail, mandatory Admin rejection reasons, automatic `caretaker_profiles` unlock on approval, and post-approval new certification queue leaving live profiles uninterrupted.
- Caretaker Verification/Certifications UI and Admin Applications & Certifications Queues with rejection modals.

### Phase 3 — Approved Caretaker Profile Editor, Avatar Upload & Hunters Feed (Done)
- Secure avatar upload & media serving (`AvatarStorageService`, `AvatarMediaController` at `/api/media/avatars/{filename}`: magic-byte JPEG/PNG/WebP validation, 2 MB cap, UUID filenames, path-traversal protection).
- Approved Caretaker Profile APIs (`GET/PUT /api/caretaker/profile/me`, `PATCH /api/caretaker/profile/availability`, `POST /api/caretaker/profile/avatar`).
- Privacy-protected Hunters Feed API (`GET /api/feed/caretakers`, `GET /api/feed/caretakers/{caretakerUserId}`) with dynamic filtering (`availableOnly=true` default, `rateType`, `maxBudget`, `area`, `minRating`, `keyword` including approved certs only), Section 2.4 RBAC (blocks unverified caretakers), and strict omission of phone numbers & emails.
- Mobile-first `HuntersFeedPage`, `CaretakerPublicProfilePage`, and `CaretakerProfileEditor`.

### Phase 4 — WebSocket (STOMP + SockJS), Privacy-Protected Contact Unlock & Live Negotiation Chat (Done)
- Flyway `V3__create_contact_unlocks_and_chat_messages.sql` (`contact_unlocks` with unique `(customer_id, caretaker_id)` pair, `chat_messages` with composite `(room_id, sent_at, id)` index).
- Spring WebSocket STOMP + SockJS (`/ws` with explicit origin allowlist, heartbeat scheduler, sanitized `CareBridgeStompErrorHandler`) and strict `StompAuthChannelInterceptor` enforcing Bearer JWT on `CONNECT`, strict channel/room/role allowlist on `SUBSCRIBE` & `SEND`, and blocking direct client publishes to `/topic/**`, `/queue/**`, or `/user/**`.
- Click-to-reveal contact unlock API (`POST/GET /api/contacts/unlock/{caretakerUserId}`, Customer-only per Section 2.4) returning revealed mobile number + deterministic room `NEGOTIATION:{customerId}:{caretakerId}` while keeping phone numbers strictly omitted from Hunters Feed & public detail payloads.
- Persistent negotiation chat REST (`GET /api/chats/rooms`, `GET /api/chats/negotiation/{roomId}`, `GET/POST /api/chats/negotiation/{roomId}/messages`) and STOMP (`@MessageMapping("/chat/negotiation/{roomId}")`) backed by `ChatRoomAuthorizationService` (IDOR protection) and server-derived sender identity.
- Bounded `@Async("notificationExecutor")` `RealtimeEventDispatcher` pushing after-commit live updates to `/topic/feed/availability` (availability/rate edits), `/topic/admin/alerts` (new applications, re-applications, new certifications), and `/user/{userId}/queue/notifications` (contact unlocks, incoming chat messages, verification decisions).
- React `WebSocketProvider` (`@stomp/stompjs` + `sockjs-client` with bounded reconnect and automatic re-subscription), `ConnectionBanner`, `CaretakerPublicProfilePage` click-to-reveal unlock (one-tap `tel:` Call + Copy + Open Live Chat), `NegotiationChatsPage` & `NegotiationChatRoomPage` (server-`id` deduplication, `Asia/Dhaka` timestamps, REST reconnect sync/fallback), live Hunters Feed availability updates, and live Admin queue auto-refresh & toast alerts.

### Phase 5 — Thread-Safe Formal Hiring Engine, Max-2-Per-Day Cap & Early Termination (Done)
- Flyway `V4__create_hire_contracts_table.sql` (`hire_contracts` with explicit `TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)` for MariaDB 10.4 + MySQL 8.0 + H2 compatibility, `@Version` optimistic locking, composite caretaker/status/time & scheduler indexes).
- Pure `Asia/Dhaka` schedule invariant validator (`BookingScheduleValidator`) enforcing valid durations, strict non-overlapping windows, and max **2 confirmed bookings per Dhaka calendar day** (including half-open `[start, end)` midnight-spanning shifts).
- Strictly non-transactional fixed-stripe per-caretaker `ReentrantLock` coordinator (`BookingConcurrencyCoordinator`, 256 fair lock stripes) acquiring locks *before* `HiringTransactionalService` (`@Transactional`) and releasing *after* DB commit, paired with `LockModeType.OPTIMISTIC_FORCE_INCREMENT` on `CaretakerProfile.version` (`409 Conflict` mapped in `GlobalExceptionHandler`).
- Complete REST API (`HireContractController` at `/api/hires`): Customer-only `POST /api/hires`, role/IDOR-scoped `GET /api/hires` & `/{id}`, privacy-safe `GET /api/hires/caretaker/{id}/schedule` (zero PII), Caretaker `accept`/`decline`, and participant/Admin `cancel` (pre-start, frees slot) & `end-early` (active shift -> `COMPLETED_PENDING_SETTLEMENT`, records `actualEndTime`, frees remaining schedule).
- Live WebSocket pushes on `/user/{id}/queue/notifications` for every contract transition (`HIRE_REQUESTED`, `HIRE_ACCEPTED`, `HIRE_DECLINED`, `CONTRACT_CANCELLED`, `CONTRACT_ENDED_EARLY`) and live `todayBookingsCount`/`dailyCapReachedToday` updates on `/topic/feed/availability`.
- Mobile-first Hiring UI: `HireRequestModal` (Dhaka time inputs + live daily schedule slots), `ContractReasonModal`, `ContractCard` (with Relative Trust Preview for Caretakers), `CustomerHiresPage` (`/customer/hires`), upgraded `CaretakerRequestsGatePage` (`/caretaker/requests`), `CaretakerBookingsPage` (`/caretaker/bookings`), Hire CTAs on Profile & Negotiation Chat, and live `2/2 Booked Today` badges on Hunters Feed.

### Phase 6 — Automated Background Workers & Bidirectional 7-Day Editable Feedback System (Done)
- Flyway `V5__create_reviews_table.sql` (`reviews` table with `UNIQUE(contract_id, reviewer_id)`, `is_submitted`, `is_edited`, `is_locked`, `editable_until`, and lock-maintenance/profile-review indexes).
- Managed `ScheduledExecutorService` (`carebridge-sched-`) + bounded `contractLifecycleExecutor` (`ThreadPoolTaskExecutor`, `contract-worker-`) in `AsyncConfig` powering:
  - `ContractLifecycleScheduler` (auto-promoting due `SCHEDULED -> ACTIVE` and auto-completing expired shifts to `COMPLETED_PENDING_SETTLEMENT` via per-caretaker `BookingConcurrencyCoordinator`, idempotently creating dual pending `reviews`, recalculating caretaker metrics, broadcasting live feed availability, and pushing WebSocket alerts).
  - `ReviewLockMaintenanceScheduler` (permanently locking submitted reviews older than 7 days and triggering async caretaker metric recalculation on `contractLifecycleExecutor`).
- Immediate dual pending review generation on **End Service Early** as well, with microsecond timestamp alignment (`ChronoUnit.MICROS`) matching SQL `TIMESTAMP(6)`.
- Bidirectional Feedback API (`FeedbackController` at `/api/reviews`: `/pending`, `/authored`, `/contract/{id}`, `POST /{id}/submit`, `PUT /{id}` 7-day edit with `edited=true` and immutable `editableUntil`, `/caretaker/{id}`, `/customer/{id}`, `/admin`), plus live customer trust ratings (`customerAvgRating`, `customerWorkAgainPercentage`, `customerReviewCount`) and viewer feedback flags wired into `HireContractResponse`.
- Mobile-first Feedback UI: `ReviewFormModal` (1–5 star picker, role-specific Yes/No repeat-engagement prompt, 7-day window notice), `ReviewItemCard` (with `"Edited"` badge + updated timestamp & lock indicator), `PendingReviewsBanner`, `CustomerReviewsModal` on Caretaker incoming requests, and Verified Family Reviews on `CaretakerPublicProfilePage`.

### Phase 7 — Dual Payment Confirmation, Real-Time 3-Way Admin Dispute Mediation & Admin Users (Done)
- Flyway `V6__create_payment_disputes_table.sql` (`payment_disputes` table with `UNIQUE(contract_id)`, `OPEN`/`UNDER_REVIEW`/`RESOLVED` status, customer/caretaker statements, Admin resolution note, and timestamps).
- Dual Payment Confirmation & Dispute Mediation API (`PaymentAndDisputeController` + `PaymentAndDisputeService`): Customer `POST /api/payments/contracts/{id}/customer-done` (with optional Cash/bKash/Nagad/Bank reference note), Caretaker `POST /caretaker-confirm` (`CLOSED_PAID`) or `POST /caretaker-refuse` (`PAYMENT_DISPUTED` + instant `/topic/admin/alerts` `PAYMENT_DISPUTE` alert), participant `PUT /api/disputes/contract/{id}/statement`, Admin `POST /join` (`UNDER_REVIEW`) & `POST /resolve` (`DISPUTE_RESOLVED`).
- Real-Time **3-Way WebSocket Dispute Mediation Room**: enabled strict 3-way authorization in `ChatRoomAuthorizationService` & `StompAuthChannelInterceptor` for `/topic/chat/dispute/{contractId}` and `/app/chat/dispute/{contractId}` (exclusively contract Customer, contract Caretaker, or `ROLE_ADMIN`, locking sends read-only once `DISPUTE_RESOLVED`), backed by persisted `chat_messages` (`room_type = DISPUTE`, `contract_id`).
- Admin Users Management API (`AdminUserController` at `/api/admin/users`: role/keyword search without password leakage, non-admin activate/deactivate moderation, and approved caretaker availability moderation with live `/topic/feed/availability` broadcast).
- Mobile-first UI: `PaymentActionModal`, upgraded `ContractCard` dual payment controls, `DisputeMediationRoomPage` (collapsible pinned payment/contract context + role-distinguished messages for Relative, Caretaker, and Admin Mediator + inline Admin resolve), `AdminDisputesPage`, and `AdminUsersPage` — zero placeholder tabs remain across all 3 roles.
- **Current Verified Baseline:** `87/87` backend tests passing (including live 3-way STOMP dispute tests), frontend `npm run lint` (0 errors, 0 warnings) + chunked `npm run build` clean, live XAMPP MariaDB verified at Flyway `V6` (`ddl-auto=validate`).

---

## Remaining Phases (Awaiting Explicit Permission Per Phase)

- **NEXT -> Phase 8: End-to-End Flow Verification, Security Hardening & Mobile UX Polish**
  - Full Section 2.4 RBAC matrix audit, end-to-end verification of Flows 5.1–5.3 on mobile (`390x844`) & desktop, security sweep, and root `README.md`.
