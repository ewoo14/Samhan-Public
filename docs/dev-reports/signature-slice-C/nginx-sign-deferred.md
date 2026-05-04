# sign.samhan-air.com nginx 라우팅 — Phase 5 Deferred

> Plan §7 Q8 결정에 따라 본 Slice C 는 desktop 앱 안 mock 라우트만 시연하고
> sign.samhan-air.com nginx 분리는 Phase 5 (web app 슬라이스) 에서 일괄 처리합니다.

## 1. 현 슬라이스 (Slice C) 처리

- 모바일 mini bundle 은 desktop Electron 안에서 `app://` 또는 `http://localhost:5173/mobile/...`
  로 시연 (개발/QA 환경)
- 실제 sign.samhan-air.com 서브도메인은 DNS 만 등록 (`project_domain_strategy.md`)
  되어 있고 nginx 라우팅 X → 본 슬라이스 진입 시 404
- API Gateway `/api/public/**` 라우트는 이미 활성 (Slice B) → API 호출은 정상

## 2. Phase 5 진입 시 작업 (deferred)

신규 파일 후보: `infrastructure/nginx/conf.d/sign.conf`

```nginx
server {
    listen 443 ssl http2;
    server_name sign.samhan-air.com;

    ssl_certificate     /etc/letsencrypt/live/sign.samhan-air.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sign.samhan-air.com/privkey.pem;

    # 정적 mobile mini bundle (clients/mobile-public dist)
    root /var/www/sign-mobile;
    index index.html;

    # /d/{token} 및 /d/{token}/s/{slipNo} SPA fallback
    location /d/ {
        try_files $uri /index.html;
    }

    # /share/{shareToken} 인수자 view
    location /share/ {
        try_files $uri /index.html;
    }

    # API 프록시 — 모든 공개 엔드포인트는 api-gateway 경유
    location /public/ {
        proxy_pass http://api-gateway:8080/api/public/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    # 보안 헤더 (api-gateway 의 PublicSecurityHeaderFilter 와 이중 적용)
    add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header X-Robots-Tag "noindex, nofollow" always;
}

server {
    listen 80;
    server_name sign.samhan-air.com;
    return 301 https://$host$request_uri;
}
```

## 3. Phase 5 체크리스트

- [ ] mobile mini bundle build → `clients/mobile-public/dist/` 산출
- [ ] Let's Encrypt 발급 (`certbot --nginx -d sign.samhan-air.com`)
- [ ] DNS A record sign.samhan-air.com → infra IP
- [ ] nginx reload + smoke test
- [ ] api-gateway PublicSecurityHeaderFilter 와 nginx `add_header` 중복 검증 (nginx 가 우선)
- [ ] `noindex` Google Search Console 검증
