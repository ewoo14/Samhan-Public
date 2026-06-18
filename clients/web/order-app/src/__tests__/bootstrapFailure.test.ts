import { describe, expect, it } from 'vitest';

declare const process: { cwd: () => string };
declare function require(id: string): any;

const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const vm = require('node:vm');

function readOrderAppHtml(): string {
  return readFileSync(resolve(process.cwd(), 'index.html'), 'utf8');
}

function extractHeadBootstrapScript(html: string): string {
  const match = html.match(/<script>\s*\(function\(\)\{[\s\S]*?\}\)\(\);\s*<\/script>/);
  if (!match) throw new Error('head bootstrap script not found');
  return match[0].replace(/^<script>/, '').replace(/<\/script>$/, '');
}

function extractBodyFatalGuard(html: string): string {
  const start = html.indexOf('if (window.__SAMHAN_BOOTSTRAP_FATAL__)');
  const end = html.indexOf('/* v4: Apps Script', start);
  if (start < 0 || end < 0 || end <= start) {
    throw new Error('bootstrap fatal guard not found');
  }
  return html.slice(start, end);
}

describe('order-app bootstrap failure guard', () => {
  it('동기 bootstrap 실패 시 빈 카탈로그로 진행하지 않고 fatal 상태를 기록한다', () => {
    const context = {
      window: {},
      console: { warn() {} },
      XMLHttpRequest: class {
        status = 503;
        responseText = '';
        open() {}
        setRequestHeader() {}
        send() {}
      },
    };

    vm.createContext(context);
    vm.runInContext(extractHeadBootstrapScript(readOrderAppHtml()), context);

    expect(context.window).toMatchObject({
      __SAMHAN_BOOTSTRAP_FATAL__: true,
      __SAMHAN_BOOTSTRAP_ERROR_MESSAGE__: 'HTTP 503',
    });
    expect(context.window).not.toHaveProperty('__SAMHAN_BOOTSTRAP_PREFETCHED__');
  });

  it('fatal 상태면 legacy snapshot const 평가 전에 재시도 UI를 렌더하고 중단한다', () => {
    const body = { innerHTML: '' };
    const context = {
      window: {
        __SAMHAN_BOOTSTRAP_FATAL__: true,
        __SAMHAN_BOOTSTRAP_ERROR_MESSAGE__: 'HTTP 503',
        __SAMHAN_RENDER_BOOTSTRAP_FATAL__: () => {
          body.innerHTML =
            '<div role="alert"><h1>주문서 데이터를 불러오지 못했습니다.</h1><button>새로고침</button></div>';
        },
      },
      document: { body },
      Error,
    };

    vm.createContext(context);

    expect(() => vm.runInContext(extractBodyFatalGuard(readOrderAppHtml()), context)).toThrow(
      /order-app bootstrap failed/,
    );
    expect(body.innerHTML).toContain('주문서 데이터를 불러오지 못했습니다.');
    expect(body.innerHTML).toContain('새로고침');
  });
});
