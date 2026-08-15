---
name: feedback-credentials-only-in-volatile-shell-env
description: "재배포가 자꾸 깨지면 자격 값이 어디에도 영속되지 않고 휘발성 셸 환경에만 있는지 봐라 — 화면에는 \"권한 없음\"으로 보인다"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: c912e540-6b1a-48d7-a602-a64c7fa3e6ca
  modified: 2026-08-15T17:35:41.623Z
---

2026-08-16 실측. **한 밤에 같은 뿌리로 두 번 깨졌다.**

```text
notification-service  재배포 → SAMHAN_GATEWAY_ATTESTATION 이 비어 기동 실패
                      IllegalStateException: ... is required when enforcement is enabled
arologis-service      재배포 → SAMHAN_INTERNAL_TOKEN 만 혼자 다른 값(48자 vs 28자)
                      ⟹ 권한 조회 401 "내부 인증 토큰이 유효하지 않습니다"
                      ⟹ 화면에는 권한 조회 실패 후 로그인 화면 복귀
```

## 왜 안 보이나

`infrastructure/.env.local` 에 **그 키 자체가 없었다.** 살아 있던 컨테이너들은
과거 어느 PowerShell 세션에서 `$env:` 로 export 한 값을 받아 만들어진 것이었고,
**셸 상태는 툴 호출 사이에 남지 않는다.** 그래서

```text
지금 도는 컨테이너   값이 있다 (생성 시점에 받았으므로)
지금 재배포하면      값이 없다
```

`docker exec <c> printenv <KEY>` 로 **살아 있는 컨테이너에서 회수**할 수 있다.
값을 출력하지 말고 `sha256sum | cut -c1-8` 로만 비교하라.

## How to apply

- 재배포가 기동 실패·401 로 끝나면 **코드보다 먼저 env 키 존재를 세라**
  ```bash
  cut -d= -f1 infrastructure/.env.local | grep -i <키>
  ```
- 키가 없으면 살아 있는 다른 컨테이너에서 회수해 **`.env.local` 에 영속화**하고
  **모든 워크트리에 복사**한다 (gitignored — `git check-ignore -v` 로 확인)
- mesh 자격은 **전수로 비교**하라. 하나만 달라도 그 서비스만 조용히 죽는다
  ```bash
  for c in ...; do docker exec $c printenv KEY | sha256sum | cut -c1-8; done
  ```
- 🚩 자격 원천을 바꾸는 PR(하드코딩 → `${VAR:?required}`)은 **14개를 함께 재배포**해야 한다.
  반쪽 재배포는 mesh 를 깨고, 그 증상은 "권한 없음" 으로 위장한다

관련: [[feedback_permission_denied_may_be_401_from_auth]] ·
[[feedback_shared_service_redeploy_breaks_other_track_qa]] ·
[[feedback_worktree_missing_gitignored_inputs]] · [[feedback_stale_deployment_looks_like_defect]]
