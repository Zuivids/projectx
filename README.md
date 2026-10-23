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

## Roles and the first super admin

| Role | Can |
| --- | --- |
| Learner (`LEARNER`) | Use the site; every account has this role |
| Admin (`ADMIN`) | Admin panel: courses, and managing learner accounts |
| Super admin (`SUPER_ADMIN`) | Everything an admin can, plus managing admin accounts and granting/removing roles |

Admin roles always require MFA. Nobody can block themselves or change their own roles.

The first super admin is created at startup:

1. Register normally.
2. List your email under `app.bootstrap.admin-emails` (see the commented example in `application-local.yml`,
   or set the environment variable `APP_BOOTSTRAP_ADMIN_EMAILS`) and restart the backend. The account gets the
   super admin role.
3. Log in and set up MFA on the security page. The admin panel opens only in a session that passed MFA.
4. From then on, appoint other admins in the admin panel (Lietotāji) and remove the setting.

Important:

- **Register first, then list the email.** Until email verification exists (backlog W-01), anyone could
  register a listed email that is not taken yet and become admin at the next restart. Once the real person has
  registered, the email is taken and this is no longer possible. Remove the setting when it is no longer needed.
- `TestProjectxApplication` starts an empty database on every run, so for a lasting admin account use the
  Docker Compose database (see "Run the backend locally").

## User management API

Admin role + MFA. Every action returns the user and is audited with the admin as actor and the user as target.
Blocking, role changes and MFA resets end the user's open sessions immediately (security version).

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/admin/users?query=&status=&page=&size=` | Search by email or name |
| GET | `/api/v1/admin/users/{id}` | Details and the last 30 activity entries |
| POST | `/api/v1/admin/users/{id}/disable` · `/enable` | Block / unblock the account |
| POST | `/api/v1/admin/users/{id}/unlock` | Lift a lockout after failed logins (account and every IP) |
| POST | `/api/v1/admin/users/{id}/reset-mfa` | Remove authenticator and recovery codes (lost phone) |
| PUT / DELETE | `/api/v1/admin/users/{id}/roles/{ADMIN\|SUPER_ADMIN}` | Grant / remove a role (super admin only) |

Accounts with an admin role can only be managed by a super admin.

## Courses API

Courses go through `DRAFT → PUBLISHED → ARCHIVED → DRAFT`. Only published courses are visible to learners.
Prices are in euro cents (`4900` = 49.00 EUR). Lessons are `AUDIO` or `TEXT`; the audio file itself waits for
the media hosting decision (backlog D-06).

Admin endpoints (admin role + MFA). Every change returns the whole course and is written to the audit log.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/admin/courses?status=&page=&size=` | List all courses |
| POST | `/api/v1/admin/courses` | Create `{title, slug?, description, priceCents, level?}`; slug generated from the title if empty |
| GET | `/api/v1/admin/courses/{id}` | Course with sections and lessons |
| PUT | `/api/v1/admin/courses/{id}` | Update; send the `version` you loaded, 409 if someone changed it meanwhile |
| DELETE | `/api/v1/admin/courses/{id}` | Only drafts that were never published; otherwise archive |
| POST | `/api/v1/admin/courses/{id}/publish` · `/archive` · `/restore` | Change status |
| POST | `/api/v1/admin/courses/{id}/sections` | Add section `{title}` |
| PUT / DELETE | `/api/v1/admin/courses/{id}/sections/{sectionId}` | Rename / delete section |
| PUT | `/api/v1/admin/courses/{id}/sections/order` | Reorder sections `{ids: [...]}` (every id exactly once) |
| POST | `/api/v1/admin/courses/{id}/sections/{sectionId}/lessons` | Add lesson `{title, type, contentText?, audioRef?, durationSeconds?, preview}` |
| PUT | `/api/v1/admin/courses/{id}/sections/{sectionId}/lessons/order` | Reorder lessons |
| PUT / DELETE | `/api/v1/admin/courses/{id}/lessons/{lessonId}` | Update / delete lesson |

Public catalog (no login):

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/courses?page=&size=` | Published courses |
| GET | `/api/v1/courses/{slug}` | Course page: description, price, table of contents (no lesson content) |
| GET | `/api/v1/courses/{slug}/lessons/{lessonId}` | Content of a free preview lesson; other lessons answer 403 until purchases exist |

## Conventions

- Controllers accept and return DTOs only; JPA entities never leave the service layer.
- Every schema change is a new Flyway migration (`V2__...sql`); applied migrations are never edited.
- Secrets come from environment variables or a vault, never from Git.
- Security is deny-by-default: every new endpoint requires authentication unless explicitly opened.

Requirements: see the team requirements doc (Latvian). Open decisions and work: [BACKLOG.md](BACKLOG.md).
