---
name: spring-boot-backend
description: >-
  Use this skill when building, modifying, or reviewing Spring Boot 3 REST APIs,
  Flyway MySQL 8 migrations, Spring Security JWT authentication, file uploads,
  or Google Drive link validation in the CareMate backend.
---

# CareMate Spring Boot Backend Skill

## Architecture & Package Layout
Organize `backend/src/main/java/com/carebridge/` cleanly by domain module or crisp layered packages:
- `config/` — Security, CORS, Async executors, WebSocket, Jackson, OpenAPI, storage config
- `security/` — `JwtService`, `JwtAuthenticationFilter`, `CustomUserDetails`, RBAC helpers
- `common/` — `ApiErrorResponse`, `GlobalExceptionHandler`, `PhoneNumberNormalizer`, pagination helpers
- `auth/` — Registration (Customer 4-field minimal, Caretaker + App #1), Mobile+Password Login, Protected Admin seeder
- `caretaker/` — Applications (re-application audit trail), Profiles, Certifications, Hunters Feed search/filter
- `booking/` — Contact unlock, Hire contracts, Concurrency lock coordinator, Early termination
- `feedback/` — Bidirectional reviews, 7-day edit window, scheduled lock & metric recalculation
- `payment/` — Dual payment confirmation, Admin payment disputes
- `chat/` — Persistent negotiation and 3-way dispute messages
- `notification/` — Async notifications and audit logging

## Environment Execution (Windows)
Always pin Java 21 when running Maven Wrapper commands:
```powershell
$env:JAVA_HOME = "C:\Users\Reduan\.jdks\jbr-21.0.11"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
cd backend
.\mvnw.cmd test
```

## Database & Flyway Standards
1. Set `spring.jpa.hibernate.ddl-auto=validate` in `application.properties`. Never use `update` or `create-drop` against MySQL.
2. Author incremental, immutable Flyway migrations in `backend/src/main/resources/db/migration/` (`V1__...sql`, `V2__...sql`, etc.).
3. Use `BIGINT AUTO_INCREMENT` primary keys, explicit foreign keys, `DECIMAL(10,2)` for BDT currency amounts, `TIMESTAMP(6)` for UTC instants, and composite indexes for query hot paths (Hunters Feed filters, caretaker bookings by date/status, chat history by `room_id, sent_at`).
4. Normalize Bangladesh mobile numbers (`01XXXXXXXXX`) before uniqueness checks and persistence so format variants cannot create duplicate accounts.

## Security & Input Validation Standards
1. **Authentication:** Stateless JWT (HMAC-SHA256 with >= 256-bit secret from environment/local config). Hash passwords with `BCryptPasswordEncoder` (strength >= 10).
2. **Authorization:** Enable `@EnableMethodSecurity`. Guard every endpoint with `@PreAuthorize` AND verify resource ownership inside the service layer (prevent IDOR on profiles, contracts, chats, reviews, and disputes).
3. **Admin Protection:** Never allow `ROLE_ADMIN` registration through public signup endpoints. Seed initial admin credentials only via untracked local configuration/environment variables.
4. **Input Validation:** Annotate all request DTOs with Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Min`, `@Max`, `@DecimalMin`, `@Pattern`). Handle validation and domain exceptions centrally in `GlobalExceptionHandler` without leaking stack traces or SQL errors.
5. **Avatar Upload Security:**
   - Allowlist MIME types & magic bytes: JPEG (`FF D8 FF`), PNG (`89 50 4E 47`), WebP (`RIFF....WEBP`).
   - Enforce strict max file size (e.g., 2 MB).
   - Store under UUID filenames in a configured upload directory; validate normalized canonical paths to prevent path traversal (`..`).
6. **Google Drive Link Pre-Validator (SSRF Safe):**
   - Require `https://` scheme and strict hostname allowlist (`drive.google.com`, `docs.google.com`).
   - Disable blind redirect following to arbitrary hosts; re-validate hostnames and reject loopback/private/link-local IPs (`127.0.0.0/8`, `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`, `::1`).
   - Enforce short connect/read timeouts (e.g., 5 seconds) on a dedicated bounded `@Async` pool.

## Verification Checklist
- `.\mvnw.cmd clean test` passes with zero warnings or skipped tests.
- All Flyway migrations apply cleanly from scratch on an empty schema.
- Every new endpoint has controller/service tests verifying both happy paths and `401`/`403`/`400`/`409` failure modes.
