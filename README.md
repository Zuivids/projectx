# ProjectX

Online learning platform with paid courses, MFA login and an admin area.
"ProjectX" is a working name; the product name lives in configuration, not in code identifiers.

## Stack

- Java 21, Spring Boot 4.1 (Web MVC, Security, Data JPA, Validation, Actuator)
- MariaDB 11.4 LTS, schema managed by Flyway (`src/main/resources/db/migration`)
- Tests: JUnit 5 + Testcontainers (needs Docker running)

## Run locally

1. Copy `.env.example` to `.env` and set your own passwords.
2. Start the database: `docker compose up -d`
3. Start the app with the variables from `.env` set, e.g. in PowerShell:
   `$env:DB_PASSWORD="..."; ./mvnw spring-boot:run`
4. Health check: http://localhost:8080/actuator/health

Alternatively, run `TestProjectxApplication` from `src/test` — it starts a throwaway MariaDB container automatically.

## Conventions

- Controllers accept and return DTOs only; JPA entities never leave the service layer.
- Every schema change is a new Flyway migration (`V2__...sql`); applied migrations are never edited.
- Secrets come from environment variables or a vault, never from Git.
- Security is deny-by-default: every new endpoint requires authentication unless explicitly opened.

Requirements: see the team requirements doc (Latvian).
