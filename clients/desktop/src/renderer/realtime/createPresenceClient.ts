import { apiClient, type ApiEnvelope } from '../api/client'
import { createRealtimeClient, type RealtimeEvent } from './createRealtimeClient'

export type PresenceColor =
  | 'BLUE'
  | 'GREEN'
  | 'AMBER'
  | 'ROSE'
  | 'VIOLET'
  | 'CYAN'
  | 'LIME'
  | 'PINK'

export interface PresenceEntry {
  /** 내부 식별자. React key/API state 전용이며 화면 표시 금지. */
  userId: string
  displayName: string
  color: PresenceColor
  lastSeenAt?: string
}

export interface PresenceUser {
  userId: string
  displayName: string
}

export interface PresenceClientConfig {
  name: string
  presencePath: (entityId: string, action?: 'join' | 'leave') => string
  streamPath: (entityId: string) => string
}

export interface PresenceClient {
  list: (entityId: string) => Promise<PresenceEntry[]>
  join: (entityId: string, user: PresenceUser) => Promise<PresenceEntry>
  leave: (entityId: string, user: PresenceUser) => Promise<void>
  subscribe: (entityId: string, onEvent: (event: RealtimeEvent) => void) => AbortController
}

async function collabHeaders(): Promise<Record<string, string>> {
  try {
    const auth = await window.samhanAuth.getToken()
    const headers: Record<string, string> = {}
    if (auth?.userId) headers['X-User-Id'] = auth.userId
    if (auth?.fullName) headers['X-User-Name'] = auth.fullName
    return headers
  } catch {
    return {}
  }
}

export function createPresenceClient(config: PresenceClientConfig): PresenceClient {
  const realtime = createRealtimeClient({
    name: `${config.name}-presence`,
    endpointPath: config.streamPath,
  })

  async function list(entityId: string): Promise<PresenceEntry[]> {
    const res = await apiClient.get<ApiEnvelope<PresenceEntry[]>>(
      config.presencePath(entityId),
      { headers: await collabHeaders() },
    )
    return res.data.data
  }

  async function join(entityId: string, user: PresenceUser): Promise<PresenceEntry> {
    const res = await apiClient.post<ApiEnvelope<PresenceEntry>>(
      config.presencePath(entityId, 'join'),
      user,
      { headers: await collabHeaders() },
    )
    return res.data.data
  }

  async function leave(entityId: string, user: PresenceUser): Promise<void> {
    await apiClient.post<ApiEnvelope<null>>(
      config.presencePath(entityId, 'leave'),
      user,
      { headers: await collabHeaders() },
    )
  }

  return {
    list,
    join,
    leave,
    subscribe: realtime.subscribe,
  }
}

export const SlipPresenceClient = createPresenceClient({
  name: 'slip',
  presencePath: (slipId, action) => {
    const base = `/api/v1/slips/${encodeURIComponent(slipId)}/collab/presence`
    return action ? `${base}/${action}` : base
  },
  streamPath: (slipId) =>
    `/api/v1/slips/${encodeURIComponent(slipId)}/collab/stream`,
})
