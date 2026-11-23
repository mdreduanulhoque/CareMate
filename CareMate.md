# Project Master Plan: On-Demand Hospital Caretaker and Nurse Platform

**Working Title:** CareBridge (customizable) **Primary Target Device:** Smartphone (Mobile-First Web Application) **Document Purpose:** Complete functional, architectural, and technical blueprint ready for future phase-wise implementation planning.

## 1\. Main Idea and Vision

### 1.1 Problem Statement

In today's fast-paced urban life, money is often not the primary obstacle when an elderly parent, grandparent, or close family member is hospitalized—shortage of time is. Work schedules, office responsibilities, and meetings do not pause during medical emergencies. Families are frequently forced to either neglect urgent professional duties, leave vulnerable loved ones unattended in hospital wards, or hire unverified attendants through risky informal contacts.

### 1.2 Proposed Solution

This project is a mobile-first web platform that connects relatives of hospitalized patients with verified, professional caretakers and nurses.

- Professional caretakers and nurses apply by submitting personal details and supporting documents (NID, certificates, and experience letters via Google Drive links).  
- An Administrator manually reviews and verifies every application before approving or rejecting it with a clear reason.  
- Approved caretakers customize their public profiles with photos, descriptions, availability status, and hourly or daily rates in BDT (Taka), making them discoverable on the Hunters Feed.  
- Relatives sign up quickly with minimal form friction, browse verified caretakers, unlock contact details on demand to negotiate terms, formalize a booking with an agreed rate and duration, and maintain trust through mutual reviews and dual payment confirmation backed by Admin dispute mediation.

### 1.3 Core Design Philosophy

- **Mobile-First Architecture:** Designed primarily for smartphones with vertical single-column cards, large touch targets, and thumb-friendly navigation.  
- **Empathy and Simplicity:** When a family member is hospitalized, users are stressed and impatient. Forms for customers are kept extremely short, clear, and fast to complete.  
- **Two-Way Accountability:** Both relatives and caretakers review each other, and both must confirm payment completion, creating a safe environment for everyone.

## 2\. Main Users and Roles

### 2.1 Patient's Relative (Customer / Hirer)

- **Context:** Time-constrained and emotionally stressed, typically accessing the app from a phone while at a hospital or workplace.  
- **Onboarding Fields:** Full Name, Mobile Number, Address, Email (optional), and Password.  
- **Key Capabilities:** Browse and filter verified caretakers, click to unlock contact details, negotiate via phone or live chat, submit hire requests, cancel or end contracts early if needed, confirm payment, and submit or edit reviews within 7 days.

### 2.2 Professional Caretaker / Nurse (Service Provider)

- **Context:** Trained nurses, paramedics, or experienced hospital attendants offering hourly or daily care paid in BDT (Taka).  
- **Application Fields:** Full Name, Mobile Number, Email (optional), Date of Birth (DOB), Address, Relevant Qualification, Experience (optional), and Google Drive link containing NID, Certificates, and Experience Letters.  
- **Post-Approval Profile Fields:** Profile Image, Bio/Description, Availability Status (Available or Not Available), and Service Rate (per hour or per day in BDT).  
- **Key Capabilities:** Re-apply as a new application if previously rejected, submit additional new certifications for Admin verification after approval, accept or decline hire requests (up to 2 non-overlapping bookings per day), cancel or end contracts early, confirm or dispute payment, and review customers.

### 2.3 Administrator (Verifier and Dispute Mediator)

- **Context:** Platform moderator responsible for safety, credential verification, and fair dispute resolution.  
- **Key Capabilities:** Review pending caretaker applications and Google Drive documents, approve or reject applications with mandatory reasons, verify new certifications added by already-approved caretakers, and act as a middleman in payment disputes.

### 2.4 Role Permissions Summary

