# api-gateway

Reactive Spring Cloud Gateway — single ingress point for the SamhanLogis MSA.

## Runtime

| Setting       | Value                                        |
|---------------|----------------------------------------------|
| Port          | `8080`                                       |
| Stack         | WebFlux / Netty (NOT servlet)                |
| Discovery     | Eureka client, registers as `api-gateway`    |
| Health probe  | `GET /actuator/health`                       |
| Metrics       | `GET /actuator/prometheus`                   |
| Gateway info  | `GET /actuator/gateway/routes`               |

## Environment variables

| Var          | Default                                                       | Notes                                  |
|--------------|---------------------------------------------------------------|----------------------------------------|
| `EUREKA_URL` | `http://localhost:8761/eureka/`                               | Eureka registry endpoint.              |
| `JWT_SECRET` | `dev-secret-change-me-in-production-32bytes-min!`             | HS256 secret. **Override in prod.** Must match `auth-service`. Minimum 32 bytes. |

## Routes

All routes strip the `/api/` prefix before forwarding (`StripPrefix=1`).

| Path prefix        | Downstream service       | Auth filter                                      |
|--------------------|--------------------------|--------------------------------------------------|
| `/api/auth/**`     | `lb://auth-service`      | none (login must be reachable without a token)   |
| `/api/users/**`    | `lb://user-service`      | `JwtAuthentication` (any authenticated role)     |
| `/api/slips/**`    | `lb://slip-service`      | `JwtAuthentication`                              |
| `/api/inventory/**`| `lb://inventory-service` | `JwtAuthentication`                              |
| `/api/accounting/**`| `lb://accounting-service` | `JwtAuthentication`                             |
| `/api/logs/**`     | `lb://logging-service`   | `JwtAuthentication` with `allowedRoles: [MASTER, MANAGER]` |

## JWT filter behavior

`JwtAuthenticationGatewayFilterFactory` reads `Authorization: Bearer <token>`,
verifies the HS256 signature with `app.security.jwt.secret`, then forwards
identity to the downstream service via two headers:

- `X-User-Id`   — `sub` claim
- `X-User-Role` — `role` claim (one of the 7 `Role` enum values)

Failure responses are JSON envelopes with `application/json;charset=UTF-8`:

| Condition                          | Status | `code`          | `message`                |
|------------------------------------|--------|-----------------|--------------------------|
| Missing `Authorization`            | 401    | `UNAUTHORIZED`  | 인증 토큰이 없습니다     |
| Bad signature / expired / parse err| 401    | `INVALID_TOKEN` | 유효하지 않은 토큰입니다 |
| Role not in `allowedRoles`         | 403    | `FORBIDDEN`     | 권한이 없습니다          |

### Role-based access example

```yaml
- id: logging-service
  uri: lb://logging-service
  predicates:
    - Path=/api/logs/**
  filters:
    - StripPrefix=1
    - name: JwtAuthentication
      args:
        allowedRoles:
          - MASTER
          - MANAGER
```

Empty `allowedRoles` means "any authenticated user". To make a route
permissive on missing tokens (rare — typically only for public reads), set
`required: false` in the filter args.

## Build & run

```bash
# from project root
./gradlew :services:api-gateway:bootJar
java -jar services/api-gateway/build/libs/api-gateway.jar

# docker
./gradlew :services:api-gateway:bootJar
docker build -t samhanlogis/api-gateway:dev services/api-gateway
docker run --rm -p 8080:8080 \
    -e EUREKA_URL=http://host.docker.internal:8761/eureka/ \
    -e JWT_SECRET=$JWT_SECRET \
    samhanlogis/api-gateway:dev
```
