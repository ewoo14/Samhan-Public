# 카페24 SSD 가상서버 일반형 — legacy-v2 배포 가이드

대상: `quote.samhan-air.com` (estimate-legacy) + `order.samhan-air.com` (order-legacy)
호스트: 카페24 SSD 가상서버 일반형 (RAM 1G / HDD 30G / IP 203.245.41.148)
plan 출처: `docs/migration/phase-legacy-v2/M-LEGACY-V2-deployment.md`

## 0. 사전 준비

- 호스트에 docker / docker-compose 설치 확인 (samhan repo 가 이미 Postgres 컨테이너 운영 중 → 설치 검증됨)
- samhan 공식 홈페이지 server.js 정정 PR 머지 확인 (별도 repo, 옵션 X1)
- `certbot --expand` 로 SAN 인증서 발급 (samhan-air.com + www + quote + order 4 도메인 일괄)

## 1. 배포 절차

```bash
# 1) 디렉토리 생성
sudo mkdir -p /opt/samhan/legacy-v2
sudo chown -R $USER /opt/samhan/legacy-v2

# 2) repo clone or rsync (소스 코드 + .env.example 모두 업로드)
git clone https://github.com/ewoo14/SamhanLogis.git /opt/samhan/legacy-v2-src
cd /opt/samhan/legacy-v2-src/infrastructure/cafe24

# 3) .env 작성 (placeholder 모두 실 값으로 교체)
cp .env.example .env
chmod 600 .env
nano .env   # 시크릿 입력

# 4) Service Account JSON 키 업로드
sudo mkdir -p /etc/samhan
sudo chmod 700 /etc/samhan
# scp 로 sa-key.json 업로드 후
sudo chmod 600 /etc/samhan/sa-key.json

# 5) docker compose up
docker compose -f docker-compose.legacy-v2.yml up -d --build

# 6) healthz 확인
curl http://127.0.0.1:5184/healthz
curl http://127.0.0.1:5185/healthz

# 7) samhan server.js 가 quote/order 도메인을 reverse proxy 한 후
curl -I https://quote.samhan-air.com
curl -I https://order.samhan-air.com
```

## 2. 운영

- 로그: `docker compose -f docker-compose.legacy-v2.yml logs -f`
- 재시작: `docker compose -f docker-compose.legacy-v2.yml restart estimate-legacy`
- 업데이트: `docker compose pull && docker compose up -d`
- 회귀: `docker tag samhan/estimate-legacy:v1.x.x samhan/estimate-legacy:v2.0.0 && docker compose up -d`
- 시트 캐시 무효화: `curl -X POST http://127.0.0.1:5184/rpc/clearSheetCache -H 'Content-Type: application/json' -d '{"args":[]}'`

## 3. 모니터링

- UptimeRobot 5분 ping → `https://quote.samhan-air.com/healthz` + `https://order.samhan-air.com/healthz`
- `docker stats` cron 1시간 → 200MB 초과 시 알림 (Slack / 이메일)
- certbot timer (Ubuntu 기본 systemd timer) → 자동 갱신 + post-renewal hook 으로 samhan 프로세스 reload

## 4. 시크릿 정책

- Service Account JSON 키는 호스트 `/etc/samhan/sa-key.json` 에 chmod 600 으로 저장.
- Docker container 안에서 `GOOGLE_SERVICE_ACCOUNT_KEY=/etc/samhan/sa-key.json` 로 참조.
  단, 컨테이너에서 호스트 path 가 보이지 않으므로 docker-compose 에 bind mount 추가 필요:
  ```yaml
  volumes:
    - /etc/samhan/sa-key.json:/etc/samhan/sa-key.json:ro
  ```
  또는 옵션 2) `GOOGLE_SA_KEY_JSON_BASE64` 환경변수로 주입 (mount 불요).

- Notion 토큰 4종 + e-Count creds 는 .env 파일만 사용. git commit 금지.

## 5. 회귀 (Roll-back)

- 컨테이너 oom: `docker compose restart estimate-legacy` (격리되어 order 영향 X)
- 이미지 회귀: `docker tag` 으로 이전 tag 로 swap 후 `up -d`
- 전체 폐쇄: `docker compose down` + samhan server.js 의 host 분기 주석 처리
