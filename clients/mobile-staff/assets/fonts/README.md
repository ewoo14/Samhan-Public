# Pretendard self-host (Phase 10 W10-3)

> Designer-2 채택 (사용자 결정 2026-05-07) — jsdelivr CDN 회피 + 정식 도입.

본 디렉토리는 Pretendard OTF 9 weight 의 정식 배치 위치다. 본 PR (W10-3) 진입 시점은 graceful guard
방식으로 OTF 미배치 환경에서도 RN UI 가 차단되지 않도록 `usePretendardFontGuarded()` 가 처리.

## 정식 배치 (운영 시점 의무)

```
clients/mobile-staff/assets/fonts/
├── Pretendard-Thin.otf       (100)
├── Pretendard-ExtraLight.otf (200)
├── Pretendard-Light.otf      (300)
├── Pretendard-Regular.otf    (400)
├── Pretendard-Medium.otf     (500)
├── Pretendard-SemiBold.otf   (600)
├── Pretendard-Bold.otf       (700)
├── Pretendard-ExtraBold.otf  (800)
└── Pretendard-Black.otf      (900)
```

본 PR (W10-3) 시점 의무 weight = 4 종 (Regular / Medium / SemiBold / Bold). 운영 시점 9 weight 전체 배치
권장 (다국어 + 인쇄 양식 호환).

## 출처

- 공식 GitHub release: https://github.com/orioncactus/pretendard/releases
- 라이센스: SIL Open Font License 1.1 (자유 사용)

## 후속

- 정식 OTF 배치는 별도 asset 슬라이스 (W10-3 후속 fix 가능) 또는 W10-4 slip-service 통합 시점에
  일괄 배치. 본 PR 은 graceful guard + `app.json` plugin 등록 + `usePretendardFontGuarded()` 의 try/catch
  fallback 으로 OTF 미배치 환경 보호.
