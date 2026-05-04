# eureka-server

Service discovery for SamhanLogis MSA (Phase 1). All other services register
here at startup and resolve each other via the Eureka client.

## Ports

| Port | Purpose                                |
| ---- | -------------------------------------- |
| 8761 | Eureka REST API + dashboard + actuator |

## Local run

```bash
./gradlew :services:eureka-server:bootRun
```

Dashboard: <http://localhost:8761/>
Health:    <http://localhost:8761/actuator/health>
Metrics:   <http://localhost:8761/actuator/prometheus>

## Profiles

- `default` / `local` — standalone, self-preservation OFF (dev only).
- `docker` — for docker-compose; hostname `eureka-server`.
- `prod-peer1` / `prod-peer2` — HA peer mode, self-preservation ON.

## HA environment variables

When running with `prod-peer1` or `prod-peer2`:

| Variable                  | Description                                 | Default                          |
| ------------------------- | ------------------------------------------- | -------------------------------- |
| `EUREKA_PEER_URL`         | URL of the *other* Eureka peer's `/eureka/` | `http://eureka-peer:8761/eureka/`|
| `EUREKA_INSTANCE_HOSTNAME`| This instance's advertised hostname         | `eureka-peer1` / `eureka-peer2`  |

## Build & image

```bash
./gradlew :services:eureka-server:bootJar
docker build -t samhanlogis/eureka-server:0.1.0 services/eureka-server
```
