---
name: PowerShell 파일 쓰기 인코딩 트랩 — UTF-16 LE BOM 기본값
description: PowerShell 의 Set-Content/Out-File 기본 인코딩이 UTF-16 LE BOM 이라 한글 본문이 gh CLI/도구에서 깨짐. body-file 은 Write/Edit tool 만 사용
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: SamhanLogis 의 GitHub Issue/PR/Comment **본문 파일은 절대 PowerShell 의 `Set-Content` / `Out-File` 으로 쓰지 말 것**. 반드시 다음 중 하나 사용:
1. **Write tool** — UTF-8 으로 저장 (안전)
2. **Edit tool** — 기존 파일 일부 치환 시 UTF-8 보존
3. **Bash heredoc** (`cat > file <<'EOF' ... EOF`) — UTF-8

PowerShell 에서 굳이 써야 한다면 **반드시 `-Encoding utf8` 옵션 명시**:
```powershell
... | Set-Content -Encoding utf8 path.md
... | Out-File -Encoding utf8 path.md
```

**Why**: 2026-05-04 PR #7 (Product Service BE) 의 본문에서 한글이 모조리 깨짐. 원인:
- Issue 번호를 본문에 치환하기 위해 PowerShell 으로 `(Get-Content ...) -replace '<ISSUE_NUM>', $n | Set-Content ...` 실행
- PowerShell 5.1 의 기본 file write 인코딩은 **UTF-16 LE with BOM**
- `gh pr edit --body-file` 가 BOM-prefixed UTF-16 파일을 잘못 해석 → 한글 모조리 mojibake (예: `결재` → `결재 ?�계`)

비교 (같은 PR 의 다른 부분):
- TM/PM 코멘트 (Write tool 작성 → bash 의 `gh pr comment --body-file`): 정상 한글
- Issue 본문 (Write tool 작성 → 그대로 `gh issue create --body-file`): 정상 한글
- **PR 본문**: PowerShell 으로 한 번 재작성 → 깨짐

**적용 절차** (앞으로 모든 PR/Issue/Comment 작성):
1. Write tool 로 markdown 본문 작성 (`.tmp_xxx.md`)
2. 동적 값(Issue 번호 등) 치환 필요 시 **Edit tool** 사용 (UTF-8 보존)
3. PowerShell 또는 bash 의 `gh ... --body-file` 호출 (파일 읽기는 안전)
4. 절대 PowerShell pipe 로 파일 다시 쓰기 금지

**과거 위반 사례**: PR #7 (커밋 `2942579`) — 본문 한글 깨짐. 즉시 Write tool 로 재작성 후 `gh pr edit` 로 정정.

**관련**: 시스템 프롬프트에도 명시 — "Default file encoding is UTF-16 LE (with BOM). When writing files other tools will read, pass `-Encoding utf8` to Out-File/Set-Content." 본 메모리는 이 경고를 SamhanLogis 워크플로우에 강하게 적용.
