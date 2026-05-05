/**
 * GET /healthz — Liveness + dependency probe.
 *
 * Docker HEALTHCHECK 와 UptimeRobot ping 양쪽이 사용.
 * 200 OK 시 정상, 503 시 의존성 중 하나라도 실패.
 */

'use strict';

const express = require('express');
const sheets = require('../lib/google-sheets-client');
const ecount = require('../lib/ecount-client');
const notion = require('../lib/notion-client');

const router = express.Router();

router.get('/', (req, res) => {
  const sheetsHealth = sheets.healthz();
  const ecountHealth = ecount.healthz();
  const notionHealth = notion.healthz();
  const ok = sheetsHealth.ok !== false && ecountHealth.ok !== false && notionHealth.ok !== false;
  res
    .status(ok ? 200 : 503)
    .json({
      ok,
      app: 'estimate-legacy',
      version: '2.0.0',
      uptime: process.uptime(),
      sheets: sheetsHealth,
      ecount: ecountHealth,
      notion: notionHealth,
      env: process.env.NODE_ENV || 'development',
    });
});

module.exports = router;
