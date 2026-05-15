# D-AX-22 Team 1 TM 통합 리뷰

## 결론

D-AX-22 는 driver-facing 경계에서 내부 UUID/원본 URL/저장키가 보이지 않도록 API, 모바일 normalize, 데스크톱 typecheck, QA 문서를 함께 묶은 통합 hardening PR 로 진행한다.

## 5-agent 검토 반영

- Backend: GPS 위치 row key, 서명 내부키, sign-and-send-copy 성공 header, `sourceWarehouseName` UUID fallback 을 차단했다.
- Frontend: `clients/arologis-mobile` API 계층에서 내부 필드를 반환 타입에서 제거하고 Jest/typecheck 회귀 테스트를 추가했다.
- Designer: PR 캡처는 계약/화면/실패/검증 매트릭스 8장으로 나눠, 작은 화면에서도 보이도록 1200px 폭으로 생성한다.
- DevOps: workflow 변경은 최소화하고 Docker TCP 환경에서 backend 전체 테스트를 hard gate 로 둔다.
- QA: 성공 경로뿐 아니라 인수자 번호 없음, renderer timeout, bridge 실패, duplicate 계열에서 내부키/저장 경로가 없는지 body scan 으로 검증한다.

## 사용자 최신 결정 반영

전표번호는 다른 서비스/메뉴/업무 속성 사이에서 중복되어도 된다. 판매전표 `YYYY/MM/DD-1` 과 구매전표 `YYYY/MM/DD-1` 은 동일 문자열이어도 서로 다른 메뉴값과 업무 타입을 가지므로 충돌이 아니다. D-AX22 문서와 QA 기준은 이 원칙을 전제로 UUID PK 를 내부 정합성용으로만 다룬다.

## 남은 후속 후보

- Samhan Public 거래처 생성/관리 UI gap 점검.
- comments/audit/SSE proxy 확장.
- 실제 기기 QA.
- Testcontainers no-skip hardening.
