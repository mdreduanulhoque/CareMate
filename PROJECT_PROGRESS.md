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
- **Current Verified Baseline:** `72/72` backend tests passing (including live embedded-server STOMP over SockJS security & realtime broadcast tests), frontend `npm run lint` (0 errors, 0 warnings) + `npm run build` clean, live XAMPP MariaDB verified at Flyway `V3` (`ddl-auto=validate`).

---

## Remaining Phases (Awaiting Explicit Permission Per Phase)

- **NEXT -> Phase 5: Thread-Safe Formal Hiring Engine, Max-2-Per-Day Cap & Early Termination**
  - Flyway `V4` (`hire_contracts`), per-caretaker `ReentrantLock` outside `@Transactional` + `@Version`, non-overlapping + max-2-per-calendar-day (`Asia/Dhaka`), Cancel & End Service Early, `CountDownLatch` concurrency tests, Hiring/Requests/Bookings UI.
- **Phase 6: Automated Background Workers & Bidirectional 7-Day Editable Feedback System**
  - Flyway `V5` (`reviews`), `ScheduledExecutorService` + `ThreadPoolTaskExecutor` auto-activation/auto-completion & dual pending review generation, 7-day editable window with `"Edited"` badge, scheduled lock & async aggregate recalculation.
- **Phase 7: Dual Payment Confirmation & Real-Time 3-Way Admin Dispute Mediation**
  - Flyway `V6` (`payment_disputes`), Customer Payment Done, Caretaker Confirm (`CLOSED_PAID`) / Refuse (`PAYMENT_DISPUTED`), 3-Way WebSocket Dispute Room (`/topic/chat/dispute/{contractId}`), Admin resolution (`DISPUTE_RESOLVED`) & Admin Users tab.
- **Phase 8: End-to-End Flow Verification, Security Hardening & Mobile UX Polish**
  - Full Section 2.4 RBAC matrix audit, end-to-end verification of Flows 5.1–5.3 on mobile (`390x844`) & desktop, security sweep, and root `README.md`.
