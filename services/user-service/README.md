# user-service

Owns the **employees** and **departments** tables and exposes the org-chart / employee
provisioning REST surface for the SamhanLogis MSA.

## Responsibilities

- CRUD for employees (id is shared 1:1 with `auth-service.accounts.id`).
- Single-level department directory (대표실 / 영업1팀 / 영업2팀 / 영업3팀 / 회계팀).
- Org-chart projection used by the SPA home page.
- Provisioning orchestration: create Employee + create Account in Auth Service via the
  internal `/auth/internal/accounts` endpoints (saga-style — Auth first, compensation if
  the local persist fails).
- Display-name propagation: when an employee's `fullName` is updated, the change is
  synced into `auth-service.accounts.display_name` (Q2 — CEO-confirmed).

## Endpoints

| Method | Path | Required role |
|---|---|---|
| `POST` | `/users/employees` | MASTER, MANAGER |
| `GET` | `/users/employees?departmentId=&role=` | any auth |
| `GET` | `/users/employees/{id}` | any auth |
| `POST` | `/users/employees/lookup` (max 100 ids) | any auth |
| `PATCH` | `/users/employees/{id}` | MASTER, MANAGER |
| `PATCH` | `/users/employees/{id}/role` | MASTER |
| `POST` | `/users/employees/{id}/terminate` | MASTER |
| `GET` | `/users/org-chart` | any auth |
| `GET` | `/users/departments` | any auth |

All ingress is via the API Gateway, which strips `/api/users` to `/users` and forwards
`X-User-Id` / `X-User-Role` headers.

## Default seed password

The `OrgChartSeeder` provisions the 16 real employees of Samhan Logis on first boot when
`app.user.seed-org=true`. Each account is created with the **default password
`samhan!2026`** (CEO-confirmed Q1). Employees must change the password on first login.

## Internal service-to-service token

Both `user-service` (caller) and `auth-service` (callee) share `app.security.internal.token`,
overridable via env `INTERNAL_AUTH_TOKEN`. Default for dev: `dev-internal-token-change-me`.

## DB

PostgreSQL `user_db`. Schema is owned by Flyway:
- `V1__init_user_service.sql`
- `V2__seed_org_chart.sql`

## Local dev

```
gradlew :services:user-service:bootRun
```

Requires `eureka-server` and `auth-service` running, and `user_db` reachable on the
default datasource (or the `local` profile for H2).
