# public/fonts — Pretendard self-host asset 디렉토리

본 디렉토리는 Pretendard web font 의 woff2 asset 을 호스팅한다.

## 받는 방법

저장소 root 에서:

```bash
bash scripts/download-pretendard-fonts.sh
```

받는 파일 (총 3개):

- `Pretendard-Regular.woff2`
- `Pretendard-Bold.woff2`
- `PretendardVariable.woff2`

## 왜 self-host 인가

- jsdelivr CDN SPOF 회피 (장애 시 FOUC + 한글 폰트 깨짐)
- CSP `font-src 'self'` 만 허용 가능 (외부 도메인 의존 제거)
- 운영 환경의 외부 네트워크 출구 차단 정책 호환

## 적용 위치

- `views/index.ejs` head 의 `<link rel="stylesheet" href="/fonts/fonts.css">` (Express static)
- `clients/web/design-system/src/styles/fonts.css` 와 동일 정책

## .gitignore

woff2 binary 는 저장소에 commit 하지 않는다 (저장소 크기 + 저작권 mirroring 회피).
CI / 로컬 dev / production build 에서 download script 사전 실행 의무.
