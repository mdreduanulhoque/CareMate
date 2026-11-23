---
name: mobile-first-react-ui
description: >-
  Use this skill when designing, building, or refining CareMate's mobile-first
  React (Vite) and Tailwind CSS UI components, navigation, forms, and responsive layouts.
---

# CareMate Mobile-First React + Tailwind UI Skill

## 1. Core UX Philosophy
CareMate users are often stressed family members at a hospital on a smartphone with one free hand and unstable cellular data. Every UI decision must prioritize **speed, calm clarity, trust, and thumb reachability**:
- **Primary Viewport:** Design for `360px–430px` mobile width first; gracefully center or expand for tablet/desktop (`max-w-md` / `max-w-5xl` container discipline).
- **Touch Targets:** Minimum `44px` height (`min-h-[44px]`) on all buttons, filter chips, inputs, and bottom nav tabs.
- **Short Forms:** Customer onboarding must stay ultra-fast (4 required fields + optional email) with clear inline validation.
- **Visual System:** Clean clinical-warm palette (calm medical teal primary, high-contrast slate typography, crisp white cards on soft neutral surface, clear semantic status pills: emerald=Available/Paid, amber=Pending/Requested, rose=Disputed/Rejected, sky=Active/Scheduled).

## 2. Role-Based Navigation Shell
Render a persistent sticky bottom navigation bar on mobile (and clean top/side header on desktop) strictly tailored to the active user's role:
- **Customer:** `Feed`, `Chats`, `My Hires`, `Profile`
- **Caretaker:** `Requests`, `Chats`, `Bookings`, `Profile`
- **Admin:** `Applications`, `Certifications`, `Disputes`, `Users`

## 3. Frontend Architecture (`frontend/src/`)
- `api/` — Axios/fetch client with JWT injection, 401 expiry handler, and structured domain API modules
- `context/` — `AuthContext` (JWT + user role/status), `WebSocketContext` (STOMP connection & subscriptions)
- `components/ui/` — Reusable accessible primitives (`Button`, `Badge`, `Card`, `Modal`, `BottomNav`, `Skeleton`, `Toast`, `EmptyState`, `ConnectionBanner`)
- `features/` — Domain-organized pages & components (`auth/`, `feed/`, `caretaker/`, `chat/`, `hiring/`, `feedback/`, `disputes/`, `admin/`)
- `routes/` — `ProtectedRoute` enforcing auth + role + caretaker approval state

## 4. Key Screen Patterns
- **Hunters Feed Cards:** Single-column cards showing avatar, name, primary qualification + verified badge, live Availability pill, BDT rate (`৳/hr` or `৳/day`), star rating, and `"X% Would Hire Again"` trust badge. Phone number is NEVER shown until the user explicitly taps **Contact**.
- **Contact Unlock & Negotiation:** Tapping **Contact** reveals the phone number with a one-tap `<a href="tel:...">Call</a>` action and opens the live STOMP chat room.
- **Contract Cards:** Clear status timeline badge, BDT rate, formatted `Asia/Dhaka` shift times, context-aware primary actions (`Accept`/`Decline`, `Cancel`, `End Service Early`, `Mark Payment Done`, `Confirm`/`Refuse Payment`, `Open Dispute Room`, `Leave/Edit Review`).
- **3-Way Dispute Room:** Visually distinguish messages by role (`Relative`, `Caretaker`, `Admin Mediator`) with pinned contract/payment reference context at the top.

## 5. Resilience & Verification Checklist
- Every async view implements loading skeletons, empty states, and retryable error banners.
- Verify `npm run build` and `npm run lint` succeed cleanly with zero unused imports or runtime console errors.
- Visually inspect rendered screens at mobile width (`390×844`) and desktop width (`1280×800`) to confirm zero horizontal overflow or clipped buttons.