| Capability | Relative (Customer) | Caretaker (Unverified) | Caretaker (Approved) | Admin |
| :---- | :---- | :---- | :---- | :---- |
| Account Registration and Login | Yes | Yes | Yes | Protected |
| Browse and Filter Hunters Feed | Yes | No | Read-Only | Yes |
| Submit Initial or New Re-Application | No | Yes | No | No |
| Submit New Certifications for Review | No | No | Yes | No |
| Approve or Reject Applications and Certs | No | No | No | Yes |
| Edit Profile, Rate, and Availability | No | Locked | Yes | Moderation |
| Unlock Contacts and Send Hire Request | Yes | No | No | No |
| Accept or Decline Hire Request | No | No | Yes | No |
| Cancel or End Active Contract Early | Yes | No | Yes | Override |
| Submit and Edit Reviews (7-Day Window) | Yes | No | Yes | View/Moderate |
| Confirm Payment and Join Disputes | Yes | No | Yes | Mediate/Resolve |

## 3\. Comprehensive Feature List

### 3.1 Authentication and Low-Friction Onboarding

- Login using **Mobile Number and Password**, since email is optional for both relatives and caretakers.  
- Stateless JWT (JSON Web Token) authentication with Spring Security role-based access control.  
- Minimal 4-field signup flow for relatives so they can start searching for caretakers in under 30 seconds.

### 3.2 Caretaker Verification, Re-Application, and Certification Updates

- **Initial Verification Queue:** Admin reviews all pending applications, inspects the submitted Google Drive documents (NID, certificates, experience letters), and either approves or rejects each applicant.  
- **Mandatory Rejection Reason:** If rejected, the Admin must supply a specific explanation (for example, private Drive link or unclear NID).  
- **Re-Application as a New Application:** A rejected caretaker may submit a fresh application after fixing the issue. Each re-application is stored and tracked as a distinct **new application record** so Admins retain a complete audit trail of prior submissions and rejection reasons.  
- **Post-Approval New Certification Verification:** When an already-approved caretaker wants to add a new qualification or certificate, they submit the new certification details and supporting Google Drive link. Their existing approved profile remains live in the Hunters Feed, while the new certification stays hidden with a Pending Verification status until an Admin approves or rejects it (with a reason).

### 3.3 Caretaker Profile and Availability Management

- Unlocked immediately upon Admin approval.  
- Caretakers upload a profile photo, write a short bio/description, set their rate in BDT (per hour or per day), and toggle availability (`Available` or `Not Available`).  
- Displays verified qualification badges, average star rating, total completed hires, and the percentage of past customers who answered "Yes" to hiring them again.

### 3.4 Hunters Feed (Discovery, Search, and Filtering)

- Displays only Admin-approved caretakers in a clean mobile card layout.  
- Each card highlights key decision metrics: profile photo, name, primary qualification, availability status badge, rate in BDT, average rating, and "Would Hire Again" percentage.  
- Quick filters for Availability (Available Now by default), Rate Type (Hourly or Daily), Maximum Budget (BDT), Area/Location, Minimum Rating, and Keyword Search.

### 3.5 Privacy-Protected Contact and Real-Time Negotiation

- **Click-to-Reveal Contact:** To prevent spam and protect caretaker privacy, phone numbers and direct contact options are hidden by default on public cards.  
- When a logged-in relative taps the **Contact** button on a caretaker's profile, the caretaker's mobile number (with one-tap Call action) and an **in-app WebSocket Negotiation Chat** are unlocked.  
- Both parties discuss the patient's condition, hospital location, shift timing, and exact rate before finalizing a booking.

### 3.6 Formal Hiring, Daily Booking Cap, and Early Termination

- **Hiring Form:** Once terms are agreed upon, the relative taps **Hire** and enters only the essentials: agreed fixed rate (BDT), rate unit (hourly, daily, or fixed), service start date/time, end date/time, and an optional short note (such as hospital name and ward/cabin).  
- **Mutual Acceptance:** The booking remains in `REQUESTED` status until the caretaker taps **Accept** (or **Decline**).  
- **Non-Overlapping Multi-Booking Rule (Max 2 Per Day):** A caretaker can accept multiple non-overlapping bookings (for example, a morning shift and an evening shift), strictly limited to a **maximum of 2 bookings per calendar day**. The system automatically blocks any overlapping time slot or any third booking on the same calendar day.  
- **Cancellation and Early Termination:**  
  - **Before Service Starts:** Either party can cancel a `REQUESTED` or upcoming `SCHEDULED` booking with a brief reason, immediately freeing the caretaker's daily booking slot.  
  - **During Active Service (End Early):** Because hospital situations change unpredictably (such as early patient discharge or medical emergencies), either party can trigger **End Service Early**. The system records the actual stop time, frees the caretaker's remaining schedule, and moves the contract directly into settlement (dual payment confirmation and automatic post-service feedback).

