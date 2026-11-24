import type { ApiResult } from '../types/world'

export interface WorldEvent {
  id: string
  content: string
  createdTick: number
  createdTimeDisplay: string
  remainingTicks: number
  durationTicks: number
}

export async function injectWorldEvent(content: string, durationTicks = 6): Promise<WorldEvent> {
  const res = await fetch('/api/world/events', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, durationTicks }),
  })
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  const body = (await res.json()) as ApiResult<WorldEvent>
  if (body.code !== 0) throw new Error(body.message || '投放失败')
  return body.data
}
