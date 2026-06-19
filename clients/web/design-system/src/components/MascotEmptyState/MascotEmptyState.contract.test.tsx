import { MascotEmptyState } from './MascotEmptyState'

const basicEmptyState = <MascotEmptyState title="조회 결과가 없습니다." />
const emptyStateWithAction = (
  <MascotEmptyState
    title="주문서가 없습니다."
    description="필터를 변경하거나 새 주문서를 등록해 주세요."
    action={<button type="button">새 주문서</button>}
  />
)

// @ts-expect-error title 은 필수다.
const invalidEmptyState = <MascotEmptyState />

export { basicEmptyState, emptyStateWithAction, invalidEmptyState }
