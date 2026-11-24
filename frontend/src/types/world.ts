/** 与后端 WorldState 对齐的前端类型定义 */

export type ZoneType = 'ROAD' | 'PLAZA' | 'CAFE' | 'LIBRARY' | 'PARK' | 'HOUSE'

export type AgentState =
  | 'IDLE'
  | 'WALKING'
  | 'WORKING'
  | 'READING'
  | 'WRITING'
  | 'CHATTING'
  | 'RESTING'

export type SimStatus = 'PAUSED' | 'RUNNING'

export interface Building {
  id: string
  name: string
  type: ZoneType
  x: number
  y: number
  width: number
  height: number
}

export interface GridPoint {
  x: number
  y: number
}

export interface Agent {
  id: string
  name: string
  role: string
  persona: string
  x: number
  y: number
  state: AgentState
  bubble: string
  lastThought: string
  lastAction?: string
  lastSpeech?: string | null
  lastError?: string | null
  retrievedMemorySummary?: string | null
  color: string
  /** 寻路最终目标 */
  targetX?: number | null
  targetY?: number | null
  /** 后端 A* 剩余路点（不含当前格） */
  path?: GridPoint[] | null
}

export interface AreaSpeechEvent {
  tick: number
  areaKey: string
  areaName: string
  speakerId: string
  speakerName: string
  targetAgentName?: string | null
  speech: string
  x: number
  y: number
}

export interface GameTime {
  day: number
  hour: number
  minute: number
  tick: number
}

export interface WorldEvent {
  id: string
  content: string
  createdTick: number
  createdTimeDisplay: string
  remainingTicks: number
  durationTicks: number
}

export interface WorldState {
  mapSize: number
  buildings: Building[]
  agents: Agent[]
  recentSpeeches?: AreaSpeechEvent[]
  activeWorldEvents?: WorldEvent[]
  gameTime: GameTime
  simStatus: SimStatus
  timeDisplay: string
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}
