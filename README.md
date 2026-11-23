# CareBridge — On-Demand Hospital Caretaker & Nurse Platform

**CareBridge** is a mobile-first full-stack web platform connecting relatives of hospitalized patients with manually verified professional nurses and hospital caretakers across Bangladesh (rates in BDT / Taka, schedule rules in `Asia/Dhaka` time).

Built with **Java 21 + Spring Boot 3.4.5**, **Spring Security (Stateless JWT + BCrypt)**, **Spring WebSocket (STOMP + SockJS)**, **MySQL 8 / MariaDB 10.4 + Flyway**, and **React 19 + Vite 6 + Tailwind CSS 4**.

---

## Core Capabilities

- **Low-Friction Mobile Onboarding:** Mobile-number + password authentication (`01XXXXXXXXX` / `+8801XXXXXXXXX`), 4-field Customer (Relative) signup, Caretaker signup, and protected Admin bootstrap seeding.
- **Manual Verification, Re-Application Audit Trail & Post-Approval Certifications:** Caretakers submit credentials + Google Drive links (`Application #1`). An async SSRF-safe worker pre-checks Drive reachability. Admins approve (unlocking public profiles) or reject with mandatory reasons; rejected applicants submit distinct-row re-applications (`#2+`). Approved caretakers can submit additional certifications that queue for Admin verification without interrupting their live profile.
- **Privacy-Protected Hunters Feed & Click-to-Reveal Contact:** Displays Admin-approved caretakers with dynamic filtering (Availability, Rate Type, Max BDT Budget, Area, Min Rating, Keyword/Cert Search). Caretaker phone numbers are strictly hidden until a Customer clicks **Contact & Unlock**, revealing the number (`tel:` call + copy) and opening a live negotiation chat.
- **Thread-Safe Formal Hiring Engine (Max 2 Non-Overlapping Bookings / Day):** Customers propose formal contracts (`REQUESTED`) with agreed BDT rate, shift start/end, and ward/cabin notes. Caretakers review customer trust metrics before accepting/declining. Both parties can cancel pre-start (releasing slots) or **End Service Early** during active shifts.
- **Automatic Bidirectional 7-Day Editable Feedback:** Background workers auto-activate due shifts, auto-complete expired shifts, and generate dual pending reviews (1–5 stars, comment, Yes/No repeat-engagement prompt). Reviews remain editable for 7 days with an **"Edited"** indicator before locking permanently.
- **Dual Payment Confirmation & Real-Time 3-Way Admin Dispute Mediation:** Direct Cash/bKash/Nagad/Bank settlements require Customer **Payment Done** + Caretaker **Confirm Payment Received** (`CLOSED_PAID`) or **Refuse / Not Received** (`PAYMENT_DISPUTED`). Disputed contracts trigger instant Admin WebSocket alerts and open a persisted **3-Way WebSocket Dispute Room** (Relative + Caretaker + Admin Mediator) until resolved (`DISPUTE_RESOLVED`).
- **Admin User & Availability Moderation:** Search users by role/keyword, activate/deactivate non-admin accounts, and moderate caretaker availability with real-time Hunters Feed updates.

---

## Architecture Overview

