import { useEffect, useMemo, useState } from 'react'
import {
  SlipPresenceClient,
  type PresenceClient,
  type PresenceEntry,
  type PresenceUser,
} from '../realtime/createPresenceClient'

const HEARTBEAT_MS = 30_000

function isPresenceEntry(value: unknown): value is PresenceEntry {
  return typeof value === 'object'
    && value !== null
    && 'userId' in value
    && 'displayName' in value
    && 'color' in value
}

function upsertPresence(entries: PresenceEntry[], next: PresenceEntry): PresenceEntry[] {
  const without = entries.filter((entry) => entry.userId !== next.userId)
  return [...without, next].sort((a, b) => {
    const byName = a.displayName.localeCompare(b.displayName, 'ko')
    return byName === 0 ? a.userId.localeCompare(b.userId) : byName
  })
}

async function resolveCurrentUser(): Promise<PresenceUser | null> {
  try {
    const auth = await window.samhanAuth.getToken()
    if (!auth?.userId) return null
    return {
      userId: auth.userId,
      displayName: auth.fullName?.trim() || '사용자',
    }
  } catch {
    return null
  }
}

export interface UsePresenceOptions {
  entityId: string
  client?: PresenceClient
  enabled?: boolean
}

export function usePresence({
  entityId,
  client = SlipPresenceClient,
  enabled = true,
}: UsePresenceOptions): PresenceEntry[] {
  const [entries, setEntries] = useState<PresenceEntry[]>([])
  const stableEntityId = useMemo(() => entityId, [entityId])

  useEffect(() => {
    if (!enabled || !stableEntityId) {
      setEntries([])
      return
    }

    let active = true
    let currentUser: PresenceUser | null = null
    let heartbeatTimer: ReturnType<typeof setInterval> | null = null

    const refresh = async () => {
      try {
        const next = await client.list(stableEntityId)
        if (active) setEntries(next)
      } catch (err) {
        console.warn('[presence] 목록 조회 실패', err)
      }
    }

    const join = async () => {
      if (!currentUser) return
      try {
        const entry = await client.join(stableEntityId, currentUser)
        if (active) {
          setEntries((prev) => upsertPresence(prev, entry))
        }
      } catch (err) {
        console.warn('[presence] join/heartbeat 실패', err)
      }
    }

    const ctrl = client.subscribe(stableEntityId, (evt) => {
      if (evt.event === 'presence:join' && isPresenceEntry(evt.data)) {
        const entry = evt.data
        setEntries((prev) => upsertPresence(prev, entry))
        return
      }
      if (evt.event === 'presence:leave' && isPresenceEntry(evt.data)) {
        const entry = evt.data
        setEntries((prev) => prev.filter((item) => item.userId !== entry.userId))
      }
    })

    void (async () => {
      currentUser = await resolveCurrentUser()
      await refresh()
      await join()
      heartbeatTimer = setInterval(() => {
        void join()
      }, HEARTBEAT_MS)
    })()

    return () => {
      active = false
      ctrl.abort()
      if (heartbeatTimer !== null) clearInterval(heartbeatTimer)
      if (currentUser) {
        void client.leave(stableEntityId, currentUser).catch((err) => {
          console.warn('[presence] leave 실패', err)
        })
      }
    }
  }, [client, enabled, stableEntityId])

  return entries
}
