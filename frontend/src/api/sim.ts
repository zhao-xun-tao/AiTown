import type { ApiResult, WorldState } from '../types/world'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

async function post<T>(url: string): Promise<T> {
  const res = await fetch(url, { method: 'POST', headers: JSON_HEADERS })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  const body = (await res.json()) as ApiResult<T>
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

async function get<T>(url: string): Promise<T> {
  const res = await fetch(url)
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  const body = (await res.json()) as ApiResult<T>
  if (body.code !== 0) {
    throw new Error(body.message || '请求失败')
  }
  return body.data
}

/** 拉取完整世界快照 */
export function fetchWorldState() {
  return get<WorldState>('/api/world/state')
}

export function startSim() {
  return post<Record<string, unknown>>('/api/sim/start')
}

export function pauseSim() {
  return post<Record<string, unknown>>('/api/sim/pause')
}

export function stepSim() {
  return post<WorldState>('/api/sim/step')
}
