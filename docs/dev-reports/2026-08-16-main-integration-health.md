# main 통합 건강 점검 — 2026-08-16 04:00 (머지 4건 이후)

오늘 밤 네 건이 각각 다른 base 에서 머지됐다. **PR 별 CI 는 각자 자기 base 에서 통과했고, 넷이 합쳐진 상태는 아무도 안 봤다.** 이 repo 에는 stacked PR false-green 이력이 있어 점검했다.

## 결론

```text
main 소스 자체   교차 충돌 0
배포된 컨테이너  🚨 8개가 #1226 미반영 — 실 사용자 경로에서 500 이 재현된다
```

## 빌드 — 전부 통과

```text
14개 서비스 통합 재컴파일   BUILD SUCCESSFUL in 1m 41s · 98 tasks · exit 0
desktop 실제 번들           770 modules transformed · built in 10.56s · exit 0
```

## 마이그레이션 3중 확인 — 충돌 0

```text
소스 migration 파일        456
고유 서비스별 버전          456
라이브 DB 적용 성공 버전    456
소스 중복 번호 0 · DB 중복 번호 0
```

서비스별 `파일/DB 적용` 이 전부 일치한다 (auth 107/107 · slip 83/83 · accounting 77/77 · product 43/43 · inventory 28/28 · arologis 25/25 · groupware 21/21 · partner-order 17/17 · partner 14/14 · user 14/14 · notification 11/11 · dashboard 8/8 · dc-config 5/5 · partner-auth 3/3).

## 🚨 실 사용자 경로 결함 — 배포 미반영

로그인 JWT 로 게이트웨이를 통과한 **명백한 미존재 경로**가 500 을 준다.

```json
{"success":false,"code":"INTERNAL_ERROR","message":"서버 내부 오류가 발생했습니다.","data":null}
```

```text
500 재현   auth · dashboard · inventory · notification · partner · product · slip
반영됨     accounting · arologis · dc-config · groupware · partner-order · user
```

🔑 **#1226 은 머지됐지만 컨테이너가 옛 브랜치 빌드다.** 밤새 트랙들이 서로의 서비스를 자기 브랜치로 덮어쓴 결과다.
⟹ PM 이 8개를 main 으로 재배포한다.

🚩 partner-auth 는 직원 JWT 경로에서 인증이 먼저 401 을 반환해 사용자 경로 관측이 불가했다. 직접 attested 요청에서는 500 이고 JAR marker 도 없다.

## 교차 표면 — 우려했던 조합은 성립하지 않았다

`#1226` 의 404 advice 가 `#1225` 의 새 DTO 검증 실패를 404 로 덮는가?

```text
POST /slips {}
HTTP/1.1 400
{"success":false,"code":"INVALID_INPUT","message":"slipType: must not be null"}
```

**덮지 않는다.** 저장 로직·DB write 전에 400 으로 끝난다.

```text
slip 404 handler + 가입고 생성 2경로     BUILD SUCCESSFUL
notification V11 migration contract      BUILD SUCCESSFUL
desktop #1222 + #1225 공유 표면          6 files · 118 tests passed
```

`#1224` 의 V11 은 `notification_db`, 가입고 전표는 `slip_db` — 번호·스키마 충돌 없음.

## 🚩 증거 무결성 예외 2건 (검증자 자기 신고)

```text
① 검증 중 PM 이 docs-only 커밋 2개를 밀어 HEAD 가 c6aea2666 → 1e4c683c2 로 바뀌었다
   차이는 문서 3개뿐이고 제품 코드 변경 0
② npm run typecheck 는 컴파일 후 증거 집합 가드에서 exit 1
   원인: .gitignore 로 커버되지 않는 기존 미추적 스펙 1개
   order-approval-real-qa.spec.ts — 제품 결함 아님
```

## Desktop 실제 기동

```text
ELECTRON_PID=94612 · CWD_IN_COMMAND=YES
RENDERER_ROUTE=…/index.html#/purchases/new/inbound-xlsx
PAGE_TESTID=VISIBLE
종료 후 ALIVE=NO · ELECTRON_WITH_CWD_COUNT=0
```
