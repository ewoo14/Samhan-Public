/**
 * estimate-app v2 — Node.js + Express + EJS bootstrap.
 *
 * Apps Script HtmlService 환경을 1:1 흉내내는 Express 서버.
 * - GET /            → views/index.ejs (legacy index.html 변환본) render
 * - GET /healthz     → 헬스체크
 * - POST /rpc/:fn    → google.script.run 호환 RPC dispatch
 * - public/*         → 정적 자산 (logo / font / stamp / samhan 인감 등)
 */

'use strict';

require('dotenv').config();

const path = require('path');
const express = require('express');
const helmet = require('helmet');

const app = express();

// EJS view engine
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// 보안 헤더 (Phase 7 5/6차 정식 — helmet middleware 도입).
//
// Phase 7 2~3차 의 inline CSP middleware → helmet contentSecurityPolicy directive 로 정식화.
// 효과:
//   - HSTS / CSP / X-Frame-Options / Referrer-Policy / X-Content-Type-Options /
//     Permissions-Policy / X-DNS-Prefetch-Control / X-Download-Options / X-Permitted-Cross-Domain-Policies
//     까지 helmet 기본 묶음 적용 (이전 inline middleware 보다 보강).
//   - CSP directive 는 order-app 의 _headers (Cloudflare Pages) 와 1:1 정합.
//   - script-src: 카카오 우편번호 (t1.kakaocdn.net) + html2canvas/jspdf (cdnjs.cloudflare.com).
//   - font-src 'self' data: — Phase 7 5/6차 self-host @font-face 로 외부 도메인 의존 제거.
//   - connect-src: dev 에서는 localhost API 호출 허용, production 은 *.samhan-air.com 만.
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: [
        "'self'",
        "'unsafe-inline'",
        "'unsafe-eval'",
        'https://t1.kakaocdn.net',
        'https://cdnjs.cloudflare.com',
      ],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", 'data:', 'https:'],
      fontSrc: ["'self'", 'data:'],
      connectSrc: process.env.NODE_ENV === 'production'
        ? ["'self'", 'https://*.samhan-air.com']
        : ["'self'", 'https://*.samhan-air.com', 'http://localhost:*', 'http://127.0.0.1:*'],
      frameAncestors: ["'self'"],
      baseUri: ["'self'"],
      formAction: ["'self'"],
    },
  },
  hsts: { maxAge: 63072000, includeSubDomains: true, preload: true },
  referrerPolicy: { policy: 'strict-origin-when-cross-origin' },
  frameguard: { action: 'sameorigin' },
  // helmet 기본값 외 추가 헤더 — 인쇄 / 카메라 / 마이크 / 위치 정보 권한 차단.
  crossOriginEmbedderPolicy: false, // legacy 외부 script (카카오/cdnjs) 호환
}));

// helmet 이 처리하지 않는 잔여 헤더 (Permissions-Policy 는 5.x 부터 지원).
app.use((req, res, next) => {
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  next();
});

// JSON body
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// 정적 자산
app.use(express.static(path.join(__dirname, 'public'), {
  maxAge: '1d',
  setHeaders(res, filePath) {
    if (filePath.endsWith('.html')) res.setHeader('Cache-Control', 'no-cache');
  },
}));

// 라우터
app.use('/', require('./routes/index'));
app.use('/rpc', require('./routes/rpc'));

// 에러 핸들러
app.use((err, req, res, next) => { // eslint-disable-line no-unused-vars
  console.error('[express] 에러:', err);
  res.status(500).json({
    ok: false,
    error: String(err.message || err),
    stack: process.env.NODE_ENV === 'production' ? undefined : err.stack,
  });
});

const PORT = parseInt(process.env.PORT || '5183', 10);

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`[estimate-app] v2 listening on http://localhost:${PORT}`);
    console.log(`[estimate-app] SAMHAN_API_BASE_URL=${process.env.SAMHAN_API_BASE_URL || '(default)'}`);
    console.log(`[estimate-app] SLIP_SERVICE_URL=${process.env.SLIP_SERVICE_URL || '(default :8086)'}`);
  });
}

module.exports = app;
