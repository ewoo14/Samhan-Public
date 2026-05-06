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

## 활성화 절차 (D6/D7/D8 답변 + 활성 결정 후)

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
