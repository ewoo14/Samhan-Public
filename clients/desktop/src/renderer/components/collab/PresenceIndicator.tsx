import { Badge } from '@samhan/design-system'
import type { PresenceColor, PresenceEntry } from '../../realtime/createPresenceClient'

const COLOR_HEX: Record<PresenceColor, string> = {
  BLUE: '#2563EB',
  GREEN: '#15803D',
  AMBER: '#B45309',
  ROSE: '#E11D48',
  VIOLET: '#7C3AED',
  CYAN: '#0E7490',
  LIME: '#4D7C0F',
  PINK: '#DB2777',
}

function isPresenceEntry(value: unknown): value is PresenceEntry {
  return typeof value === 'object'
    && value !== null
    && 'sessionId' in value
    && 'displayName' in value
    && 'color' in value
}

export interface PresenceIndicatorProps {
  entries: PresenceEntry[] | unknown
}

export function PresenceIndicator({ entries }: PresenceIndicatorProps) {
  const list = Array.isArray(entries) ? entries.filter(isPresenceEntry) : []
  const deduped = Array.from(
    list.reduce((acc, entry) => {
      const key = `${entry.displayName}|${entry.color}`
      if (!acc.has(key)) acc.set(key, entry)
      return acc
    }, new Map<string, PresenceEntry>()).values(),
  )
  const visible = deduped.slice(0, 3)
  const hiddenCount = Math.max(deduped.length - visible.length, 0)
  const hiddenNames = deduped.slice(3).map((entry) => entry.displayName).join(', ')
  if (deduped.length === 0) return null

  return (
    <div
      data-testid="presence-indicator"
      aria-label={`현재 보고 있음 ${deduped.length}명`}
      style={{ display: 'flex', alignItems: 'center', gap: 8 }}
    >
      <span
        style={{
          color: 'var(--color-neutral-500, #64748B)',
          fontSize: 12,
          lineHeight: 1,
          whiteSpace: 'nowrap',
        }}
      >
        현재 보는 중:
      </span>
      {visible.map((entry) => (
        <span
          key={entry.sessionId}
          title={`${entry.displayName} 현재 보고 있음`}
          aria-label={`${entry.displayName} 현재 보고 있음`}
          style={{
            borderRadius: 999,
            display: 'inline-flex',
            alignItems: 'center',
            gap: 6,
            padding: '2px 8px',
            border: '1px solid var(--color-neutral-200, #E2E8F0)',
            background: 'var(--color-neutral-50, #F8FAFC)',
            color: 'var(--color-neutral-900, #0F172A)',
            fontSize: 12,
            lineHeight: 1.5,
            maxWidth: 200,
            boxSizing: 'border-box',
          }}
        >
          <span
            aria-hidden="true"
            style={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              background: COLOR_HEX[entry.color] ?? '#64748B',
              flex: '0 0 auto',
            }}
          />
          <span
            style={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {entry.displayName}
          </span>
        </span>
      ))}
      {hiddenCount > 0 ? (
        <Badge variant="neutral" title={hiddenNames} aria-label={hiddenNames}>
          +{hiddenCount}
        </Badge>
      ) : null}
    </div>
  )
}
