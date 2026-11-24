<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import type { Agent, Building, WorldState, ZoneType } from '../types/world'
import { loadTownAssets, type TownAssets } from '../render/assets'

const props = defineProps<{
  world: WorldState | null
  selectedId: string | null
  syncing?: boolean
}>()

const emit = defineEmits<{
  select: [id: string]
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const wrapRef = ref<HTMLDivElement | null>(null)
const assetsReady = ref(false)

const renderPos = new Map<string, { x: number; y: number }>()
let assets: TownAssets | null = null
let raf = 0
let resizeObs: ResizeObserver | null = null
let t0 = performance.now()
let mapCache: HTMLCanvasElement | null = null
let mapCacheKey = ''
let lastCssW = 0
let lastCssH = 0
let view = { cell: 0, ox: 0, oy: 0, mapW: 0, mapH: 0 }

function buildingAt(buildings: Building[], gx: number, gy: number) {
  return (
    buildings.find(
      (b) => gx >= b.x && gx < b.x + b.width && gy >= b.y && gy < b.y + b.height,
    ) ?? null
  )
}

onMounted(async () => {
  resizeObs = new ResizeObserver(() => {
    mapCacheKey = ''
    draw()
  })
  if (wrapRef.value) resizeObs.observe(wrapRef.value)
  try {
    assets = await loadTownAssets()
    assetsReady.value = true
    mapCacheKey = ''
  } catch (e) {
    console.warn('美术资源加载失败，将使用简化绘制', e)
  }
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
      if (!renderPos.has(a.id)) renderPos.set(a.id, { x: a.x, y: a.y })
    }
    mapCacheKey = ''
  },
  { immediate: true },
)

function loop(now: number) {
  t0 = now
  updateInterpolation()
  draw()
  raf = requestAnimationFrame(loop)
}

function updateInterpolation() {
  const w = props.world
  if (!w) return
  for (const a of w.agents) {
    let p = renderPos.get(a.id)
    if (!p) {
      p = { x: a.x, y: a.y }
      renderPos.set(a.id, p)
    }
    p.x += (a.x - p.x) * 0.16
    p.y += (a.y - p.y) * 0.16
  }
}

function buildingSprite(type: ZoneType): HTMLImageElement | null {
  if (!assets) return null
  switch (type) {
    case 'HOUSE':
      return assets.house
    case 'CAFE':
      return assets.cafe
    case 'LIBRARY':
      return assets.library
    case 'PARK':
      return assets.park
    case 'PLAZA':
      return assets.plaza
    default:
      return null
  }
}

function ensureMapCache(
  world: WorldState,
  cssW: number,
  cssH: number,
  cell: number,
  ox: number,
  oy: number,
  mapW: number,
  mapH: number,
) {
  const key = `${cssW}x${cssH}:${cell}:${assetsReady.value}:${world.buildings.map((b) => b.id).join(',')}`
  if (mapCache && mapCacheKey === key) return mapCache

  const c = document.createElement('canvas')
  c.width = cssW
  c.height = cssH
  const ctx = c.getContext('2d')
  if (!ctx) return null

  // 远景草地
  const sky = ctx.createLinearGradient(0, 0, 0, cssH)
  sky.addColorStop(0, '#8ec5a2')
  sky.addColorStop(0.5, '#5f9a72')
  sky.addColorStop(1, '#3d6b52')
  ctx.fillStyle = sky
  ctx.fillRect(0, 0, cssW, cssH)

  // 地图阴影
  ctx.fillStyle = 'rgba(15, 30, 22, 0.35)'
  roundRect(ctx, ox + 8, oy + 12, mapW, mapH, 14)
  ctx.fill()

  ctx.save()
  roundRect(ctx, ox, oy, mapW, mapH, 12)
  ctx.clip()

  // 地砖
  for (let gy = 0; gy < world.mapSize; gy++) {
    for (let gx = 0; gx < world.mapSize; gx++) {
      const b = buildingAt(world.buildings, gx, gy)
      const x = ox + gx * cell
      const y = oy + gy * cell
      const tile = b
        ? b.type === 'PARK'
          ? assets?.grass
          : b.type === 'PLAZA'
            ? assets?.path
            : assets?.grass
        : assets?.path
      if (tile) {
        ctx.drawImage(tile, x, y, cell + 0.5, cell + 0.5)
      } else {
        ctx.fillStyle = b ? '#5f9e5a' : '#c9b896'
        ctx.fillRect(x, y, cell, cell)
      }
    }
  }

  // 建筑精灵：铺满各自区域，略留边
  for (const b of world.buildings) {
    drawBuildingSprite(ctx, b, ox, oy, cell)
  }

  // 日照
  const light = ctx.createRadialGradient(
    ox + mapW * 0.3,
    oy + mapH * 0.15,
    cell,
    ox + mapW * 0.5,
    oy + mapH * 0.55,
    mapW * 0.8,
  )
  light.addColorStop(0, 'rgba(255, 230, 160, 0.14)')
  light.addColorStop(1, 'rgba(20, 50, 30, 0.1)')
  ctx.fillStyle = light
  ctx.fillRect(ox, oy, mapW, mapH)

  ctx.restore()

  ctx.strokeStyle = 'rgba(255,255,255,0.28)'
  ctx.lineWidth = 2.5
  roundRect(ctx, ox, oy, mapW, mapH, 12)
  ctx.stroke()

  mapCache = c
  mapCacheKey = key
  return c
}

