import type { ApiResult } from '../types/world'

export interface CotTurnLog {
  agentId: string
  agentName: string
  tick: number
  timeDisplay: string
  perceptionSummary: string
  retrievedMemories: string[]
  thought: string
  action: string
  targetLocation?: string | null
  targetAgent?: string | null
  speech?: string | null
  fallback?: boolean
  error?: string | null
  latencyMs?: number | null
  promptTokens?: number | null
  completionTokens?: number | null
  totalTokens?: number | null
  model?: string | null
  createdAtEpochMs: number
}

export interface RelationChange {
  tick: number
  timeDisplay: string
  delta: number
  favorabilityAfter: number
  note?: string | null
}

export interface AgentRelation {
  fromAgentId: string
  fromAgentName: string
  toAgentId: string
  toAgentName: string
  favorability: number
  impression: string
  history: RelationChange[]
}

async function get<T>(url: string): Promise<T> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const body = (await res.json()) as ApiResult<T>
  if (body.code !== 0) throw new Error(body.message || '请求失败')
  return body.data
}

export function fetchCot(agentId: string) {
  return get<CotTurnLog[]>(`/api/agents/${encodeURIComponent(agentId)}/cot`)
}

export function fetchRelations(agentId: string) {
  return get<AgentRelation[]>(`/api/agents/${encodeURIComponent(agentId)}/relations`)
}
