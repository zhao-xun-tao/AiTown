<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import type { Agent, Building, WorldState, ZoneType } from '../types/world'

const props = defineProps<{
  world: WorldState | null
  selectedId: string | null
}>()

const emit = defineEmits<{
  select: [id: string]
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const wrapRef = ref<HTMLDivElement | null>(null)

/** 平滑插值用的渲染坐标（格子浮点） */
const renderPos = new Map<string, { x: number; y: number }>()

const ZONE_COLORS: Record<ZoneType, string> = {
  ROAD: '#3a4550',
  PLAZA: '#c9b896',
  CAFE: '#c47a5a',
  LIBRARY: '#6b8cae',
  PARK: '#5f8f6a',
  HOUSE: '#8b7355',
}

const ZONE_LABEL_COLOR: Record<ZoneType, string> = {
  ROAD: '#9aa8b5',
  PLAZA: '#4a3f2e',
  CAFE: '#2e1c14',
  LIBRARY: '#1c2a3a',
  PARK: '#1c2e20',
  HOUSE: '#2a2218',
}

let raf = 0
let resizeObs: ResizeObserver | null = null

onMounted(() => {
  resizeObs = new ResizeObserver(() => draw())
  if (wrapRef.value) resizeObs.observe(wrapRef.value)
  raf = requestAnimationFrame(loop)
})

onUnmounted(() => {
  cancelAnimationFrame(raf)
  resizeObs?.disconnect()
})

watch(
  () => props.world,
  (w) => {
    if (!w) return
    for (const a of w.agents) {
      if (!renderPos.has(a.id)) {
        renderPos.set(a.id, { x: a.x, y: a.y })
      }
    }
  },
  { immediate: true },
)

function loop() {
  updateInterpolation()
  draw()
  raf = requestAnimationFrame(loop)
}

/** 将渲染坐标向权威格子坐标平滑靠拢 */
function updateInterpolation() {
  const w = props.world
  if (!w) return
  for (const a of w.agents) {
    let p = renderPos.get(a.id)
    if (!p) {
      p = { x: a.x, y: a.y }
      renderPos.set(a.id, p)
    }
    p.x += (a.x - p.x) * 0.12
    p.y += (a.y - p.y) * 0.12
  }
}

function draw() {
  const canvas = canvasRef.value
  const wrap = wrapRef.value
  if (!canvas || !wrap) return

  const dpr = window.devicePixelRatio || 1
  const cssW = wrap.clientWidth
  const cssH = wrap.clientHeight
  canvas.width = Math.floor(cssW * dpr)
  canvas.height = Math.floor(cssH * dpr)
  canvas.style.width = `${cssW}px`
  canvas.style.height = `${cssH}px`

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

  // 背景
  ctx.fillStyle = '#1a1f24'
  ctx.fillRect(0, 0, cssW, cssH)

  const world = props.world
  if (!world) {
    ctx.fillStyle = '#9aa8b5'
    ctx.font = '14px DM Sans, sans-serif'
    ctx.textAlign = 'center'
    ctx.fillText('正在加载小镇世界…', cssW / 2, cssH / 2)
    return
  }

  const size = world.mapSize
  const pad = 24
  const cell = Math.floor(Math.min((cssW - pad * 2) / size, (cssH - pad * 2) / size))
  const mapW = cell * size
  const mapH = cell * size
  const ox = Math.floor((cssW - mapW) / 2)
  const oy = Math.floor((cssH - mapH) / 2)

  // 底图道路
  ctx.fillStyle = ZONE_COLORS.ROAD
  ctx.fillRect(ox, oy, mapW, mapH)

  // 建筑色块
  for (const b of world.buildings) {
    drawBuilding(ctx, b, ox, oy, cell)
  }

  // 网格线
  ctx.strokeStyle = 'rgba(0,0,0,0.22)'
  ctx.lineWidth = 1
  for (let i = 0; i <= size; i++) {
    const x = ox + i * cell
    const y = oy + i * cell
    ctx.beginPath()
    ctx.moveTo(x + 0.5, oy)
    ctx.lineTo(x + 0.5, oy + mapH)
    ctx.stroke()
    ctx.beginPath()
    ctx.moveTo(ox, y + 0.5)
    ctx.lineTo(ox + mapW, y + 0.5)
    ctx.stroke()
  }

  // 居民
  for (const a of world.agents) {
    const p = renderPos.get(a.id) ?? { x: a.x, y: a.y }
    drawAgent(ctx, a, p.x, p.y, ox, oy, cell, a.id === props.selectedId)
  }

  // 图例
  drawLegend(ctx, 12, cssH - 78)
}

function drawBuilding(
  ctx: CanvasRenderingContext2D,
  b: Building,
  ox: number,
  oy: number,
  cell: number,
) {
  const x = ox + b.x * cell
  const y = oy + b.y * cell
  const w = b.width * cell
  const h = b.height * cell

  ctx.fillStyle = ZONE_COLORS[b.type] ?? ZONE_COLORS.ROAD
  ctx.fillRect(x, y, w, h)

  // 内边框
  ctx.strokeStyle = 'rgba(255,255,255,0.18)'
  ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1)

  ctx.fillStyle = ZONE_LABEL_COLOR[b.type] ?? '#fff'
  ctx.font = `600 ${Math.max(10, Math.floor(cell * 0.55))}px DM Sans, sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(b.name, x + w / 2, y + h / 2)
}

function drawAgent(
  ctx: CanvasRenderingContext2D,
  agent: Agent,
  fx: number,
  fy: number,
  ox: number,
  oy: number,
  cell: number,
  selected: boolean,
) {
  const cx = ox + (fx + 0.5) * cell
  const cy = oy + (fy + 0.5) * cell
  const r = Math.max(6, cell * 0.32)

  // 选中光环
  if (selected) {
    ctx.beginPath()
    ctx.arc(cx, cy, r + 4, 0, Math.PI * 2)
    ctx.strokeStyle = '#5b9fd4'
    ctx.lineWidth = 2
    ctx.stroke()
  }

  // 身体
  ctx.beginPath()
  ctx.arc(cx, cy, r, 0, Math.PI * 2)
  ctx.fillStyle = agent.color
  ctx.fill()
  ctx.strokeStyle = 'rgba(0,0,0,0.35)'
  ctx.lineWidth = 1
  ctx.stroke()

  // 名字
  ctx.fillStyle = '#e8eef2'
  ctx.font = `600 ${Math.max(10, Math.floor(cell * 0.38))}px DM Sans, sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'top'
  ctx.fillText(agent.name, cx, cy + r + 2)

  // 头顶气泡
  drawBubble(ctx, agent.bubble || agent.state, cx, cy - r - 8)
}

