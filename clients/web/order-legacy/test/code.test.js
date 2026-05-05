/**
 * order-legacy smoke test — lib/code.js 의 export surface + RPC dispatch 검증.
 */

'use strict';

describe('order-legacy lib/code.js', () => {
  let code;
  beforeAll(() => {
    process.env.NOTION_TOKEN_AUTH = process.env.NOTION_TOKEN_AUTH || 'dummy_for_test';
    process.env.NOTION_TOKEN_DC = process.env.NOTION_TOKEN_DC || 'dummy_for_test';
    process.env.NOTION_TOKEN_ORDER = process.env.NOTION_TOKEN_ORDER || 'dummy_for_test';
    process.env.NOTION_TOKEN_SNAPSHOT = process.env.NOTION_TOKEN_SNAPSHOT || 'dummy_for_test';
    code = require('../lib/code');
  });

  test('module loads and exports bootstrap', () => {
    expect(typeof code.bootstrap).toBe('function');
    expect(typeof code.clearSheetCache).toBe('function');
  });

  test('legacy RPC 함수가 모두 export 되어 있다 — partner-order 전용 인증 흐름 포함', () => {
    const expectedFns = [
      'doGet',
      'getHomeMulti',
      'getSingleSets',
      'getCommercialMulti',
      'sendOrderFromUi',
      'saveOrderToNotion',
      'fetchNotionDcConfig_',
      'getOrderHistory',
      'saveOrderSnapshot',
      'getOrderSnapshotHistory',
      // 인증
      'checkAuthStatus',
      'requestAuthApproval',
      'setAuthPassword',
      'tryLogin',
      'queryAuthDb_',
      'getAccessExpiration',
      'saveTutorialState',
      'logActionToNotion',
      'logFrontEvent',
    ];
    for (const fn of expectedFns) {
      expect(typeof code[fn]).toBe('function');
    }
  });

  test('hashPassword_ 가 SHA-256 hex 64 char 반환', () => {
    const h = code.hashPassword_('test1234');
    expect(h).toMatch(/^[0-9a-f]{64}$/);
  });
});
