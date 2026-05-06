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

const app = express();

// EJS view engine
app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

// 보안 헤더 (Phase 7 2차 DevOps) — order-app 의 _headers 와 동일 정책.
// HSTS / CSP / X-Frame-Options / Referrer-Policy / X-Content-Type-Options / Permissions-Policy.
app.use((req, res, next) => {
  res.setHeader('Strict-Transport-Security', 'max-age=63072000; includeSubDomains; preload');
  res.setHeader(
    'Content-Security-Policy',
    [
      "default-src 'self'",
      "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data: https:",
      "font-src 'self' data: https:",
      "connect-src 'self' https://*.samhan-air.com",
      "frame-ancestors 'self'",
      "base-uri 'self'",
      "form-action 'self'",
    ].join('; '),
  );
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('X-Content-Type-Options', 'nosniff');
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