function drawBuildingSprite(
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
  const img = buildingSprite(b.type)

  // 地面垫色，避免精灵透明处穿帮
  if (b.type === 'HOUSE' || b.type === 'CAFE' || b.type === 'LIBRARY') {
    ctx.fillStyle = 'rgba(80, 100, 70, 0.35)'
    ctx.fillRect(x + cell * 0.08, y + cell * 0.2, w - cell * 0.16, h - cell * 0.15)
  }

  if (img) {
    // 建筑尽量铺满区域，底部对齐
    const padX = cell * 0.08
    const padTop = cell * 0.05
    const dw = w - padX * 2
    const dh = h - padTop - cell * 0.05
    const aspect = img.width / img.height
    let drawW = dw
    let drawH = drawW / aspect
    if (drawH > dh) {
      drawH = dh
      drawW = drawH * aspect
    }
    const dx = x + (w - drawW) / 2
    const dy = y + h - drawH - cell * 0.02
    ctx.drawImage(img, dx, dy, drawW, drawH)
  }

  // 铭牌
  const label = shortName(b)
  ctx.font = `800 ${Math.max(12, Math.floor(cell * 0.45))}px Nunito, sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  const tw = ctx.measureText(label).width
  const lx = x + w / 2
  const ly = y + cell * 0.55
  ctx.fillStyle = 'rgba(18, 28, 22, 0.72)'
  roundRect(ctx, lx - tw / 2 - 8, ly - 11, tw + 16, 22, 8)
  ctx.fill()
  ctx.fillStyle = '#f7f2e8'
  ctx.fillText(label, lx, ly + 1)
}

function shortName(b: Building) {
  if (b.name.includes('咖啡')) return '咖啡馆'
  if (b.name.includes('图书')) return '图书馆'
  if (b.name.includes('广场')) return '中央广场'
  if (b.name.includes('公园')) return b.name.includes('西') ? '西侧公园' : '南侧公园'
  if (b.name.includes('西北')) return '西北宅'
  if (b.name.includes('东北')) return '东北宅'
  if (b.name.includes('西南')) return '西南宅'
  if (b.name.includes('东南')) return '东南宅'
  return b.name
}

function draw() {
  const canvas = canvasRef.value
  const wrap = wrapRef.value
  if (!canvas || !wrap) return

  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const cssW = wrap.clientWidth
  const cssH = wrap.clientHeight
  if (cssW !== lastCssW || cssH !== lastCssH) {
    canvas.width = Math.floor(cssW * dpr)
    canvas.height = Math.floor(cssH * dpr)
    canvas.style.width = `${cssW}px`
    canvas.style.height = `${cssH}px`
    lastCssW = cssW
    lastCssH = cssH
    mapCacheKey = ''
  }

  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)

  const world = props.world
  if (!world) {
    ctx.fillStyle = '#5f9a72'
    ctx.fillRect(0, 0, cssW, cssH)
    return
  }

  const size = world.mapSize
  const pad = 20
  const cell = Math.max(14, Math.floor(Math.min((cssW - pad * 2) / size, (cssH - pad * 2) / size)))
  const mapW = cell * size
  const mapH = cell * size
  const ox = Math.floor((cssW - mapW) / 2)
  const oy = Math.floor((cssH - mapH) / 2)
  view = { cell, ox, oy, mapW, mapH }

  const cached = ensureMapCache(world, cssW, cssH, cell, ox, oy, mapW, mapH)
  if (cached) ctx.drawImage(cached, 0, 0)

  // 喷泉水花
  for (const b of world.buildings) {
    if (b.type !== 'PLAZA') continue
    const cx = ox + (b.x + b.width / 2) * cell
    const cy = oy + (b.y + b.height / 2) * cell
    const bob = Math.sin(t0 / 260) * cell * 0.12
    ctx.fillStyle = 'rgba(255,255,255,0.85)'
    ctx.beginPath()
    ctx.arc(cx, cy - cell * 0.55 - bob, Math.max(3, cell * 0.1), 0, Math.PI * 2)
    ctx.fill()
    ctx.beginPath()
    ctx.arc(cx - cell * 0.12, cy - cell * 0.35 - bob * 0.6, Math.max(2, cell * 0.06), 0, Math.PI * 2)
    ctx.fill()
  }

  // 角色（放大到约 2 格高，按 y 排序）
  const agents = [...world.agents].sort((a, b) => {
    const pa = renderPos.get(a.id) ?? { x: a.x, y: a.y }
    const pb = renderPos.get(b.id) ?? { x: b.x, y: b.y }
    return pa.y - pb.y
  })
  for (const a of agents) {
    const p = renderPos.get(a.id) ?? { x: a.x, y: a.y }
    drawAgent(ctx, a, p.x, p.y, ox, oy, cell, a.id === props.selectedId)
  }
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
  const walk = agent.state === 'WALKING'
  const bob = walk ? Math.sin(t0 / 110 + fx * 4) * 2.5 : Math.sin(t0 / 480 + fy) * 0.8
  const cx = ox + (fx + 0.5) * cell
  const footY = oy + (fy + 0.85) * cell + bob

  // 角色高度约 2.1 格，明显可见
  const drawH = cell * 2.15
  const drawW = drawH * (96 / 128)

  const sprite = assets?.chars[agent.id]
  const dx = cx - drawW / 2
  const dy = footY - drawH

  // 选中光环
  if (selected) {
    ctx.strokeStyle = 'rgba(240, 200, 90, 0.95)'
    ctx.lineWidth = 3
    ctx.beginPath()
    ctx.ellipse(cx, footY - 2, drawW * 0.42, cell * 0.18, 0, 0, Math.PI * 2)
    ctx.stroke()
  }

  // 影
  ctx.fillStyle = 'rgba(20, 30, 20, 0.3)'
  ctx.beginPath()
  ctx.ellipse(cx, footY, drawW * 0.32, cell * 0.12, 0, 0, Math.PI * 2)
  ctx.fill()

  if (sprite) {
    ctx.drawImage(sprite, dx, dy, drawW, drawH)
  } else {
    // 兜底小人
    ctx.fillStyle = agent.color
    roundRect(ctx, cx - cell * 0.25, footY - cell * 1.1, cell * 0.5, cell * 0.7, 6)
    ctx.fill()
    ctx.fillStyle = '#f3d2b3'
    ctx.beginPath()
    ctx.arc(cx, footY - cell * 1.25, cell * 0.28, 0, Math.PI * 2)
    ctx.fill()
  }

  // 名牌
  ctx.font = `800 ${Math.max(12, Math.floor(cell * 0.4))}px Nunito, sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'top'
  const name = agent.name
  const nw = ctx.measureText(name).width
  const ny = footY + 4
  ctx.fillStyle = 'rgba(18, 28, 22, 0.78)'
  roundRect(ctx, cx - nw / 2 - 7, ny, nw + 14, 18, 7)
  ctx.fill()
  ctx.fillStyle = '#f7f2e8'
  ctx.fillText(name, cx, ny + 2)

  drawBubble(ctx, agent.bubble || agent.state, cx, dy + cell * 0.1)
}

