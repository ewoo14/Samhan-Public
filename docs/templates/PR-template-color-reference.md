# PR template color reference — Google Material baseline

> **Phase 9 W4+ baseline (W3 Designer backlog #3 채택)**. 모든 신규 PR 의 QA HTML matrix
> (`docs/qa/<slug>/3-api-endpoints-summary.html` 등) 의 method 컬러 + badge 컬러는 본 표준을 1:1 복제한다.

---

## 1. HTTP method 컬러 (Google Material)

| Method | Hex | 용도 |
|---|---|---|
| GET | `#0f9d58` | 조회 (Material Green 600) |
| POST | `#1a73e8` | 생성 / 트리거 (Material Blue 700) |
| PUT | `#f9ab00` | 부분 갱신 / 멱등 (Material Yellow 700) |
| DELETE | `#d93025` | 삭제 (Material Red 600) |

---

## 2. Status badge 컬러

| Class | 의미 | Hex (background) |
|---|---|---|
| `b-ok` | 정상 / 성공 | `#34a853` (Material Green) |
| `b-warn` | 주의 / 권한 필요 | `#f9ab00` (Material Yellow) |
| `b-info` | 안내 / Internal | `#1a73e8` (Material Blue) |

---

## 3. 3 channel badge 토큰 (Phase 9 W4 신규 — W3 Designer backlog #2 채택)

| Class | 채널 | Hex (background) |
|---|---|---|
| `b-channel-push` | PUSH (FCM / APNs) | `#4285f4` (Google Blue) |
| `b-channel-email` | EMAIL (SES) | `#ea4335` (Google Red) |
| `b-channel-sms` | SMS (Aligo / Solapi) | `#34a853` (Google Green) |

본 토큰은 `clients/web/design-system/src/tokens/tokens.css` 에서 정식 export.

---

## 4. 슬레이트 / dark theme 팔레트 (W3 baseline 일관)

| Token | Hex | 용도 |
|---|---|---|
| `--slate-50` | `#f8fafc` | 배경 (light) |
| `--slate-200` | `#e2e8f0` | border / divider |
| `--slate-700` | `#334155` | text primary (dark on light) |
| `--slate-900` | `#0f172a` | 배경 (dark) |

---

## 5. 적용 예시 — QA HTML matrix

```html
<style>
  .m-get    { background: #0f9d58; color: #fff; }
  .m-post   { background: #1a73e8; color: #fff; }
  .m-put    { background: #f9ab00; color: #fff; }
  .m-delete { background: #d93025; color: #fff; }
  .b-channel-push  { background: #4285f4; color: #fff; }
  .b-channel-email { background: #ea4335; color: #fff; }
  .b-channel-sms   { background: #34a853; color: #fff; }
</style>
```

---

## 6. 출처 / 회고

- W3 (notification-service) Designer 권고 — `3-api-endpoints-summary.html` 슬레이트 + Google method 컬러 적용
- W3 reviewer 토론 backlog #2 (channel badge) + #3 (W4+ baseline 갱신) 본 W4 PR 시점 채택
- 향후 모든 PR 의 QA HTML 은 본 컬러 표를 reference 로 1:1 복제 의무
