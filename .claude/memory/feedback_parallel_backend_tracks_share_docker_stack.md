---
name: feedback-parallel-backend-tracks-share-docker-stack
description: services/** 를 건드리는 트랙은 병렬로 돌릴 수 없다 — Docker 이미지가 서로 덮이고 공유 DB 가 상대 검증을 오염시킨다 (2026-07-29 #984↔#985 실측)
metadata:
  type: feedback
---

# 🚨 백엔드 트랙은 직렬화 — 병렬 트랙이 서로의 이미지를 덮는다

**2026-07-29 실측.** PM 이 #984(이카운트 임포트)와 #985(주문 확정 단가)를 병렬로 돌리며 **양쪽 브리핑에 `product-service` 재빌드를 지시**했다. 두 워크트리는 소스가 다른데 **Docker 스택과 DB 는 하나**다.

```text
15:13~15:14  #984 트랙이 t9-984 소스로 product-service 재빌드   (R4 lineage 포함)
15:15        #984 임포트 2회 HTTP 200 → 726건 전수 diff 0
     ↓
15:23:46     #985 트랙이 t8-985 소스로 product-service 재빌드    ← R4 없음. 덮어씀
15:24:04     컨테이너 재기동
15:25:03     PM 3회차 임포트 → 422 MIG2_NO_MAIN_CANDIDATE
```

PM 이 결과를 독립 재현하려다 422 를 받고서야 알았다. **검증 결과가 틀린 게 아니라 검증 환경이 파괴된 것**이었다.

## 왜 안 보이는가

각 트랙은 자기 워크트리 안에서만 산다고 착각하기 쉽다. **워크트리는 격리되지만 Docker 데몬·이미지 태그·DB 는 전역**이다. `infrastructure-<svc>:latest` 는 단 하나뿐이라 나중에 빌드한 트랙이 조용히 이긴다. 로그에도 에러가 안 난다 — 그냥 다른 코드가 돌 뿐이다.

DB 는 더 나쁘다. #984 의 임포트가 `products` **2,655행을 갱신**했고, 그 시각에 #985 는 같은 DB 로 부트스트랩↔확정 대조를 돌리고 있었다. 대조 **사이**에 갱신이 끼었다면 표시와 저장이 서로 다른 카탈로그를 본 것이라 **판정 자체가 무효**다.

## 적용

- **`services/**` 를 건드리는 트랙은 동시에 둘 이상 돌리지 않는다.** 프론트 전용·문서·스크립트 트랙만 병렬 허용.
- 병렬 트랙 브리핑에는 **"Docker 기동·DB 접근·`services/**` 빌드 금지 — 건드리면 다른 트랙 검증이 무효가 된다"** 를 명시한다. 경로 배타만으로는 부족하다.
- **검증 결과를 받으면 그 이미지가 아직 그 코드인지 확인**한다:
  ```bash
  docker inspect -f '{{.Created}}' infrastructure-<svc>:latest
  docker exec samhan-<svc> sh -c 'unzip -l /app/app.jar | grep -i <새심볼>'
  ```
  심볼이 없으면 그 검증은 **지금 재현되지 않는다**.
- **"당시엔 옳았다"는 게이트를 통과시키지 않는다.** 머지 게이트 ①은 재현 가능성을 요구한다. 클로버됐으면 직렬화 후 재검증한다.
- 실행 중인 Codex 는 **강제 종료하지 않는다** — MCP codex 는 중단 시 산출물이 하나도 안 남는다. 끝난 뒤 직렬로 재검증하는 편이 낫다.

관련: [[feedback_parallel_agent_gradle_shared_tree_contention]] · [[feedback_qa_live_shared_data_readonly]] · [[feedback_pm_verify_what_measurement_proves]]
