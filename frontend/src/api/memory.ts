import type { ApiResult } from '../types/world'

export interface WorkingMemoryDto {
  tick: number
  timeDisplay: string
  type: string
  content: string
  createdAtEpochMs: number
}

export interface LongTermMemoryDto {
  id: number
  agentId: string
  agentName: string
  memoryType: string
  content: string
  importance: number
  gameDay: number
  gameHour: number
  gameMinute: number
  gameTick: number
  createdAt?: string
}

export interface AgentMemoriesResponse {
  agentId: string
  working: WorkingMemoryDto[]
  longTerm: LongTermMemoryDto[]
}

export async function fetchAgentMemories(agentId: string): Promise<AgentMemoriesResponse> {
  const res = await fetch(`/api/agents/${encodeURIComponent(agentId)}/memories?longTermLimit=40`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const body = (await res.json()) as ApiResult<AgentMemoriesResponse>
  if (body.code !== 0) throw new Error(body.message || '加载记忆失败')
  return body.data
}