function drawBubble(ctx: CanvasRenderingContext2D, text: string, cx: number, bottomY: number) {
  const maxLen = 14
  const label = text.length > maxLen ? text.slice(0, maxLen) + '…' : text
  ctx.font = '11px DM Sans, sans-serif'
  const tw = ctx.measureText(label).width
  const padX = 8
  const bw = tw + padX * 2
  const bh = 18
  const bx = Math.round(cx - bw / 2)
  const by = Math.round(bottomY - bh)

  ctx.fillStyle = 'rgba(232, 238, 242, 0.95)'
  roundRect(ctx, bx, by, bw, bh, 4)
  ctx.fill()

  // 小三角
  ctx.beginPath()
  ctx.moveTo(cx - 5, by + bh)
  ctx.lineTo(cx + 5, by + bh)
  ctx.lineTo(cx, by + bh + 5)
  ctx.closePath()
  ctx.fill()

  ctx.fillStyle = '#1a1f24'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(label, cx, by + bh / 2 + 0.5)
}

function roundRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
) {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
}

function drawLegend(ctx: CanvasRenderingContext2D, x: number, y: number) {
  const items: { type: ZoneType; name: string }[] = [
    { type: 'PLAZA', name: '广场' },
    { type: 'CAFE', name: '咖啡馆' },
    { type: 'LIBRARY', name: '图书馆' },
    { type: 'PARK', name: '公园' },
    { type: 'HOUSE', name: '住宅' },
  ]
  ctx.font = '11px DM Sans, sans-serif'
  ctx.textAlign = 'left'
  ctx.textBaseline = 'middle'
  items.forEach((it, i) => {
    const iy = y + i * 14
    ctx.fillStyle = ZONE_COLORS[it.type]
    ctx.fillRect(x, iy - 5, 10, 10)
    ctx.fillStyle = '#9aa8b5'
    ctx.fillText(it.name, x + 14, iy)
  })
}

function onClick(ev: MouseEvent) {
  const world = props.world
  const canvas = canvasRef.value
  const wrap = wrapRef.value
  if (!world || !canvas || !wrap) return

  const rect = canvas.getBoundingClientRect()
  const mx = ev.clientX - rect.left
  const my = ev.clientY - rect.top

  const size = world.mapSize
  const pad = 24
  const cssW = wrap.clientWidth
  const cssH = wrap.clientHeight
  const cell = Math.floor(Math.min((cssW - pad * 2) / size, (cssH - pad * 2) / size))
  const mapW = cell * size
  const mapH = cell * size
  const ox = Math.floor((cssW - mapW) / 2)
  const oy = Math.floor((cssH - mapH) / 2)

  // 点选最近居民
  let best: Agent | null = null
  let bestDist = Infinity
  for (const a of world.agents) {
    const p = renderPos.get(a.id) ?? { x: a.x, y: a.y }
    const cx = ox + (p.x + 0.5) * cell
    const cy = oy + (p.y + 0.5) * cell
    const d = Math.hypot(mx - cx, my - cy)
    if (d < cell * 0.6 && d < bestDist) {
      best = a
      bestDist = d
    }
  }
  if (best) emit('select', best.id)
}
</script>

<template>
  <div ref="wrapRef" class="canvas-wrap">
    <canvas ref="canvasRef" @click="onClick" />
  </div>
</template>

<style scoped>
.canvas-wrap {
  width: 100%;
  height: 100%;
  min-height: 320px;
  background:
    radial-gradient(ellipse at 30% 20%, rgba(91, 159, 212, 0.08), transparent 50%),
    radial-gradient(ellipse at 70% 80%, rgba(95, 143, 106, 0.06), transparent 45%),
    var(--bg-deep);
}

canvas {
  display: block;
  width: 100%;
  height: 100%;
  cursor: crosshair;
}
</style>