```text
CareMate/
├── backend/                  # Spring Boot 3.4.5 (Java 21, com.carebridge)
│   ├── src/main/java/com/carebridge/
│   │   ├── admin/            # Admin verification queues & user moderation
│   │   ├── auth/             # JWT login/register, Admin bootstrap seeder
│   │   ├── booking/          # Contact unlocks, HireContract, ReentrantLock coordinator
│   │   ├── caretaker/        # Applications, profiles, certifications, SSRF-safe Drive probe
│   │   ├── chat/             # REST + STOMP negotiation & 3-way dispute chat
│   │   ├── common/           # GlobalExceptionHandler, TransactionCommitHooks, DTO errors
│   │   ├── config/           # AsyncConfig bounded thread pools & schedulers
│   │   ├── feed/             # Hunters Feed dynamic JPA Specifications & privacy DTOs
│   │   ├── feedback/         # Bidirectional 7-day editable reviews & schedulers
│   │   ├── notification/     # Async RealtimeEventDispatcher over STOMP
│   │   ├── payment/          # Dual payment confirmation & dispute mediation
│   │   ├── security/         # SecurityConfig, JwtAuthenticationFilter, Role RBAC
│   │   ├── storage/          # AvatarStorageService (magic bytes, 2 MB cap, UUID filenames)
│   │   ├── user/             # User entity, Role enum, Bangladeshi phone normalizer
│   │   └── websocket/        # WebSocketConfig, StompAuthChannelInterceptor, ErrorHandler
│   └── src/main/resources/
│       ├── db/migration/     # Flyway V1–V6 SQL migrations (ddl-auto=validate)
│       ├── application.properties
│       └── application-local.properties.example
├── frontend/                 # React 19 + Vite 6 + Tailwind CSS 4 (mobile-first SPA)
│   └── src/
│       ├── api/              # Authenticated fetch clients (auth, feed, chat, hire, review, payment, admin)
│       ├── components/       # AppShell (role bottom/top nav), ConnectionBanner, UI primitives
│       ├── context/          # AuthContext & WebSocketContext (STOMP + SockJS reconnect)
│       ├── features/         # Role-scoped screens (auth, feed, chat, hiring, feedback, disputes, caretaker, admin)
│       └── utils/            # Asia/Dhaka date/time formatting utilities
├── CareMate.md               # Master functional & technical blueprint
├── AGENTS.md                 # Engineering governance & quality rules
└── PROJECT_PROGRESS.md       # Phase 0–8 completion record
```

### Database Schema (Flyway `V1`–`V6`)
| Migration | Tables Created | Key Invariants |
| :--- | :--- | :--- |
| `V1` | `users` | Unique normalized Bangladeshi `mobile_number`, BCrypt(12) `password_hash`, `role` (`CUSTOMER`, `CARETAKER`, `ADMIN`). |
| `V2` | `caretaker_applications`, `caretaker_profiles`, `caretaker_certifications` | Distinct rows per re-application `(user_id, application_number)`, `@Version` optimistic locking on `caretaker_profiles`. |
| `V3` | `contact_unlocks`, `chat_messages` | Unique `(customer_id, caretaker_id)` unlock pair; composite `(room_id, sent_at, id)` chat history index. |
| `V4` | `hire_contracts` | Full 9-state contract lifecycle, `@Version` optimistic locking, `actual_end_time`, payment flags, MariaDB 10.4 + MySQL 8 compatible `TIMESTAMP(6)`. |
| `V5` | `reviews` | `UNIQUE(contract_id, reviewer_id)`, `is_submitted`, `is_edited`, `is_locked`, `editable_until` (`submitted_at + 7 days`). |
| `V6` | `payment_disputes` | `UNIQUE(contract_id)`, `OPEN`/`UNDER_REVIEW`/`RESOLVED` status, customer/caretaker statements, Admin resolution note. |

---

## Multithreading & Concurrency Design

All background and concurrent workflows use bounded, explicitly named Java executors configured in `com.carebridge.config.AsyncConfig`:

| Mechanism / Pool | Bean / Prefix | Role in CareBridge |
| :--- | :--- | :--- |
| **Per-Caretaker Fair Striped Locks + Optimistic Locking** | `BookingConcurrencyCoordinator` (256 fair `ReentrantLock` stripes) + `LockModeType.OPTIMISTIC_FORCE_INCREMENT` | Strictly **non-transactional** outer coordinator acquires a per-caretaker lock *before* opening the `@Transactional` DB boundary (`HiringTransactionalService`) and releases *after* commit, guaranteeing zero overlapping shifts and max **2 confirmed bookings per `Asia/Dhaka` calendar day** under parallel load. |
| **Managed Scheduler + Contract Worker Pool** | `careBridgeScheduledExecutor` (`carebridge-sched-`) + `contractLifecycleExecutor` (`contract-worker-`) | Periodically promotes due `SCHEDULED -> ACTIVE` contracts, auto-completes expired shifts to `COMPLETED_PENDING_SETTLEMENT`, idempotently creates dual pending reviews, updates feed availability, and locks submitted reviews older than 7 days. |
| **Async Drive Pre-Validator** | `driveValidationExecutor` (`drive-val-`) | Runs SSRF-hardened Google Drive URL probes *outside* DB transactions via `TransactionCommitHooks.runAfterCommit(...)` so uploads never stall HTTP requests. |
| **Async Notification Dispatcher** | `notificationExecutor` (`notify-`) | Pushes post-commit WebSocket broadcasts (`/topic/feed/availability`, `/topic/admin/alerts`, `/user/{id}/queue/notifications`) without blocking mobile HTTP requests. |

