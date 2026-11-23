# CareMate Engineering Rules & Project Governance

These rules apply to the entire CareMate repository (`d:\AAAAAAAAAAAAAAA\CareMate`).

## 1. Non-Negotiable Workflow Rules
- **Phase-Gated Execution:** Never move from one implementation phase to the next without the user's explicit permission.
- **Root-Cause Only (Zero Error Bypassing):** Never suppress, skip, comment out, or work around errors, failing tests, security checks, or build failures. Always diagnose the root cause and fix it properly. If manual intervention is required (e.g., starting the Windows `MySQL80` service, supplying local DB credentials, or resolving OS permissions), stop and ask the user clearly.
- **Mandatory Post-Phase Audit:** At the end of every phase, execute the `phase-gate-audit` skill covering:
  1. User requirements traceability against `CareMate.md`
  2. End-to-end flow continuity (zero regressions across prior phases)
  3. Security review (OWASP, RBAC, IDOR, SSRF, XSS, SQLi, secrets)
  4. Code readability, modularity, and maintainability
  5. Automated build & test verification
- **GitHub Commit Messages:** After every major code change or completed phase, provide a clean Conventional Commit message (title + bullet summary) ready for the user to commit and push. Do not push to remote unless explicitly requested.

## 2. Local Toolchain & Environment Pinning
- **JDK:** Target **Java 21**. Use `C:\Users\Reduan\.jdks\jbr-21.0.11` (`JAVA_HOME`) rather than JDK 27 on default PATH to guarantee full compatibility with Spring Boot 3.x and annotation processors.
- **Build Tool:** Use the project's Maven Wrapper (`backend/mvnw.cmd`) so builds do not depend on a global Maven install.
- **Frontend Runtime:** Node.js 22 + npm + Vite + React + Tailwind CSS inside `frontend/`.
- **Database:** XAMPP MariaDB 10.4 / MySQL 8.0 compatible (`carebridge` schema). Manage all schema changes exclusively with **Flyway** migrations (`spring.jpa.hibernate.ddl-auto=validate`, Flyway `9.22.3` for MariaDB 10.4 + MySQL 8.0 compatibility).
- **Branding & Package:** Brand the application as **CareBridge** using Java root package `com.carebridge`.
- **Secrets Hygiene:** Never commit passwords, JWT signing secrets, or local connection strings. Keep secrets in untracked local config (`application-local.properties`, `.env.local`) and maintain sanitized `*.example` templates in Git.

## 3. Core Engineering Standards
- **Strict DTO Boundaries:** Controllers must only accept and return dedicated request/response DTOs (Java records). Never expose JPA entities directly over REST or WebSocket.
- **Timezone Consistency:** Persist timestamps in UTC (`Instant`) and evaluate business calendar-day rules (e.g., max 2 bookings per calendar day) in `Asia/Dhaka` (`ZoneId.of("Asia/Dhaka")`).
- **Mobile-First UX:** Every screen must work cleanly at `360px–430px` smartphone width with minimum `44px` touch targets, minimal form friction for stressed families, and clear offline/reconnecting states.
- **RBAC Enforcement:** Enforce role permissions at both REST (`@PreAuthorize` + ownership checks) and STOMP WebSocket (`ChannelInterceptor` on `CONNECT`, `SUBSCRIBE`, and `SEND`) layers matching Section 2.4 of `CareMate.md`.
