---
name: phase-gate-audit
description: >-
  Use this skill at the end of EVERY CareMate implementation phase before asking
  the user for permission to move to the next phase. Performs code readability,
  security, user-requirement, flow-continuity, and verification checks, then
  produces a GitHub commit message.
---

# CareMate Mandatory Phase-Gate Audit Skill

Execute ALL 5 gates at the completion of every phase. Do NOT declare a phase complete or ask to proceed if any gate fails—diagnose and fix the root cause first.

## Gate 1: User Requirements & Flow Continuity
1. Cross-check every deliverable of the current phase against `CareMate.md`.
2. Confirm every previously completed user flow still works end-to-end (no broken APIs, migrations, routes, WebSocket topics, or role guards).
3. Verify edge cases explicitly called out in `CareMate.md` (optional email, rejected re-applications as new rows, live profile unaffected by pending new certs, hidden phone number until Contact click, max 2 bookings/day, 7-day review edit with "Edited" badge, dual payment confirmation -> dispute).

## Gate 2: Security Audit
Check every file added or modified in the phase for:
- [ ] **Authentication & RBAC:** Every endpoint enforces `@PreAuthorize` matching Section 2.4 of `CareMate.md`; no public route leaks protected data.
- [ ] **IDOR Protection:** Services verify the acting user actually owns or participates in the target entity (contract, review, chat room, profile, certification, dispute).
- [ ] **Input Validation & Injection:** All DTOs validated with Jakarta Validation; all SQL/JPQL queries parameterized (zero string concatenation); no `dangerouslySetInnerHTML` in React.
- [ ] **Secrets & Config:** Zero hardcoded credentials, tokens, or API keys in committed files; `.gitignore` excludes `.env*` and `application-local.properties`.
- [ ] **Upload & URL Safety (when applicable):** Avatar uploads verify MIME + magic bytes + size + path containment; Google Drive validator enforces HTTPS + host allowlist + SSRF IP checks.
- [ ] **WebSocket Safety (when applicable):** STOMP `CONNECT`, `SUBSCRIBE`, and `SEND` enforce JWT auth and room participation.

## Gate 3: Code Readability & Maintainability
- [ ] Clear, descriptive names for classes, methods, variables, DTOs, and React components.
- [ ] Single-responsibility services and modular components (no monolithic god files or duplicated business logic).
- [ ] Zero swallowed exceptions (`catch (Exception e) {}`), zero commented-out dead code, zero debug `console.log` spam.
- [ ] Helpful, empathetic error messages returned to mobile users without exposing internal stack traces.

## Gate 4: Build, Test & Runtime Verification
Run and verify all applicable checks cleanly (never skip tests):
- Backend: `$env:JAVA_HOME="C:\Users\Reduan\.jdks\jbr-21.0.11"; cd backend; .\mvnw.cmd test`
- Frontend: `cd frontend; npm run lint; npm run build` (plus unit tests when configured)
- Inspect `git status` and `git diff --stat` to confirm no unintended or sensitive files are staged/untracked.

## Gate 5: Phase Report & GitHub Commit Message
Present a concise structured summary to the user containing:
1. **What was built** (with clickable file links).
2. **Audit Scorecard** (Requirements & Flow Continuity, Security, Readability, Tests/Build).
3. **Ready-to-Push GitHub Commit Message** in Conventional Commit format:
   ```text
   <type>(<scope>): <short imperative summary>

   - <key change 1>
   - <key change 2>
   - Verified: <tests/checks run>
   ```
4. **Explicit Gate Stop:** Clearly state the next phase preview and wait for the user's explicit permission before starting it.
