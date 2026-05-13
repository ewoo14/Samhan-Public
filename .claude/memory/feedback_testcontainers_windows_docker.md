---
name: Windows Docker Desktop + Testcontainers — npipe 한계 + DOCKER_HOST 우회
description: PM 환경 (Windows + Docker Desktop) 에서 Testcontainers 가 npipe 로 연결 시 IT 가 skip 되거나 실패. DOCKER_HOST=tcp://localhost:2375 로 우회 권장
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: PM 환경이 Windows + Docker Desktop 인 경우, `./gradlew :services:<svc>:test` 를 실행하기 전 다음 한 가지를 보장한다.

1. **Docker Desktop 설정 → General → "Expose daemon on tcp://localhost:2375 without TLS" 체크** + `DOCKER_HOST=tcp://localhost:2375` PowerShell `$env:DOCKER_HOST` 설정
2. 또는 npipe 가 정상 동작함을 `docker info` + Testcontainers ryuk 로그로 확인

**Why**: 2026-05-04 PR #21 hotfix 작업 중, PM 통합 단계에서 SlipServiceTest / SlipDomainTest 는 단위 테스트 (Mockito 만, Docker 불필요) 로 76건 PASS 검증 가능. 하지만 SlipInspectControllerIT (Testcontainers PostgreSQL) 는 PM 환경에서 사전 검증이 필요했으나:
- Docker Desktop 은 npipe (`\\.\pipe\docker_engine`) 로만 listen
- Testcontainers Java client 가 가끔 npipe handshake 실패 → `Could not find a valid Docker environment` → IT 일괄 skip
- PM 이 "Docker 미가용" 으로 판단하고 Layer 2 skip → CI 에서 fail 발견

**증상 식별**:
```
Testcontainers DockerClientProviderStrategy
  Could not find a valid Docker environment. Please see logs and check configuration
```
또는
```
TestContainersConfigurationException: Could not find a valid Docker environment
```

**우회 절차** (확실):
```powershell
# 1. Docker Desktop GUI → Settings → General → 체크박스 켜기
#    "Expose daemon on tcp://localhost:2375 without TLS"
# 2. Apply & Restart
# 3. PowerShell 에서:
$env:DOCKER_HOST = "tcp://localhost:2375"
docker info  # 정상 응답 확인

# 4. Gradle 실행 (자식 프로세스도 환경변수 상속)
.\gradlew.bat :services:slip-service:test --no-daemon
```

**보안 주의**: tcp://localhost:2375 는 인증 없음. **로컬 개발 환경에서만** 사용. 회사 LAN 노출 금지.

**대안 (복잡, 비추천)**: `~/.testcontainers.properties` 에 `docker.host=npipe:////./pipe/docker_engine` 명시. 하지만 Testcontainers 버전마다 동작 다름.

**관련 메모리**: `feedback_pm_integration_build_check.md` Layer 2 (Docker 가용 IT 실행). 본 메모리는 그 Layer 2 가 PM 환경에서 강제될 수 있도록 하는 **세팅 가드**.

**적용 시점**:
- 새 PM 머신 셋업 시 1회
- Docker Desktop 업데이트 후 npipe 깨졌을 때
- 4-team 슬라이스 통합 단계마다 `docker info` 사전 확인
