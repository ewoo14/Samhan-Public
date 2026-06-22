import type { SlipDetail } from '../api/slip'
import type { ApprovalLineStructure } from '../api/approvalLineConfigApi'

export type ApprovalSlipType = 'OUTBOUND' | 'INBOUND'

export interface RoleCellProps {
  label: string
  value?: string | null
  signaturePng?: string | null
}

export interface ApprovalRoleCellsProps {
  slip: SlipDetail
  roles: ApprovalLineStructure[] | null
  slipType: ApprovalSlipType
}

function normalizeSignature(src: string | null | undefined): string | null {
  if (!src) return null
  return src.startsWith('data:') ? src : `data:image/png;base64,${src}`
}

/**
 * 결재란 셀 — 작성자/입고인/출고자/검수인 및 추가 단계 공통 표시 단위.
 */
export function RoleCell({ label, value, signaturePng }: RoleCellProps) {
  const normalizedSignature = normalizeSignature(signaturePng)

  return (
    <div className="approval-role-cell">
      <div className="approval-role-label">{label}</div>
      <div className="approval-role-value">
        {normalizedSignature ? (
          <img className="approval-role-stamp" src={normalizedSignature} alt={`${label} 서명`} />
        ) : (
          <span className="approval-role-stamp-space" />
        )}
        {value ? <span className="name">{value}</span> : null}
      </div>
    </div>
  )
}

export function roleSignerName(
  slip: SlipDetail,
  role: ApprovalLineStructure,
  slipType: ApprovalSlipType,
): string | null {
  if (role.stepType === 'CREATOR') return slip.ownerFullName ?? null

  if (slipType === 'OUTBOUND') {
    if (role.actionKey === 'OUTBOUND_DISPATCH') return slip.dispatcherFullName ?? null
    if (role.actionKey === 'OUTBOUND_INSPECT') return slip.inspectorFullName ?? null
    return null
  }

  if (role.actionKey === 'INBOUND_RECEIVE') return slip.acceptedByFullName ?? null
  if (role.actionKey === 'INBOUND_INSPECT') return slip.inspectorFullName ?? null
  return null
}

export function fallbackRoles(slipType: ApprovalSlipType): ApprovalLineStructure[] {
  if (slipType === 'OUTBOUND') {
    return [
      { sequence: 0, label: '작성자', stepType: 'CREATOR', actionKey: null },
      { sequence: 1, label: '출고자', stepType: 'GROUP', actionKey: 'OUTBOUND_DISPATCH' },
      { sequence: 2, label: '검수자', stepType: 'GROUP', actionKey: 'OUTBOUND_INSPECT' },
    ]
  }

  return [
    { sequence: 0, label: '작성자', stepType: 'CREATOR', actionKey: null },
    { sequence: 1, label: '입고인', stepType: 'GROUP', actionKey: 'INBOUND_RECEIVE' },
    { sequence: 2, label: '검수인', stepType: 'GROUP', actionKey: 'INBOUND_INSPECT' },
  ]
}

export function ApprovalRoleCells({ slip, roles, slipType }: ApprovalRoleCellsProps) {
  return (
    <>
      {(roles ?? fallbackRoles(slipType))
        .slice()
        .sort((a, b) => a.sequence - b.sequence)
        .map((role) => (
          <RoleCell
            key={`${role.sequence}-${role.label}-${role.actionKey ?? role.stepType}`}
            label={role.label}
            value={roleSignerName(slip, role, slipType)}
          />
        ))}
    </>
  )
}
