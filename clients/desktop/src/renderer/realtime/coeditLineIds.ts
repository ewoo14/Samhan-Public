import type { DocCoeditProvider } from './createCoeditProvider'

/**
 * coedit 라인의 서버 lineId 해석 — 전표/견적 공용.
 *
 * <p><b>왜 이 모듈이 있나 (R8-FE-1 = R8-QA-2 · BLOCKING · 라이브 2/2 결정적 재현)</b>:
 * 종전 전표/견적 폼은 coedit Y.Doc → 폼 라인 변환 시 lineId 를 <b>위치(index)로 복원</b>했다
 * ({@code lineId: current[index]?.lineId ?? null}). 위치는 CRDT 에서 안정적이지 않다 —
 * 원격 피어가 1행을 삭제하면 Y.Doc 은 즉시 한 칸 당겨지지만 로컬 {@code current} 배열은
 * 그 시점에 아직 구 스냅샷이라, 남은 모든 행이 <b>이웃 행의 lineId</b>를 물려받는다.
 * 서버는 lineId 를 무조건 신뢰하므로(휴리스틱 폴백 의도적 제거) 그대로 각인된다:
 * 단품이 남의 세트 계보를 상속하고, {@code set_head} 가 탈취되고, 사용자가 입력한 단가가
 * 가격기억에서 증발한다.
 *
 * <p><b>처방</b>: lineId 를 위치로 추정하지 않고 <b>Y.Doc 에서 직독</b>한다. Y.Doc 행과 lineId 는
 * 같은 CRDT 트랜잭션으로 이동/삭제되므로 원격 편집과 무관하게 항상 자기 자신을 가리킨다.
 *
 * <p><b>🔴 그런데 직독만으로는 안전하지 않다 (R8-FE-9 — fix 지뢰)</b>:
 * {@link DocCoeditProvider.replaceItems} 는 seed row 의 lineId 가 비어 있으면
 * <b>클라이언트 랜덤 UUID</b>({@code generateLineId()})를 대신 채운다. 그 값을 그대로 저장
 * payload 에 실으면 서버 소유검증에 걸려 <b>전 라인 400</b> 이 난다. 실제로 견적 seed 는
 * lineId 를 pick 하지 않아 기존 Y.Doc 이 전부 랜덤 UUID 다(본 PR 에서 seed 를 고쳤으나,
 * <b>이미 서버에 영속된 구 Y.Doc</b> 은 여전히 랜덤 UUID 를 담고 있다).
 *
 * <p>따라서 직독값은 반드시 <b>현재 문서의 서버 라인 id 집합</b>으로 검증한다. 미검증 값은
 * null(신규 평면 라인) 로 강등해 400 대신 안전한 fail-soft 로 수렴시킨다. 이 검증은
 * BE {@code validateLineIds} (타 문서 lineId → 400) 의 클라이언트측 미러이며,
 * 서버 방어를 대체하지 않는다.
 */

/** Y.Doc 라인 map 의 lineId 필드명 — {@code createCoeditProvider.LINE_ID_FIELD} 와 동일 규약. */
const LINE_ID_CELL = 'lineId'

/**
 * Y.Doc index 행의 lineId 를 직독한다 — 위치복원 금지.
 *
 * @returns Y.Doc 에 실린 lineId 문자열. 미보유 시 빈 문자열.
 */
export function readCoeditLineId(provider: DocCoeditProvider, index: number): string {
  return provider.getItemValue(index, LINE_ID_CELL)
}

/**
 * Y.Doc index 행의 lineId 를 직독하고 <b>현재 문서 소유</b> 인지 검증해 저장 payload 값을 만든다.
 *
 * @param knownServerLineIds 현재 로드된 문서 상세 응답의 라인 id 집합
 * @returns 서버가 아는 기존 라인이면 그 lineId, 아니면 null(= 신규 평면 라인)
 */
export function resolveServerLineId(
  provider: DocCoeditProvider,
  index: number,
  knownServerLineIds: ReadonlySet<string>,
): string | null {
  const docLineId = readCoeditLineId(provider, index)
  if (!docLineId) return null
  // 클라 랜덤 UUID(replaceItems 폴백/addItem 신규행)는 서버가 모르는 값이다 → 신규 라인으로 강등.
  return knownServerLineIds.has(docLineId) ? docLineId : null
}

/** 문서 상세 라인 배열에서 서버 lineId 집합을 만든다 (null/빈값 제외). */
export function toServerLineIdSet(
  lines: ReadonlyArray<{ id?: string | null; lineId?: string | null }>,
): ReadonlySet<string> {
  const ids = new Set<string>()
  for (const line of lines) {
    const id = line.id ?? line.lineId
    if (id) ids.add(id)
  }
  return ids
}

/**
 * provider Y.Doc 이 서버 lineId 를 담고 있지 않은 <b>구 스냅샷</b>인지 판정한다 — 재시드 게이트.
 *
 * <p>lineId seed 도입 이전에 만들어져 서버에 영속된 Y.Doc 은 라인마다 클라 랜덤 UUID 를 갖는다.
 * 그대로 두면 {@link resolveServerLineId} 가 전 라인을 null 로 강등해 계보가 조용히 소실되고,
 * 계보 보유 문서라면 BE {@code requireLineIdContract} 가 400 을 낸다. 열람 시점에 서버 기준으로
 * 재시드해 그 두 경로를 모두 닫는다.
 *
 * <p>전표/견적 모두 coedit 중 라인 추가·삭제를 잠그므로(seed-lock) Y.Doc 행은 전부 seed 유래다 —
 * 즉 "서버가 모르는 lineId 가 하나라도 있다" = "이 Y.Doc 은 구 seed" 로 안전하게 환원된다.
 */
export function coeditLineIdsAreStale(
  provider: DocCoeditProvider,
  knownServerLineIds: ReadonlySet<string>,
): boolean {
  const rowCount = provider.items.toArray().length
  for (let index = 0; index < rowCount; index += 1) {
    const docLineId = readCoeditLineId(provider, index)
    if (!docLineId || !knownServerLineIds.has(docLineId)) return true
  }
  return false
}
