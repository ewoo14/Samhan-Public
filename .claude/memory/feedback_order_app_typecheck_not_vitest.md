---
name: feedback_order_app_typecheck_not_vitest
description: order-app(및 tsc strict FE) CI는 typecheck(tsc --noEmit) 포함 — vitest는 tsc 미실행이라 로컬 npm run typecheck 별도 확인 의무
metadata:
  type: feedback
---

🚨 order-app CI 잡 "Frontend Order-App (typecheck + test + build)"은 `npm run typecheck`(= `tsc -p tsconfig.json --noEmit`)를 test/build 앞단에 실행한다. **vitest(esbuild 기반)는 타입체크를 하지 않으므로** 로컬에서 `npx vitest run`만 돌리면 타입 에러를 못 잡고 CI에서 red 난다.

- 실증(2026-07-11 #778 item2): order-app 테스트 `explode*()[N].price` 접근이 tsconfig `noUncheckedIndexedAccess`로 `TS2532 Object is possibly 'undefined'` → **vitest 8 pass인데 CI typecheck FAIL**. 소급 `[N]!.price` non-null 단언 fix(`b7e29ca8a`). 원인 커밋은 구현(3f263021a)부터였고 vitest만 돌려 R1 fix까지 미포착.
- **규칙**: order-app(및 strict/noUncheckedIndexedAccess tsconfig FE) 변경 push 전 **로컬 `npm run typecheck`(cwd=해당 앱)를 vitest와 함께 실행**. desktop 타입검증은 [[feedback_desktop_typecheck_command]] 참조.
