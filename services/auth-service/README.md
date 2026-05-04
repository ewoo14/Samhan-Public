# auth-service

JWT issuer + account management for SamhanLogis MSA (Phase 1, plan §3.4).

## Ports

| Port | Purpose                                |
| ---- | -------------------------------------- |
| 8081 | REST API + actuator                    |

## Database

PostgreSQL `auth_db` — owns the `accounts` table (UUID PK, soft-deleted).

## Endpoints

| Method | Path             | Auth                            | Description                          |
| ------ | ---------------- | ------------------------------- | ------------------------------------ |
| POST   | `/auth/login`    | public                          | Issue JWT for login_id + password    |
| POST   | `/auth/register` | `ROLE_MASTER` (gateway header)  | Create new account (BCrypt hash)     |
| GET    | `/auth/me`       | gateway-set `X-User-Id`         | Echo current user profile            |

All responses are wrapped in `ApiResponse<T>`; errors surface as
`BusinessException` with Korean messages.

## Environment variables

| Variable      | Default                                                   | Description                   |
| ------------- | --------------------------------------------------------- | ----------------------------- |
| `DB_HOST`     | `localhost`                                               | PostgreSQL host               |
| `DB_PORT`     | `5432`                                                    | PostgreSQL port               |
| `DB_NAME`     | `auth_db`                                                 | Database name                 |
| `DB_USER`     | `samhan`                                                  | Database user                 |
| `DB_PASSWORD` | `samhan_dev_pw`                                           | Database password             |
| `EUREKA_URL`  | `http://localhost:8761/eureka/`                           | Eureka registry URL           |
| `JWT_SECRET`  | `dev-secret-change-me-in-production-32bytes-min!`         | HS256 signing key (>=32 byte) |

## Profiles

- `default` — PostgreSQL + Eureka (production-like)
- `local` — H2 in-memory + Eureka disabled (offline dev)

## Local run

```bash
./gradlew :services:auth-service:bootRun --args='--spring.profiles.active=local'
```

## Build & image

```bash
./gradlew :services:auth-service:bootJar
docker build -t samhanlogis/auth-service:0.1.0 services/auth-service
```
