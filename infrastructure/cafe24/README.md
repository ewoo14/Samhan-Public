# 카페24 SSH 인프라 (테스트 전용)

본 디렉토리는 카페24 호스팅 (203.245.41.148, 1 vCPU / 1G RAM / 30G HDD) 의 **SSH 연결 검증**
script 만 보관한다. **실 배포 workflow 는 활성화되지 않은 상태이며**, 본 단계에서는 테스트만 진행한다.

## 가드 (필독)

- `.github/workflows/deploy-cafe24-ssh.yml.template` 의 `.template` suffix 는 제거되지 않은 상태이다.
  GitHub Actions 는 `.template` 파일을 workflow 로 인식하지 않으므로 자동 배포가 발생하지 않는다.
- 본 디렉토리의 script 는 **개발자 로컬에서 수동 실행**한다 (CI 자동 X).
- 카페24 호스트는 1G RAM 한계 + 기존 `samhan` 공식 홈페이지 pm2 가 운영 중이다. 추가 service 배포 전
  반드시 free / pm2 list 결과를 확인한다.

## 파일 목록

| 파일 | 용도 |
|---|---|
| `test-ssh-connection.sh` | SSH 인증 + 자원 (nproc/free/df) + 도구 (docker/pm2/nginx/node) 점검 dry-run. 실 배포 X. |

## 실행 절차

```bash
# 1. 카페24 콘솔에서 발급한 SSH key 등록 (public key 카페24 측, private key 로컬)
chmod 600 ~/.ssh/id_ed25519_cafe24

# 2. 환경변수 설정 + script 실행
chmod +x infrastructure/cafe24/test-ssh-connection.sh
CAFE24_HOST=203.245.41.148 \
CAFE24_USER=root \
CAFE24_SSH_KEY=~/.ssh/id_ed25519_cafe24 \
  ./infrastructure/cafe24/test-ssh-connection.sh
```

## 활성화 절차 (D6/D7/D8 답변 + 활성 결정 후) — **Phase 8 영구 보류**

배포 활성화는 다음 답변 항목이 확정된 후에만 진행한다:

- **D6**: 배포 대상 앱 (estimate-app v2 / order-app 정적 / 둘 다)
- **D7**: 카페24 호스트 내 배포 디렉토리 (`/home/samhan/apps/<name>` 등)
- **D8**: pm2 process 명명 규약 (`samhan-estimate-app` / `samhan-order-app` 등)

답변 + 활성 결정 후:

1. `.github/workflows/deploy-cafe24-ssh.yml.template` → `deploy-cafe24-ssh.yml` 로 rename
2. workflow 내 `TBD` placeholder 를 D6/D7/D8 답변값으로 교체
3. GitHub Secrets 등록: `CAFE24_SSH_KEY`, `CAFE24_HOST`, `CAFE24_USER`
4. `push:` 트리거 주석 해제

활성화 전까지 본 script 만 사용한다.

## D6/D7/D8 답변 = AWS 채택으로 무관 (Phase 8 영구 보류 결정)

Phase 8 호환성 가드 (`docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md`) 의 **X3 AWS 옵션 채택** (D-P8-03)
+ Phase 9 4 신규 service 포트 확정 (D-P9-01) cascade 결과, 다음과 같이 정리된다.

### 결정 (Phase 8 마무리 시점)

- **D-P7-03** (카페24 SSH 활성 보류) → **Phase 8 D-P8-03 cascade — AWS X3 채택으로 영구 보류**
- 카페24 1G RAM 한계 + 단일 노드 운영은 14 service MSA + Phase 9 신규 4 service 의 부하 흡수 불가 (총 18 service)
- AWS RDS + EC2/ECS Fargate 채택으로 카페24 의 운영 부담 (호스팅 분산) 회피

### D6/D7/D8 답변 = 무관

- **D6 (배포 대상)**: AWS ALB 가 모든 트래픽 흡수 — 카페24 측 배포 대상 = **없음**
- **D7 (디렉토리)**: AWS ECS Fargate 의 task definition 으로 격리 — 카페24 디렉토리 = **무관**
- **D8 (pm2 명명)**: AWS ECS service name 으로 통일 (`samhan-<service>-prod`) — pm2 = **사용 X**

### 본 디렉토리의 잔존 자산

- `test-ssh-connection.sh` — Phase 7 시점 SSH 연결 검증용. **Phase 10 cutover 후 archive 대상** (즉시 삭제 X — 회고 자산 보존).
- `.github/workflows/deploy-cafe24-ssh.yml.template` — `.template` suffix 영구 유지 (활성화 X).

### 카페24 호스트의 향후 용도

- 기존 `samhan` 공식 홈페이지 pm2 → 카페24 그대로 유지 (별 도메인 / 별 운영). SamhanLogis MSA 와 무관.
- SamhanLogis MSA 는 Phase 10 cutover 시점 AWS 로 일괄 이관 — 카페24 SSH workflow 는 사용되지 않는다.

상세는 `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md` § 7 (Route 53 DNS cutover) 참조.
