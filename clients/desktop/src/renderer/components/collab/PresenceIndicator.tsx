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

function initialOf(displayName: string): string {
  const trimmed = displayName.trim()
  return trimmed.length > 0 ? [...trimmed][0] ?? '?' : '?'
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
        <Badge variant="neutral" title={hiddenNames} aria-label={hiddenNames}>
          +{hiddenCount}
        </Badge>
      ) : null}
    </div>
  )
}
