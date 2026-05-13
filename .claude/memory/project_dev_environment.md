---
name: Local Dev Environment & Quick Commands
description: Toolchain locations, env vars, and common Gradle/Docker commands for the SamhanLogis project
type: project
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**Toolchain installed (2026-05-04)**:
- JDK: Eclipse Temurin **17.0.18+8** at `C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot`
- `JAVA_HOME` set in user env; `%JAVA_HOME%\bin` prepended to user PATH
- Gradle distribution: 8.10.2 at `C:\tools\gradle-8.10.2` (used once to bootstrap the wrapper; not strictly needed afterward)
- Gradle Wrapper committed in repo — use `./gradlew.bat` (Windows) or `./gradlew` (POSIX)
- Docker Desktop: 29.4.0
- Node.js: v24.15.0 (for frontend in later phases)
- Maven: not installed; Gradle is the canonical build tool (Maven Wrapper would be added per-module if anyone wants it)

**Common commands (run from repo root in PowerShell)**:
```powershell
# Build everything except tests (works on Korean path)
./gradlew.bat assemble

# Run a single service
./gradlew.bat :services:eureka-server:bootRun     # :8761
./gradlew.bat :services:api-gateway:bootRun       # :8080
./gradlew.bat :services:auth-service:bootRun      # :8081

# Local infra stack
docker compose -f infrastructure/docker-compose.yml up -d
docker compose -f infrastructure/docker-compose.yml down       # stop
docker compose -f infrastructure/docker-compose.yml down -v    # destructive: wipe volumes
```

**DB credentials (DEV ONLY)**: user `samhan`, password `samhan_dev_pw`, port 5432. 10 service-specific DBs auto-created via `infrastructure/postgres/init/01-create-databases.sql`.

**Module list mapped in root `settings.gradle`**: `:shared:common`, `:services:eureka-server`, `:services:api-gateway`, `:services:auth-service`. Add new microservices by appending to settings.gradle and the `leafProjects` array in root `build.gradle`.

**How to apply**: When the user asks to run/build something, prefer these commands. If they want to run tests locally, see the Korean Path memory — recommend they either move the project to an ASCII path or run tests in CI/Docker.

**Claude Code 환경 (2026-05-04 확인)**:
- Claude Code CLI: **v2.1.126** at `C:\Users\user\.local\bin\claude` — Remote Control(v2.1.51+) + PushNotification(v2.1.110+) 모두 지원
- 모바일 Claude 앱 (iOS/Android) → 데스크톱 Claude Code 세션 원격 제어 가능 (Claude Max 구독 필수, Research Preview)
- Remote Control 보안: 로컬 세션 outbound-only, 모바일 명령은 데스크톱의 `permissions.json` 권한으로 실행
- 셋업 단계: ① Claude Max 구독 ② 모바일 앱 설치 + 같은 계정 로그인 ③ `claude code` 세션 시작 시 자동 페어링
- PushNotification hook: settings.json 에 셋업해 작업 완료/승인 필요 시 모바일 알림 (구독 + 페어링 후 활성)
- 공식 문서: https://code.claude.com/docs/en/remote-control
