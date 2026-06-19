import { Badge } from '@samhan/design-system'
import type { PresenceColor, PresenceEntry } from '../../realtime/createPresenceClient'

const COLOR_HEX: Record<PresenceColor, string> = {
  BLUE: '#2563EB',
  GREEN: '#16A34A',
  AMBER: '#D97706',
  ROSE: '#E11D48',
  VIOLET: '#7C3AED',
  CYAN: '#0891B2',
  LIME: '#65A30D',
  PINK: '#DB2777',
}

function initialOf(displayName: string): string {
  const trimmed = displayName.trim()
  return trimmed.length > 0 ? trimmed.slice(0, 1) : '?'
}

export interface PresenceIndicatorProps {
  entries: PresenceEntry[]
}

export function PresenceIndicator({ entries }: PresenceIndicatorProps) {
  const deduped = Array.from(
    entries.reduce((acc, entry) => {
      if (!acc.has(entry.displayName)) acc.set(entry.displayName, entry)
      return acc
    }, new Map<string, PresenceEntry>()).values(),
  )
  const visible = deduped.slice(0, 3)
  const hiddenCount = Math.max(deduped.length - visible.length, 0)
  if (deduped.length === 0) return null

  return (
    <div
      data-testid="presence-indicator"
      aria-label={`현재 보고 있음 ${deduped.length}명`}
      style={{ display: 'flex', alignItems: 'center', gap: 8 }}
    >
      <div style={{ display: 'flex', alignItems: 'center', minHeight: 28 }}>
        {visible.map((entry, index) => (
          <span
            key={entry.sessionId}
            title={`${entry.displayName} 현재 보고 있음`}
            aria-label={`${entry.displayName} 현재 보고 있음`}
            style={{
              width: 28,
              height: 28,
              borderRadius: '50%',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              marginLeft: index === 0 ? 0 : -6,
              border: '2px solid var(--color-surface, #FFFFFF)',
              background: COLOR_HEX[entry.color] ?? '#64748B',
              color: '#FFFFFF',
              fontSize: 12,
              fontWeight: 700,
              lineHeight: 1,
              boxSizing: 'border-box',
            }}
          >
            {initialOf(entry.displayName)}
          </span>
        ))}
      </div>
      {hiddenCount > 0 ? (
        <Badge variant="neutral">+{hiddenCount}</Badge>
      ) : null}
    </div>
  )
}
