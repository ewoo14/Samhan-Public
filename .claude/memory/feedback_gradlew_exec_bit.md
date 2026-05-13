---
name: gradlew 실행 권한 — Windows 커밋 시 git index 명시 필요
description: Windows 에서 gradlew 를 신규 커밋하면 git index 에 100644 로 들어가 Linux CI 에서 Permission denied 발생. `git update-index --chmod=+x` 필수
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: Windows 환경에서 `gradlew` (또는 다른 shebang 스크립트) 를 처음 커밋하거나 재생성할 때는 반드시 `git update-index --chmod=+x <파일>` 으로 git index 의 file mode 를 `100755` 로 설정해야 한다.

**Why**: 2026-05-04 PR #5 (post-phase2-cleanup) 의 GitHub Actions 첫 실행에서 다음 에러 발생:
```
Run ./gradlew assemble --no-daemon
/home/runner/work/_temp/.../sh: line 1: ./gradlew: Permission denied
Error: Process completed with exit code 126.
```
원인: Windows 의 NTFS 는 POSIX exec bit 개념이 없어, Windows 의 `git add gradlew` 는 file mode 를 `100644` (rw-r--r--) 로 git index 에 저장. Linux runner 가 그대로 체크아웃하면 `gradlew` 실행 권한 없음 → exit 126.

**해결 절차** (이미 사고 발생 시):
```bash
git update-index --chmod=+x gradlew
git commit -m "fix: gradlew 실행 권한 설정"
git push
```
파일 내용 변경 없이 git tree 의 mode 만 100644 → 100755 로 바뀜 (SHA 동일 유지).

**예방 절차** (앞으로 모든 PR):
- 새 shebang 스크립트(`gradlew`, `mvnw`, `*.sh` 등) 추가 시 `git ls-files --stage <파일>` 로 mode 확인
- `100644` 면 `git update-index --chmod=+x <파일>` 즉시 적용
- CI 워크플로우에 방어용 `chmod +x ./gradlew` 스텝 유지 (root cause 가 새 파일에서 재발하더라도 CI 는 살아남음)
- 가능하면 `.gitattributes` 에 `gradlew text eol=lf` + Windows Git 설정으로 mode 보존 (옵션, 본 프로젝트는 적용 안 함)

**검증 방법**:
- 로컬: `git ls-files --stage gradlew` → `100755 ...` 이면 OK
- 원격: GitHub UI 의 파일 메타데이터 또는 `gh api repos/.../contents/gradlew` 응답의 mode 필드

**관련 사고**: PR #5 머지 전 발견 (CI 실패 → 즉시 fix commit 추가 → 재push). 메모리 저장은 동일 사고 재발 방지용.
