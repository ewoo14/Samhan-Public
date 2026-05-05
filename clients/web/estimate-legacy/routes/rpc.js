/**
 * google.script.run RPC 호환 endpoint.
 *
 * legacy index.html 은 다음 패턴으로 백엔드 함수 호출:
 *   google.script.run
 *     .withSuccessHandler(cb)
 *     .withFailureHandler(cb)
 *     .fnName(args)
 *
 * EJS 안의 client-side shim (views/index.ejs 끝부분) 이 위 패턴을 가로채
 * 본 endpoint 로 fetch POST → `POST /rpc/:fnName` body=`{ args: [...] }`.
 *
 * 본 라우터는 lib/code.js 의 export 된 함수를 dispatch 하고 결과를 JSON 응답.
 */

'use strict';

const express = require('express');
const code = require('../lib/code');

const router = express.Router();

router.post('/:fnName', async (req, res) => {
  const fnName = req.params.fnName;
  const args = Array.isArray(req.body && req.body.args) ? req.body.args : [];

  const fn = code[fnName];
  if (typeof fn !== 'function') {
    return res.status(404).json({
      ok: false,
      error: `Unknown RPC: ${fnName}`,
      available: Object.keys(code).filter((k) => typeof code[k] === 'function'),
    });
  }

  try {
    const result = await Promise.resolve(fn.apply(null, args));
    return res.json({ ok: true, result });
  } catch (err) {
    console.error(`[rpc] ${fnName} 에러:`, err);
    return res.status(500).json({ ok: false, error: String(err.message || err) });
  }
});

module.exports = router;
