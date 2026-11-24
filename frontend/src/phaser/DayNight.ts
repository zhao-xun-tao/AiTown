/** 根据游戏内小时计算昼夜氛围参数 */

export type DayPhase = 'dawn' | 'day' | 'dusk' | 'night'

export type DayNightState = {
  phase: DayPhase
  /** 夜幕遮罩透明度 0~0.72 */
  veilAlpha: number
  /** 环境色调（相机背景 / 提示） */
  ambient: number
  /** 灯光强度 0~1 */
  lightStrength: number
  label: string
}

export function resolveDayNight(hour: number, minute = 0): DayNightState {
  const t = ((hour % 24) + minute / 60 + 24) % 24

  if (t >= 5 && t < 8) {
    const k = (t - 5) / 3
    return {
      phase: 'dawn',
      veilAlpha: lerp(0.45, 0.08, k),
      ambient: 0xc48a5a,
      lightStrength: lerp(0.55, 0.15, k),
      label: '清晨',
    }
  }
  if (t >= 8 && t < 17) {
    return {
      phase: 'day',
      veilAlpha: 0,
      ambient: 0x7ab06a,
      lightStrength: 0,
      label: '白天',
    }
  }
  if (t >= 17 && t < 20) {
    const k = (t - 17) / 3
    return {
      phase: 'dusk',
      veilAlpha: lerp(0.08, 0.42, k),
      ambient: 0xd4783c,
      lightStrength: lerp(0.2, 0.7, k),
      label: '黄昏',
    }
  }
  // 夜晚
  const deep = t >= 20 || t < 5
  return {
    phase: 'night',
    veilAlpha: deep ? 0.62 : 0.5,
    ambient: 0x1a2848,
    lightStrength: 0.9,
    label: '夜晚',
  }
}

function lerp(a: number, b: number, t: number) {
  return a + (b - a) * Math.max(0, Math.min(1, t))
}
