/**
 * GET / — 종합견적서 진입.
 *
 * Apps Script `doGet()` 호환 — bootstrap data 모두 prefetch 후 EJS render.
 * legacy 는 server-side template 안에서 `<?= var ?>` / `<?!= var ?>` 로
 * 직접 출력했으므로, EJS 도 동일한 시점에 inline JSON 으로 주입한다.
 */

'use strict';

const express = require('express');
const code = require('../lib/code');

const router = express.Router();

router.get('/', async (req, res, next) => {
  try {
    const userEmail = req.query.email || process.env.DEFAULT_USER_EMAIL || 'ops@samhan-air.com';
    const bootstrap = await code.bootstrap(userEmail);
    res.render('index', bootstrap);
  } catch (e) {
    next(e);
  }
});

module.exports = router;