---

## Real-Time WebSocket (STOMP + SockJS) Topology & Security

- **Endpoint:** `/ws` (SockJS fallback + raw WebSocket, explicit CORS origin allowlist).
- **Authentication & Authorization (`StompAuthChannelInterceptor`):**
  - `CONNECT`: Validates `Authorization: Bearer <jwt>` and binds `CareBridgeUserPrincipal`.
  - `SUBSCRIBE`: Enforces a strict allowlist — `/topic/feed/availability` (Customer/Admin/Approved Caretaker), `/topic/admin/alerts` (`ROLE_ADMIN` only), `/user/queue/notifications` & `/user/queue/errors`, `/topic/chat/negotiation/{roomId}` (only unlocked Customer & Caretaker pair), and `/topic/chat/dispute/{contractId}` (only contract Customer, contract Caretaker, or `ROLE_ADMIN`).
  - `SEND`: Permits only `/app/chat/negotiation/{roomId}` and `/app/chat/dispute/{contractId}`; directly blocks client publishes to `/topic/**`, `/queue/**`, or `/user/**` and locks dispute sends once `DISPUTE_RESOLVED`.
- **Resilience:** React `WebSocketContext` implements bounded exponential reconnect, automatic re-subscription, visible `ConnectionBanner`, server-`id` message deduplication, and REST resync/fallback on reconnect.

---

## Local Setup & Running

### Prerequisites
- **Java 21** (e.g., JetBrains Runtime / Temurin JDK 21)
- **Node.js 22+** and **npm**
- **MySQL 8.0** or **XAMPP MariaDB 10.4+** running on `localhost:3306`

### 1. Create Database Schema
Start MySQL/MariaDB (e.g., XAMPP Control Panel -> MySQL) and create the empty schema:
```sql
CREATE DATABASE IF NOT EXISTS carebridge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Configure Untracked Local Secrets
Copy the sanitized template and fill in your local DB credentials, a 256-bit Base64 JWT signing secret, and your local Admin seed credentials:
```powershell
Copy-Item backend/src/main/resources/application-local.properties.example backend/src/main/resources/application-local.properties
```
*(Note: `application-local.properties` is gitignored — never commit local credentials or JWT secrets.)*

### 3. Run the Spring Boot Backend (Port `8080`)
Using the bundled Maven Wrapper with Java 21 pinned:
```powershell
$env:JAVA_HOME = "C:\Users\Reduan\.jdks\jbr-21.0.11"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```
Flyway automatically applies and validates migrations `V1` through `V6`, and `AdminBootstrapSeeder` ensures the initial Admin account exists.

### 4. Run the React Frontend (Port `5173`)
In a second terminal:
```powershell
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` (Vite automatically proxies `/api` and `/ws` to `http://localhost:8080`).

---

## Running Automated Tests & Quality Checks

### Backend Test Suite (H2 MySQL-mode, Unit + REST + Concurrency + Live STOMP + RBAC Matrix)
```powershell
$env:JAVA_HOME = "C:\Users\Reduan\.jdks\jbr-21.0.11"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd backend
.\mvnw.cmd clean test
```

### Frontend Lint & Production Build
```powershell
cd frontend
npm run lint
npm run build
```

---

## MVP Scope Boundaries & Limitations

As documented in Section 7 of [CareMate.md](./CareMate.md):
- **Direct Off-Platform Settlement:** Payments (Cash, bKash, Nagad, Bank Transfer) happen directly between families and caretakers; financial accountability is backed by dual confirmation and 3-way Admin dispute mediation rather than an integrated payment gateway/escrow.
- **External Google Drive Documents:** Caretaker credential review depends on applicants keeping submitted Google Drive links publicly viewable ("Anyone with the link can view").
- **Mobile Validation without Paid SMS Gateway:** Bangladeshi mobile numbers are validated for format and uniqueness rather than live SMS OTP delivery in the MVP.
- **No Live GPS Attendance Tracking:** Caretaker arrival/presence at the hospital is coordinated directly between parties.
- **Visual Credential Review & Platform Role:** Admins visually verify submitted NIDs and certificates (no direct government NID/nursing council API integration), and CareBridge acts strictly as a marketplace facilitator rather than a clinical healthcare provider.
