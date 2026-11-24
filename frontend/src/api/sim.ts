import type { WorldState } from '../types/world'
import { getJson, postJson } from './http'

/** 拉取完整世界快照（8s 超时） */
export function fetchWorldState() {
  return getJson<WorldState>('/api/world/state', 8000)
}

export function startSim() {
  return postJson<Record<string, unknown>>('/api/sim/start', 10000)
}

export function pauseSim() {
  return postJson<Record<string, unknown>>('/api/sim/pause', 10000)
}

export function stepSim() {
  // LLM 单步可能较慢
  return postJson<WorldState>('/api/sim/step', 60000)
}