function drawBubble(ctx: CanvasRenderingContext2D, text: string, cx: number, bottomY: number) {
  const maxLen = 11
  const label = text.length > maxLen ? text.slice(0, maxLen) + '…' : text
  ctx.font = `700 12px Nunito, sans-serif`
  const tw = ctx.measureText(label).width
  const bw = tw + 18
  const bh = 24
  const bx = Math.round(cx - bw / 2)
  const by = Math.round(bottomY - bh)

  ctx.fillStyle = 'rgba(255, 252, 245, 0.96)'
  roundRect(ctx, bx, by, bw, bh, 10)
  ctx.fill()
  ctx.strokeStyle = 'rgba(80, 60, 40, 0.18)'
  ctx.stroke()

  ctx.beginPath()
  ctx.moveTo(cx - 6, by + bh)
  ctx.lineTo(cx + 6, by + bh)
  ctx.lineTo(cx, by + bh + 6)
  ctx.closePath()
  ctx.fill()

  ctx.fillStyle = '#3a3228'
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
  const rr = Math.min(r, w / 2, h / 2)
  ctx.beginPath()
  ctx.moveTo(x + rr, y)
  ctx.arcTo(x + w, y, x + w, y + h, rr)
  ctx.arcTo(x + w, y + h, x, y + h, rr)
  ctx.arcTo(x, y + h, x, y, rr)
  ctx.arcTo(x, y, x + w, y, rr)
  ctx.closePath()
}

