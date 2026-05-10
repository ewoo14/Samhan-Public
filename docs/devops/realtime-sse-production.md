# Realtime SSE — Production 적용 가이드 (PR-H1)

> Phase 12 Step 1 (PR-H1) — DevOps 인프라 가이드
>
> slip-service 의 Server-Sent Events (SSE) 스트림을 운영 환경에서 안정적으로
> 서비스하기 위한 reverse proxy / Load Balancer / 호스팅 설정 가이드.

## 1. 배경

Phase 12 Step 1 에서 slip-service 는 출고 슬립 상태 변경 / 알림 / 배송 진행을
실시간으로 desktop 클라이언트에 push 하기 위해 **SSE (Server-Sent Events)**
스트림을 도입한다 (`text/event-stream`). HTTP/1.1 long-lived connection 으로
유지되며, BE 측은 30초 주기로 heartbeat 이벤트를 발송하여 idle timeout 으로
인한 강제 종료를 방지한다.

**관련 환경변수**:

| 변수 | 위치 | 기본값 | 의미 |
|------|------|--------|------|
| `SAMHAN_REALTIME_HEARTBEAT_SECONDS` | slip-service | `30` | SSE keep-alive heartbeat 주기 |
| `SPRING_CLOUD_GATEWAY_HTTPCLIENT_RESPONSE_TIMEOUT` | api-gateway | `600s` | gateway → upstream 응답 타임아웃 |

**원칙**: Reverse Proxy / LB 의 idle timeout > heartbeat 주기 × 2 이상.

---

## 2. nginx — SSE 호환 config snippet

cafe24 호스팅 또는 자체 nginx (Phase 11 AWS 단일 EC2) 사용 시 적용.

```nginx
# /etc/nginx/conf.d/samhan-realtime.conf (예시)
location /api/v1/realtime/ {
    proxy_pass         http://api-gateway-upstream;
    proxy_http_version 1.1;

    # --- SSE 필수 ---
    proxy_buffering          off;       # event 즉시 flush (필수)
    proxy_cache              off;       # 응답 캐싱 금지
    proxy_read_timeout       600s;      # heartbeat 30s × 다수회 보존 (10분)
    proxy_send_timeout       600s;
    proxy_connect_timeout    60s;

    # --- 헤더 보존 ---
    proxy_pass_request_headers on;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    # SSE 응답 chunked 보존 (HTTP/1.1)
    proxy_set_header Connection        "";
}
```

**검증 명령** (관리자 PC 에서 토큰 발급 후):

```bash
curl -N -H "Accept: text/event-stream" \
     -H "Authorization: Bearer ${JWT}" \
     https://api.samhan-air.com/api/v1/realtime/slips/stream
```

`event: heartbeat` 가 30초 주기로 출력되면 OK. 60초 이상 끊기면 nginx
`proxy_read_timeout` 또는 LB idle timeout 점검.

---

## 3. AWS ALB / Cloudflare 호환성

### 3-1. AWS Application Load Balancer (Phase 11 AWS 마이그 시)

- **Idle timeout**: default 60s → **600s 이상으로 상향** 필수.
  - Console: EC2 > Load Balancers > Attributes > Idle timeout.
  - CLI:
    ```bash
    aws elbv2 modify-load-balancer-attributes \
      --load-balancer-arn <arn> \
      --attributes Key=idle_timeout.timeout_seconds,Value=600
    ```
- **Sticky session 권고**: SSE 는 동일 instance 로 라우팅되어야 안정.
  - Target Group > Attributes > Stickiness 활성화.
  - duration: 1시간 (lb_cookie 방식).
- **HTTP/2**: ALB → client 는 HTTP/2 가능하지만, **SSE 는 HTTP/1.1 사용 권장**
  (gateway upstream 호환성 + buffering 회피).

### 3-2. Cloudflare (앞단 CDN)

- **Free / Pro plan**: SSE 지원 제한 (Enterprise plan 권장).
- **WebSocket / Server-Sent Events**: Cloudflare 가 자동으로 buffering 적용 →
  실시간성 저하 위험.
- **권장 운영 방식**:
  - SSE 엔드포인트 (`/api/v1/realtime/**`) 는 Cloudflare bypass (DNS only,
    proxy 비활성) 또는 별도 sub-domain (`realtime.samhan-air.com`) 분리.
  - 기타 정적 자원 + 일반 API 는 Cloudflare proxy 유지.

### 3-3. cafe24 호스팅 (현재 운영 중)

- cafe24 standard 호스팅은 nginx 기반 reverse proxy 운영.
- **SSE 호환 검증 plan** (Phase 11 cutover 전 필수):
  1. cafe24 고객지원 채널에 다음 항목 문의:
     - `proxy_buffering off` 적용 가능 여부
     - `proxy_read_timeout` 600s 상향 가능 여부
     - HTTP/1.1 chunked transfer 보존 여부
  2. staging sub-domain 에 SSE endpoint 1개 노출 후 30분 idle 테스트
     (heartbeat event 누락 여부 + 강제 종료 여부 확인).
  3. 결과에 따라:
     - **OK**: 본 가이드 §2 nginx snippet 그대로 적용.
     - **NG**: Phase 11 AWS 마이그 우선순위 상향 (SSE 운영 가능 시점이
       Phase 11 cutover 일정과 직결).

---

## 4. Phase 11 cutover 시 적용 체크리스트

- [ ] AWS ALB idle timeout 600s 적용 (위 §3-1).
- [ ] Target Group sticky session (lb_cookie, 1h) 적용.
- [ ] api-gateway 환경변수 `SPRING_CLOUD_GATEWAY_HTTPCLIENT_RESPONSE_TIMEOUT=600s`
      EC2 systemd unit 또는 Docker env 에 주입.
- [ ] slip-service 환경변수 `SAMHAN_REALTIME_HEARTBEAT_SECONDS=30` 주입.
- [ ] (Cloudflare 도입 시) SSE endpoint 별도 sub-domain 분리.
- [ ] CloudWatch 또는 Prometheus 로 SSE 동시 연결 수 + heartbeat 누락 alarm 설정.
- [ ] Health Check Lambda 의 SSE smoke test 1건 추가
      (`curl -N --max-time 35` 로 heartbeat 1회 수신 검증).

---

## 5. 트러블슈팅

| 증상 | 원인 후보 | 조치 |
|------|----------|------|
| 60초 정확히 끊김 | LB idle timeout (default 60s) | LB idle timeout 600s 상향 |
| event 가 batch 로 늦게 도착 | nginx buffering ON | `proxy_buffering off` |
| 504 Gateway Timeout | gateway HttpClient response-timeout 짧음 | `SPRING_CLOUD_GATEWAY_HTTPCLIENT_RESPONSE_TIMEOUT=600s` |
| 다중 노드에서 일부 event 누락 | sticky session 미적용 | LB stickiness 활성 |
| Cloudflare 통해 접속 시 끊김 | Cloudflare buffering | bypass / 별도 sub-domain |

---

## 관련 문서

- [`infrastructure/env-templates/api-gateway.env`](../../infrastructure/env-templates/api-gateway.env)
- [`infrastructure/env-templates/slip-service.env`](../../infrastructure/env-templates/slip-service.env)
- [`services/slip-service/src/main/resources/application.yml`](../../services/slip-service/src/main/resources/application.yml)
- Phase 11 AWS 단일 환경 결정 (DECISIONS — Phase 11 entry)