### 3.7 Automatic Post-Service Bidirectional Feedback System

- **Automatic Delivery:** When a contract reaches its scheduled end time—or is ended early—a background worker automatically generates two pending feedback forms (one for the relative, one for the caretaker) and notifies both users.  
- **Fill Immediately or Later:** Users do not need to fill the form on the spot; pending forms stay accessible in their dashboard until completed.  
- **Relative's Review of Caretaker:** Star rating (1 to 5), written review, and a Yes/No button asking: *"Will you hire this caretaker again?"*  
- **Caretaker's Review of Relative:** Star rating (1 to 5), written review, and a Yes/No button asking: *"Will you work for this customer again?"*  
- **Profile Visibility:** Caretaker reviews are visible to relatives browsing the feed, and customer reviews are visible to caretakers when evaluating incoming hire requests.  
- **7-Day Editable Window with Indicator:** Both parties can edit their submitted rating, review text, and Yes/No response for up to **7 days** after initial submission. Any modified review displays a clear **"Edited"** indicator alongside the updated timestamp. After 7 days, the review becomes permanently locked to prevent delayed retaliation.

### 3.8 Dual Payment Confirmation and Admin Dispute Mediation

- Because payments occur directly between parties in the MVP (Cash, bKash, Nagad, or Bank Transfer), both sides must confirm payment completion:  
  1. The relative marks payment as **Payment Done** (with an optional transaction ID or note).  
  2. The caretaker either clicks **Confirm Payment Received** (closing the contract cleanly) or clicks **Refuse / Payment Not Received**.  
- **Admin Middleman Intervention:** If the relative marks payment as done and the caretaker refuses, the contract automatically enters `PAYMENT_DISPUTED`. An Admin is alerted in real time and joins a **3-Way WebSocket Dispute Mediation Room** with both users, where payment evidence or statements can be shared until the Admin records a final resolution.

## 4\. Natural Integration of Core Technical Requirements

### 4.1 Meaningful Multithreading (Java Concurrency in Spring Boot)

Multithreading is integrated naturally into time-sensitive and high-concurrency workflows rather than added artificially:

- **Contract Lifecycle and Auto-Feedback Worker Pool (`ScheduledExecutorService` and `ThreadPoolTaskExecutor`):** A background scheduler periodically checks for active contracts whose scheduled end time has passed. Expired contracts are handed off to a managed worker thread pool that concurrently updates contract statuses, generates both feedback forms, updates caretaker availability, and dispatches alerts without slowing down user HTTP requests.  
- **Thread-Safe Booking and Daily-Cap Enforcement (`ConcurrentHashMap` with per-caretaker `ReentrantLock`):** Multiple relatives may attempt to hire the same top-rated caretaker simultaneously. Acquiring a fine-grained lock per caretaker ID (paired with database transactions and optimistic locking) guarantees thread-safe verification of two critical invariants: no overlapping time windows and no more than 2 bookings on the same calendar day.  
- **Review Lock-Window Maintenance Job:** A lightweight scheduled background task marks submitted reviews older than 7 days as locked and recalculates aggregate rating metrics asynchronously.  
- **Asynchronous Document Link Pre-Validator (`@Async` Thread Pool):** When a caretaker submits a new application or a new certification Google Drive link, a background worker asynchronously checks whether the URL is reachable and publicly accessible, flagging broken or private links for the Admin queue before manual review starts.  
- **Asynchronous Notification Dispatcher:** Saving audit logs, sending optional email alerts, and pushing system notifications run on dedicated async worker threads so mobile users on weak hospital internet connections experience fast response times.

