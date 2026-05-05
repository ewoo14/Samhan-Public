/**
 * order-legacy — Node.js + Express + EJS bootstrap.
 *
 * Apps Script HtmlService 환경을 1:1 흉내내는 Express 서버.
 * - GET /            → views/index.ejs (legacy partner-order index.html EJS 변환본) render
 * - GET /healthz     → 헬스체크
 * - POST /rpc/:fn    → google.script.run 호환 RPC dispatch
 * - public/*         → 정적 자산
 *
 * 도메인: order.samhan-air.com (production)
 * Port: 5185 (default)
 */

'use strict';

require('dotenv').config();

const path = require('path');
const express = require('express');

const app = express();

app.set('view engine', 'ejs');
app.set('views', path.join(__dirname, 'views'));

app.use(express.json({ limit: '20mb' }));
app.use(express.urlencoded({ extended: true, limit: '20mb' }));

app.use(express.static(path.join(__dirname, 'public'), {
  maxAge: '1d',
  setHeaders(res, filePath) {
    if (filePath.endsWith('.html')) res.setHeader('Cache-Control', 'no-cache');
  },
}));

app.use('/', require('./routes/index'));
app.use('/healthz', require('./routes/healthz'));
app.use('/rpc', require('./routes/rpc'));

app.use((err, req, res, next) => { // eslint-disable-line no-unused-vars
  console.error('[express] 에러:', err);
  res.status(500).json({
    ok: false,
    error: String(err.message || err),
    stack: process.env.NODE_ENV === 'production' ? undefined : err.stack,
  });
});

const PORT = parseInt(process.env.PORT || '5185', 10);

if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`[order-legacy] listening on http://localhost:${PORT}`);
    console.log(`[order-legacy] SRC_SHEET_ID=${process.env.SRC_SHEET_ID || '(default)'}`);
    console.log(`[order-legacy] ECOUNT_ENDPOINT=${process.env.ECOUNT_ENDPOINT || '(default)'}`);
    console.log(`[order-legacy] NODE_ENV=${process.env.NODE_ENV || 'development'}`);
  });
}

module.exports = app;
