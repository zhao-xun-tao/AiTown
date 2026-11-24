import type { AgentState } from '../types/world'

export type Facing = 'down' | 'up' | 'left' | 'right'
export type AnimMode = 'idle' | 'walk' | 'talk' | 'think'

/**
 * 角色动画状态机：根据位移与 Agent 逻辑状态选择动画，避免无位移播 walk。
 */
export class AnimFSM {
  facing: Facing = 'down'
  mode: AnimMode = 'idle'
  private stuckFrames = 0

  /** @returns 应播放的 Phaser anim key，如 linyue-walk-down */
  update(
    charKey: string,
    dx: number,
    dy: number,
    moving: boolean,
    agentState: AgentState,
  ): string {
    if (moving && (Math.abs(dx) > 0.05 || Math.abs(dy) > 0.05)) {
      this.facing = dominantFacing(dx, dy)
      this.mode = 'walk'
      this.stuckFrames = 0
    } else {
      this.stuckFrames++
      if (this.mode === 'walk' && this.stuckFrames > 2) {
        this.mode = deriveIdleMode(agentState)
      } else if (this.mode !== 'walk') {
        this.mode = deriveIdleMode(agentState)
      }
    }

    // Talk/Think 暂复用 idle 朝向帧（资产生成可后续扩展专用帧）
    if (this.mode === 'talk' || this.mode === 'think') {
      return `${charKey}-idle-${this.facing}`
    }
    return `${charKey}-${this.mode}-${this.facing}`
  }
}

function dominantFacing(dx: number, dy: number): Facing {
  if (Math.abs(dx) > Math.abs(dy)) return dx < 0 ? 'left' : 'right'
  return dy < 0 ? 'up' : 'down'
}

function deriveIdleMode(state: AgentState): AnimMode {
  if (state === 'CHATTING') return 'talk'
  if (state === 'WORKING' || state === 'READING' || state === 'WRITING') return 'think'
  return 'idle'
}