### 4.2 Meaningful Networking (WebSocket Client-Server Communication)

Real-time bidirectional networking is powered natively by **Spring WebSocket with STOMP over SockJS**, establishing persistent full-duplex client-server connections over TCP between the React mobile web client and the Spring Boot backend:

- **Live Negotiation Chat:** Once a relative clicks **Contact**, both parties exchange instant negotiation messages over dedicated WebSocket topic channels without refreshing the page.  
- **Real-Time Hiring and Contract State Alerts:** When a relative submits a hire request, or when a caretaker accepts, declines, cancels, or ends a shift early, the server immediately pushes live status updates over WebSockets to the other party's mobile screen.  
- **Live Availability Updates on the Hunters Feed:** When a caretaker toggles availability or reaches their 2-bookings-per-day limit, connected Hunters Feed clients receive instant badge updates so stressed relatives do not waste time contacting unavailable caretakers.  
- **3-Way Real-Time Admin Dispute Room:** When a payment dispute is triggered, Spring WebSocket opens a shared real-time channel connecting the Admin, Relative, and Caretaker for live mediation and instant resolution updates.  
- **Instant Admin Queue Alerts:** New caretaker applications, re-applications, certification verification requests, and urgent payment disputes stream directly to connected Admin dashboards in real time.

## 5\. End-to-End User Flows

### 5.1 Caretaker Application, Re-Application, and New Certification Flow

