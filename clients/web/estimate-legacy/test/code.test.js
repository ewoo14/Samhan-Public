/**
 * estimate-legacy smoke test — lib/code.js 의 export surface 와 RPC dispatch 검증.
 */

'use strict';

describe('estimate-legacy lib/code.js', () => {
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

  test('legacy RPC 함수가 모두 export 되어 있다', () => {
    const expectedFns = [
      'doGet',
      'getHomeMulti',
      'getSingleSets',
      'getSingleParts',
      'getCommercialMulti',
      'getCommercialParts',
      'getHomeDefaults',
      'getSingleDefaults',
      'getRecommendOduData',
      'checkUserAuth',
      'sendOrderFromUi',
      'saveOrderToNotion',
      'fetchNotionDcConfig_',
      'searchCustomerByBizno',
      'getNotionHistory',
      'logFrontEvent',
      'getInventoryTable',
      'saveQuoteSnapshot',
      'getQuoteHistory',
    ];
    for (const fn of expectedFns) {
      expect(typeof code[fn]).toBe('function');
    }
  });

  test('pure utility 함수는 동기 + 동작 검증', () => {
    expect(code.normalizeSize_('1,000평')).toBe('1000');
    expect(code.parseKRNumber_('1,234')).toBe(1234);
    expect(code.todayYMD_()).toMatch(/^\d{8}$/);
  });
});
