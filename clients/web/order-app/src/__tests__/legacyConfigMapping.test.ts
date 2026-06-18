import { describe, expect, it } from 'vitest';

declare const process: { cwd: () => string };
declare function require(id: string): any;

const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const vm = require('node:vm');

function loadConfigRuntime() {
  const html = readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');
  const start = html.indexOf('function configNumber');
  const end = html.indexOf('// 고정DC 파싱');
  if (start < 0 || end < 0 || end <= start) {
    throw new Error('legacy config mapping block not found');
  }

  const context = {
    window: {} as Record<string, unknown>,
    CONFIG: {
      homeDiscount: 0.45,
      commDiscount: 0.45,
      showIHose: false,
      discount360: 0,
      discount4way: 0,
      discountStand: 0,
      oneWayDiscount: 0,
      deluxeDiscount: 0,
      firstGradeDiscount: 0,
      unitRoundTo: 0,
      unitRoundMode: 'ROUND',
    },
  };
  vm.createContext(context);
  vm.runInContext(`${html.slice(start, end)}; globalThis.__CONFIG__ = () => CONFIG;`, context);
  return context as typeof context & {
    applyConfigFromServer: (cfg: unknown) => void;
    __CONFIG__: () => Record<string, unknown>;
  };
}

describe('legacy order-app DC config mapping', () => {
  it('로그인 응답 nested config.dc 를 legacy 평면 CONFIG 와 전역 단가 변수로 매핑한다', () => {
    const runtime = loadConfigRuntime();

    runtime.applyConfigFromServer({
      partnerCode: 'P-001',
      dc: {
        homeDiscountRate: 0.48,
        commercialDiscountRate: 0.49,
        discount360Amount: 60000,
        discount4WayAmount: 60000,
        discount1WayAmount: 50000,
        discountStandAmount: 60000,
        discountDeluxeAmount: 70000,
        discountFirstGradeAmount: 80000,
        showIHose: true,
        unitRoundTo: 100,
        unitRoundMode: 'ROUND',
      },
    });

    expect(runtime.window).toMatchObject({
      DISCOUNT_RATE_HOME: 0.48,
      DISCOUNT_RATE_COMM: 0.49,
      DISCOUNT_360_AMT: 60000,
      DISCOUNT_4WAY_AMT: 60000,
      ONEWAY_DISCOUNT_AMT: 50000,
      DISCOUNT_STAND_AMT: 60000,
      DELUXE_DISCOUNT_AMT: 70000,
      FIRSTGRADE_DISCOUNT_AMT: 80000,
      SHOW_I_HOSE: true,
      UNIT_ROUND_TO: 100,
      UNIT_ROUND_MODE: 'ROUND',
    });

    expect(runtime.__CONFIG__()).toMatchObject({
      homeDiscount: 0.48,
      commDiscount: 0.49,
      discount360: 60000,
      discount4way: 60000,
      oneWayDiscount: 50000,
      discountStand: 60000,
      deluxeDiscount: 70000,
      firstGradeDiscount: 80000,
      unitRoundTo: 100,
      unitRoundMode: 'ROUND',
    });
  });

  it('과거 퍼센트 정수 입력은 48% = 0.48 로 보정한다', () => {
    const runtime = loadConfigRuntime();

    runtime.applyConfigFromServer({
      dc: {
        homeDiscountRate: 48,
        commercialDiscountRate: 49,
      },
    });

    expect(runtime.window.DISCOUNT_RATE_HOME).toBe(0.48);
    expect(runtime.window.DISCOUNT_RATE_COMM).toBe(0.49);
  });
});