1. Caretaker registers an account and submits personal details, qualification, optional experience, and a Google Drive link containing NID, certificates, and experience letters.  
2. An asynchronous background thread validates that the Google Drive link is reachable, and the application enters the Admin queue as `PENDING` (logged as Application \#1).  
3. Admin reviews the documents and chooses one of two outcomes:  
   - **Approved:** Caretaker profile unlocks so they can add a photo, bio, hourly/daily rate in BDT, and availability status. Their profile goes live in the Hunters Feed.  
   - **Rejected:** Admin enters a mandatory rejection reason. Caretaker views the reason, fixes the documents, and may submit a **new application** (logged as Application \#2) which re-enters the Admin queue as `PENDING`.  
4. **Post-Approval New Certification:** An approved caretaker submits a new certificate and Drive link from their dashboard. Their live profile remains active while the new certificate waits in the Admin Certification Queue as `PENDING` until approved or rejected with a reason.

### 5.2 Customer Discovery, Contact Unlock, Negotiation, and Hiring Flow

1. Relative creates an account using minimal fields (Name, Mobile Number, Address, Password, optional Email).  
2. Relative browses the mobile-first Hunters Feed and filters by availability, budget in BDT, qualification, location, and rating.  
3. Relative opens a caretaker's profile to inspect verified qualifications, bio, star rating, "Would Hire Again" percentage, and past reviews.  
4. Relative taps **Contact**, which unlocks the caretaker's phone number and opens the in-app WebSocket Negotiation Chat.  
5. After negotiating terms, relative taps **Hire** and enters the agreed fixed rate, rate unit, and start/end service period.  
6. The backend acquires a per-caretaker thread lock to verify there is no schedule overlap and that the caretaker has fewer than 2 bookings on that calendar day. If valid, the contract is saved as `REQUESTED` and pushed instantly to the caretaker via WebSocket.  
7. Caretaker reviews the customer's profile (including past caretaker reviews of that customer) and taps **Accept** or **Decline**.  
   - If accepted, the contract becomes `SCHEDULED` or `ACTIVE`.  
   - Before start, either party may **Cancel** to release the slot.  
   - During active service, either party may choose **End Service Early** if hospital needs change.

### 5.3 Completion, Automatic Feedback, Payment Confirmation, and Dispute Flow

1. When the scheduled end time arrives (detected by the background scheduler thread) or when either party triggers **End Service Early**, the contract moves to `COMPLETED_PENDING_SETTLEMENT`.  
2. Two automatic processes trigger simultaneously:  
   - **Mutual Feedback Generation:** Pending feedback forms are created for both relative and caretaker. Each can submit their 1-to-5 rating, review text, and Yes/No repeat-engagement choice immediately or later. Submitted reviews appear on both profiles and remain editable for **7 days** with an **"Edited"** badge before locking.  
   - **Dual Payment Confirmation:** Relative marks payment as **Payment Done**. Caretaker is notified via WebSocket to confirm receipt.  
3. Payment resolution branches into two paths:  
   - **Confirmed:** Caretaker taps **Payment Received**, closing the contract as `CLOSED_PAID`.  
   - **Disputed:** Caretaker taps **Refuse / Not Received**, moving the contract to `PAYMENT_DISPUTED`. Admin receives an instant WebSocket alert, opens the 3-Way Dispute Mediation Room, reviews statements/evidence, and closes the case as `DISPUTE_RESOLVED`.

## 6\. Technology Stack and System Architecture

### 6.1 Finalized Technology Stack

| Architectural Layer | Selected Technology | Purpose and Rationale |
| :---- | :---- | :---- |
| **Backend Framework** | Java 17/21 \+ Spring Boot 3.x (Mandatory) | REST APIs, Spring Security, Spring Data JPA, Spring WebSocket, and Java concurrency management. |
| **Database** | MySQL 8.x (Recommended) | ACID-compliant relational storage ideal for booking concurrency, application audit history, reviews, and dispute records. |
| **Frontend Framework** | React (Vite) | Fast mobile-first single-page application with smooth client-side state updates. |
| **Frontend Styling** | Tailwind CSS | Utility-first responsive styling optimized for rapid mobile-first layouts and clean touch-friendly UI components. |
| **Real-Time Networking** | Spring WebSocket (STOMP \+ SockJS) | Full-duplex client-server sockets for live chat, push alerts, feed availability updates, and 3-way dispute rooms. |
| **Multithreading** | Java `ThreadPoolTaskExecutor`, `ScheduledExecutorService`, `ReentrantLock` | Safe concurrent booking checks, automated contract completion, async notifications, and Drive URL validation. |
| **Security** | Spring Security \+ JWT \+ BCrypt | Secure mobile-number login, password hashing, and strict role-based endpoint authorization. |
| **Document & Image Handling** | Google Drive Links (Docs) \+ Local/Cloud Storage (Profile Images) | Zero-cost document submission via Drive links as planned, paired with lightweight avatar storage for approved caretakers. |

### 6.2 Core Database Schema Blueprint

1. **users:** Stores `id`, `full_name`, `mobile_number` (unique), `email` (nullable), `password_hash`, `address`, `role` (`CUSTOMER`, `CARETAKER`, `ADMIN`), `is_active`, and `created_at`.  
2. **caretaker\_applications:** Stores every application and re-application as an independent row: `id`, `user_id` (FK), `application_number`, `dob`, `qualification`, `experience`, `documents_drive_link`, `drive_link_reachable`, `status` (`PENDING`, `APPROVED`, `REJECTED`), `rejection_reason`, `reviewed_by_admin_id` (FK), `submitted_at`, and `reviewed_at`.  
3. **caretaker\_profiles:** Created upon first approval: `id`, `user_id` (FK), `profile_image_url`, `bio`, `rate_amount`, `rate_type` (`HOURLY`, `DAILY`), `is_available`, `avg_rating`, `hire_again_percentage`, and `version` (optimistic locking).  
4. **caretaker\_certifications:** Stores additional certifications submitted by approved caretakers: `id`, `caretaker_profile_id` (FK), `title`, `issuing_organization`, `document_drive_link`, `drive_link_reachable`, `verification_status` (`PENDING`, `APPROVED`, `REJECTED`), `rejection_reason`, `submitted_at`, and `reviewed_at`.  
5. **contact\_unlocks:** Logs when a relative clicks **Contact** on a caretaker profile: `id`, `customer_id` (FK), `caretaker_id` (FK), and `unlocked_at`.  
6. **hire\_contracts:** Stores booking lifecycle and settlement data: `id`, `customer_id` (FK), `caretaker_id` (FK), `agreed_rate`, `rate_type`, `scheduled_start_time`, `scheduled_end_time`, `actual_end_time` (used for early termination), `cancellation_or_early_end_reason`, `initiated_by_id`, `notes`, `status` (`REQUESTED`, `SCHEDULED`, `ACTIVE`, `DECLINED`, `CANCELLED`, `COMPLETED_PENDING_SETTLEMENT`, `CLOSED_PAID`, `PAYMENT_DISPUTED`, `DISPUTE_RESOLVED`), `customer_payment_confirmed`, `caretaker_payment_confirmed`, `payment_reference_note`, and `created_at`.  
7. **reviews:** Stores bidirectional feedback: `id`, `contract_id` (FK), `reviewer_id` (FK), `reviewee_id` (FK), `reviewer_role`, `rating` (1 to 5), `comment`, `would_engage_again` (Yes/No boolean), `is_submitted`, `is_edited`, `submitted_at`, `updated_at`, and `editable_until` (`submitted_at` plus 7 days).  
8. **chat\_messages:** Persists WebSocket messages for negotiation and dispute rooms: `id`, `room_id`, `room_type` (`NEGOTIATION`, `DISPUTE`), `sender_id` (FK), `contract_id` (nullable), `content`, and `sent_at`.  
9. **payment\_disputes:** Tracks Admin mediation: `id`, `contract_id` (FK), `raised_by_id` (FK), `assigned_admin_id` (FK), `status` (`OPEN`, `UNDER_REVIEW`, `RESOLVED`), `customer_statement`, `caretaker_statement`, `admin_resolution_note`, `created_at`, and `resolved_at`.

### 6.3 Mobile-First UI Navigation Layout

- **Customer Bottom Navigation:** `Feed`, `Chats`, `My Hires`, `Profile`  
- **Caretaker Bottom Navigation:** `Requests`, `Chats`, `Bookings`, `Profile`  
- **Admin Navigation:** `Applications`, `Certifications`, `Disputes`, `Users`  
- **Tailwind CSS Design Guidelines:** Sticky search header, swipe/tap-friendly filter chips, full-width primary action buttons (minimum 44px height), high-contrast status badges, and uncluttered forms tailored for one-handed phone use.

## 7\. Project Limitations and Scope Boundaries

### 7.1 Technical and MVP Limitations

- **No Integrated Online Payment Gateway:** Payments (Cash, bKash, Nagad, Bank Transfer) happen outside the system; financial trust relies on dual payment confirmation and manual Admin mediation rather than automated escrow.  
- **Dependency on External Google Drive Links:** Verification relies on applicants keeping their Google Drive document links publicly viewable ("Anyone with the link can view"). If an applicant later restricts or deletes their Drive folder, historical verification documents may become unavailable unless archived by the Admin.  
- **No Automated SMS/OTP Verification in MVP:** Without a paid SMS gateway, mobile numbers are validated by format and uniqueness rather than live OTP codes.  
- **No Live GPS Attendance Tracking:** Caretaker arrival and presence at the hospital are based on mutual agreement rather than geofenced GPS check-ins.

### 7.2 Operational and Policy Limitations

- **Visual Document Verification Only:** Admins check the visual credibility of submitted NIDs and certificates, as the platform does not have direct API access to government NID databases or nursing council registries.  
- **Marketplace Liability Boundary:** The platform acts strictly as a facilitator connecting families with independent caretakers; it does not provide medical care or hold clinical liability for medical outcomes.  
- **Off-Platform Bypass Risk:** Because contact numbers are unlocked after clicking Contact, users could theoretically arrange work off-platform. The platform mitigates this by making verified reviews, profile ratings, and Admin payment dispute protection available exclusively to formal on-platform bookings.  
- **Early Termination Billing Negotiation:** When a contract is ended early, the final adjusted payout amount (if prorated) must be agreed upon by both parties during payment confirmation, with Admin dispute mediation available as a fallback if disagreements arise.

