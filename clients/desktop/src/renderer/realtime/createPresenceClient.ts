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
  /** 클라이언트 mount 단위 opaque 식별자. account UUID 가 아니다. */
  sessionId: string
  displayName: string
  color: PresenceColor
}

export interface PresenceUser {
  sessionId: string
  displayName: string
}

export interface PresenceClientConfig {
  name: string
  presencePath: (entityId: string, action?: 'join' | 'leave') => string
  streamPath: (entityId: string) => string
}

export interface PresenceClient {
  list: (entityId: string) => Promise<PresenceEntry[]>
  join: (entityId: string, user: PresenceUser, signal?: AbortSignal) => Promise<PresenceEntry | null>
  leave: (entityId: string, user: PresenceUser, signal?: AbortSignal) => Promise<void>
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
    const res = await apiClient.get<ApiEnvelope<PresenceEntry[]> | PresenceEntry[]>(
      config.presencePath(entityId),
      { headers: await collabHeaders() },
    )
    const body = res.data
    const items = Array.isArray(body)
      ? body
      : typeof body === 'object' && body !== null && 'data' in body
        ? body.data
        : []
    return Array.isArray(items) ? items : []
  }

  async function join(
    entityId: string,
    user: PresenceUser,
    signal?: AbortSignal,
  ): Promise<PresenceEntry | null> {
    const headers = await collabHeaders()
    if (signal?.aborted) return null
    const res = await apiClient.post<ApiEnvelope<PresenceEntry>>(
      config.presencePath(entityId, 'join'),
      user,
      { headers, signal },
    )
    return res.data.data
  }

  async function leave(
    entityId: string,
    user: PresenceUser,
    signal?: AbortSignal,
  ): Promise<void> {
    const headers = await collabHeaders()
    if (signal?.aborted) return
    await apiClient.post<ApiEnvelope<null>>(
      config.presencePath(entityId, 'leave'),
      user,
      { headers, signal },
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
