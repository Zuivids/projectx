# ProjectX

Online learning platform with paid courses, MFA login and an admin area.
"ProjectX" is a working name; the product name lives in configuration, not in code identifiers.

## Stack

- Backend: Java 21, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator)
- Database: MariaDB 11.4 LTS, schema managed by Flyway (`src/main/resources/db/migration`)
- Frontend: React 19 + TypeScript + Vite in [`frontend/`](frontend/README.md) (needs Node.js 24)
- Tests: JUnit 5 + Testcontainers (needs Docker running), Vitest for the frontend
- CI: GitHub Actions runs backend and frontend checks on every pull request

## Run the whole app locally

1. Start the backend: run `TestProjectxApplication` from `src/test` in your IDE (see below for other ways).
2. Start the frontend: `cd frontend`, then `npm install` (first time only) and `npm run dev`.
3. Open http://localhost:5173.

## Run the backend locally

Easiest: run `TestProjectxApplication` from `src/test` in your IDE. It starts a throwaway MariaDB
container and the `local` profile automatically.

With the Docker Compose database instead:

1. Copy `.env.example` to `.env` and set your own passwords.
2. Start the database: `docker compose up -d`
3. Start the app with the `local` profile and the variables from `.env` set, e.g. in PowerShell:
   `$env:DB_PASSWORD="..."; ./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"`
4. Health check: http://localhost:8080/actuator/health

The `local` profile allows the session cookie over plain http and contains a development-only MFA
encryption key. Staging and production must set `MFA_ENCRYPTION_KEY` instead.

## Authentication API

Server-side sessions (HttpOnly cookie) with CSRF protection. Before the first POST, call
`GET /api/v1/auth/csrf`, then send the `XSRF-TOKEN` cookie value in the `X-XSRF-TOKEN` header of
every POST/PUT/DELETE.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/auth/csrf` | Sets the `XSRF-TOKEN` cookie |
| POST | `/api/v1/auth/register` | `{email, password, displayName}` → always 202 (no account enumeration) |
| POST | `/api/v1/auth/login` | `{email, password}` → `{status: AUTHENTICATED \| MFA_REQUIRED, mfaSetupRequired}` |
| POST | `/api/v1/auth/mfa` | `{code}` → completes an `MFA_REQUIRED` login; 6-digit code or recovery code |
| POST | `/api/v1/auth/logout` | Ends the session (204) |
| GET | `/api/v1/me` | Current user, roles and MFA state |
| POST | `/api/v1/me/mfa/totp` | Starts authenticator setup → `{secret, otpauthUri}` (render the URI as a QR code) |
| POST | `/api/v1/me/mfa/totp/confirm` | `{code}` → enables MFA, returns 10 recovery codes (shown once) |

Security rules:

- Passwords: 12–128 characters, hashed with Argon2id.
- Rate limits per client IP: login and MFA 20 requests/minute, registration 10/hour. Over the limit the
  API answers `429 Too Many Requests` with a `Retry-After` header.
- Five failed password or MFA attempts from one IP block that IP for the account for 15 minutes; the real
  user on another IP can still log in. Twenty failures from all IPs together lock the whole account for
  15 minutes. The error message is the same for a wrong email, a wrong password and a blocked account.
- Rate limits and per-IP blocks are kept in memory (one app instance). Behind a reverse proxy, forwarded
  headers must be configured first (backlog W-06), or all users would share the proxy's IP.
- Session timeouts: learners 30 minutes idle and 24 hours at most; admins 15 minutes idle and 8 hours at
  most (`app.session` in `application.yml`). Disabling an account ends its open sessions on the next
  request. The frontend then shows the login page with "Sesija beidzās".
- MFA is optional for learners. Admin endpoints (`/api/v1/admin/**`) need an admin role **and** a session
  that passed MFA.
- Authenticator codes and recovery codes each work once. TOTP secrets are AES-256-GCM encrypted.
- Security events (logins, failures, lockouts, MFA changes) go to the append-only `audit_log` table.

## Conventions

- Controllers accept and return DTOs only; JPA entities never leave the service layer.
- Every schema change is a new Flyway migration (`V2__...sql`); applied migrations are never edited.
- Secrets come from environment variables or a vault, never from Git.
- Security is deny-by-default: every new endpoint requires authentication unless explicitly opened.

Requirements: see the team requirements doc (Latvian). Open decisions and work: [BACKLOG.md](BACKLOG.md).
