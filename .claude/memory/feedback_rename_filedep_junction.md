---
name: rename-filedep-junction
description: 루트 폴더 rename 시 file: 내부 의존(@samhan/design-system) junction 이 구 절대경로로 깨짐 → npm install 복구
metadata:
  type: feedback
---

루트 폴더명 변경(예 `SamhanLogis` → `Samhan-Public`) 후, `package.json` 의 `"@samhan/design-system": "file:../web/design-system"` 같은 **상대경로 file: 내부 의존**은 선언 자체는 rename-safe 이나, npm 이 이미 만들어둔 `node_modules/@samhan/design-system` **junction 이 구 절대경로**(`/c/dev/SamhanLogis/...`)를 가리켜 깨진다.

- 영향 client = **desktop + arologis-desktop 만**(이 둘만 design-system 을 file: 로 의존). web 3종·mobile 3종은 내부 file: 의존 없어 rename 면역.
- 증상: 로컬 typecheck/build 에서 `Cannot find module '@samhan/design-system'`. CI/새 클론은 fresh install 이라 무영향.
- **복구**: 해당 client 디렉토리에서 `npm install` 1회 (junction 을 신 경로로 재생성).
- 소스/설정(vite/tsconfig/electron/expo)에는 구경로 하드코딩 없음 — 점검 시 `grep -r SamhanLogis clients --include=*.json --include=*.ts` 로 0건 확인.

**How to apply**: 폴더 rename 후 회사/집 PC 어느 쪽이든 desktop·arologis-desktop 에서 `npm install` 재실행. (2026-06-08 회사 PC SAMHAN9440 점검 시 arologis-desktop junction 복구 완료, desktop 은 개발책임자 선검증.) 관련 [[standalone-boot-real-qa]].