function onClick(ev: MouseEvent) {
  const world = props.world
  if (!world || !view.cell) return
  const canvas = canvasRef.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  const mx = ev.clientX - rect.left
  const my = ev.clientY - rect.top
  const { cell, ox, oy } = view

  let best: Agent | null = null
  let bestDist = Infinity
  for (const a of world.agents) {
    const p = renderPos.get(a.id) ?? { x: a.x, y: a.y }
    const cx = ox + (p.x + 0.5) * cell
    const footY = oy + (p.y + 0.85) * cell
    const d = Math.hypot(mx - cx, my - (footY - cell))
    if (d < cell * 1.2 && d < bestDist) {
      best = a
      bestDist = d
    }
  }
  if (best) emit('select', best.id)
}
</script>

<template>
  <div ref="wrapRef" class="canvas-wrap">
    <div class="vignette" />
    <canvas ref="canvasRef" @click="onClick" />
    <div v-if="syncing" class="sync-overlay">
      <div class="sync-card">
        <div class="spinner" />
        <div class="sync-title">正在同步小镇…</div>
        <div class="sync-sub">地图已就绪，正在连接后端世界状态</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.canvas-wrap {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 50% 0%, #9ecfad 0%, transparent 55%),
    linear-gradient(165deg, #5f9a72 0%, #3d6b52 48%, #2f5542 100%);
}

.vignette {
  pointer-events: none;
  position: absolute;
  inset: 0;
  background: radial-gradient(ellipse at center, transparent 40%, rgba(15, 30, 22, 0.4) 100%);
  z-index: 1;
}

canvas {
  position: relative;
  z-index: 0;
  display: block;
  width: 100%;
  height: 100%;
  cursor: pointer;
}

.sync-overlay {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-items: center;
  background: rgba(20, 35, 28, 0.28);
  pointer-events: none;
}

.sync-card {
  padding: 16px 22px;
  background: rgba(20, 32, 24, 0.82);
  border: 1px solid rgba(212, 180, 131, 0.35);
  color: #f4f0e6;
  text-align: center;
  min-width: 200px;
}

.spinner {
  width: 22px;
  height: 22px;
  margin: 0 auto 10px;
  border: 2px solid rgba(212, 180, 131, 0.25);
  border-top-color: #e8c98a;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.sync-title {
  font-weight: 800;
  font-size: 0.95rem;
}

.sync-sub {
  margin-top: 4px;
  font-size: 0.75rem;
  color: rgba(244, 240, 230, 0.7);
  font-weight: 600;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
