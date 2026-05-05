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
    console.log(`[estimate-app] USE_MOCK_FALLBACK=${process.env.USE_MOCK_FALLBACK || 'true'}`);
  });
}

module.exports = app;
