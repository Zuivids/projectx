# Backlog: open decisions and work

The team's list of things that still need a decision or work. Requirement IDs (e.g. `AUTH-01`) refer to the requirements doc (Latvian).

**How to use**

- Add new items at the bottom of the right table, with the next free ID.
- When a decision is made, write it in the "Decision" column, set Status to `Decided`, and add the date.
- When work is finished, set Status to `Done` and add the commit or pull request.
- Status values: `Open` · `In progress` · `Decided` · `Blocked` · `Done`

## Decisions needed

| ID | Question | Why it matters / what it blocks | Owner | Status | Decision |
| --- | --- | --- | --- | --- | --- |
| D-01 | Which email provider (e.g. Postmark, SendGrid, Amazon SES)? | Blocks W-01 email verification and W-02 password reset | | Open | |
| D-02 | Which payment provider (Stripe, Adyen, PayPal, Montonio, other)? | Blocks all payment work (phase 3) | | Open | |
| D-03 | Who handles VAT and invoice rules (accountant, or a merchant of record like Paddle)? | Affects checkout, invoices and the payment provider choice | | Open | |
| D-04 | Frontend framework (React, Angular, Thymeleaf)? | Blocks all user interface work | | Decided | React + TypeScript (Vite), in `frontend/` (2026-10-22) |
| D-05 | Hosting provider and EU region? | Blocks deployment and W-06 client IP behind a proxy | | Open | |
| D-06 | Audio/video hosting provider, and is DRM needed? | Blocks course content delivery (phase 2) | | Open | |
| D-07 | Final product name and domain | Needed before registering the domain and setting up the payment provider | | Open | |
| D-08 | Budget for a penetration test and legal review before launch | Required before go-live (SEC-17, SEC-20) | | Open | |
| D-09 | In production, is the React app served by Spring Boot or by a separate web server/CDN on the same domain? | Must stay same-origin for the session and CSRF cookies; depends on D-05 | | Open | |

## Work to do

| ID | Item | Details | Blocked by | Status | Done in |
| --- | --- | --- | --- | --- | --- |
| W-01 | Email verification (AUTH-01) | Expiring, single-use link sent after registration | D-01 | Blocked | |
| W-02 | Password reset (AUTH-12) | Expiring token by email; all sessions ended after the change | D-01 | Blocked | |
| W-03 | Leaked-password check (AUTH-03) | Reject new passwords found in known breach lists (e.g. Have I Been Pwned range API, which never sends the full password) | | Open | |
| W-04 | Per-IP rate limiting (AUTH-10) | Without it, anyone can lock an account by deliberately entering 5 wrong passwords | | Open | |
| W-05 | Instant logout of disabled users | A user disabled during an active session stays logged in until the session expires | | Open | |
| W-06 | Correct client IP behind a proxy | Audit log records the proxy's IP until forwarded headers are configured | D-05 | Blocked | |
| W-07 | Shorter session timeouts for admins (AUTH-15) | Currently 30 min idle for everyone; admins should have a shorter idle and an absolute limit | | Open | |
| W-08 | Admin panel features | Panel is an empty frame; needs backend admin endpoints (users, courses, orders) | | Open | |
| W-09 | UI texts in resource files (NFR-12) | Latvian texts are written directly in the components; move them to translation files before adding a second language | | Open | |

## Done

Move finished items here, so the tables above show only what is open.

| ID | Item | Date | Done in |
| --- | --- | --- | --- |
| – | Auth decisions: Spring Security, server sessions, optional MFA for learners (AUTH-07, AUTH-14, AUTH-19) | 2026-10-17 | requirements doc |
| – | Registration, login with TOTP MFA, lockout, audit log (phase 1) | 2026-10-17 | `654beef` on `feature/auth` |
| – | CI with GitHub Actions, Dependabot | 2026-10-22 | PR #2 |
| – | React frontend: landing, login with MFA, registration, home, MFA setup, admin skeleton | 2026-10-22 | `feature/frontend` |
