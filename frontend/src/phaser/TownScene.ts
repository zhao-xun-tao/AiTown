import Phaser from 'phaser'
import { AnimFSM } from './AnimFSM'
import { buildCollisionGrid, isWalkable, MAP_SIZE } from './collision'
import { resolveDayNight, type DayNightState } from './DayNight'
import { findPath, type Point } from './pathfinding'
import type { Agent, Building, WorldState, ZoneType } from '../types/world'

const TILE = 32

const T = {
  GRASS0: 0,
  GRASS1: 1,
  GRASS2: 2,
  GRASS3: 3,
  PATH0: 4,
  PATH1: 5,
  PATH2: 6,
  WATER: 7,
  FOUNTAIN: 8,
  PATH3: 9,
} as const

const CHAR_KEYS = ['linyue', 'chenmo', 'suqing'] as const
type CharKey = (typeof CHAR_KEYS)[number]

function agentToChar(id: string): CharKey {
  const s = id.toLowerCase()
  if (s.includes('chenmo') || s.includes('feng')) return 'chenmo'
  if (s.includes('suqing') || s.includes('lu') || s.includes('hao')) return 'suqing'
  if (s.includes('linyue')) return 'linyue'
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0
  return CHAR_KEYS[Math.abs(h) % CHAR_KEYS.length]
}

type AgentView = {
  sprite: Phaser.GameObjects.Sprite
  nameTag: Phaser.GameObjects.Text
  bubble: Phaser.GameObjects.Text
  bubbleBg: Phaser.GameObjects.Graphics
  thinkIcon: Phaser.GameObjects.Text
  selectRing: Phaser.GameObjects.Graphics
  fsm: AnimFSM
  logicX: number
  logicY: number
  followPath: Point[]
  targetX: number
  targetY: number
  agentState: Agent['state']
  arrivePause: number
  repathCooldown: number
  bobPhase: number
  lastBubble: string
  baseY: number
}

type BuildingView = {
  building: Building
  image: Phaser.GameObjects.Image
  label: Phaser.GameObjects.Text
  highlight: Phaser.GameObjects.Graphics
}

/**
 * AI Town 完整场景：多层地图 / Y-Sort / FSM / 混合寻路 / 昼夜灯光 / 交互 / Game Feel
 */
export class TownScene extends Phaser.Scene {
  private groundLayer: Phaser.Tilemaps.TilemapLayer | null = null
  private pathLayer: Phaser.Tilemaps.TilemapLayer | null = null
  private agents = new Map<string, AgentView>()
  private buildingViews: BuildingView[] = []
  private worldObjects: Phaser.GameObjects.GameObject[] = []
  private lightPoints: { x: number; y: number; r: number }[] = []
  private selectedId: string | null = null
  private selectedBuildingId: string | null = null
  private onSelectAgent: ((id: string) => void) | null = null
  private onSelectBuilding: ((id: string | null) => void) | null = null
  private lastWorldKey = ''
  private collision: boolean[][] = []
  private buildings: Building[] = []
  private pixelsPerSec = 78
  private fitZoom = 1
  private userZoom = 1
  private followSelected = true
  private dragging = false
  private dragLast = { x: 0, y: 0 }
  private readonly mapPx = MAP_SIZE * TILE
  private readonly charScale = 2.6
  private veil!: Phaser.GameObjects.Graphics
  private lightsGfx!: Phaser.GameObjects.Graphics
  private phaseLabel!: Phaser.GameObjects.Text
  private toast!: Phaser.GameObjects.Text
  private dayState: DayNightState = resolveDayNight(8)
  private lastEventSig = ''
  private timeMs = 0

  constructor() {
    super('TownScene')
  }

  preload() {
    this.load.image('tileset', '/assets/tileset.png')
    this.load.image('buildings', '/assets/buildings.png')
    for (const k of CHAR_KEYS) {
      this.load.spritesheet(k, `/assets/characters/${k}.png`, {
        frameWidth: TILE,
        frameHeight: TILE,
      })
    }
  }

  create() {
    this.cameras.main.setBackgroundColor('#3d7a38')
    const pad = TILE * 2
    this.cameras.main.setBounds(-pad, -pad, this.mapPx + pad * 2, this.mapPx + pad * 2)

    this.registerBuildingFrames()
    this.createAnims()
    this.buildTileLayers(null)
    this.setupCameraControls()

    this.veil = this.add.graphics().setDepth(9000)
    this.lightsGfx = this.add.graphics().setDepth(9001)
    this.phaseLabel = this.add
      .text(12, 12, '', {
        fontFamily: 'monospace',
        fontSize: '12px',
        color: '#f4efe6',
        backgroundColor: '#00000099',
        padding: { x: 8, y: 5 },
      })
      .setScrollFactor(0)
      .setDepth(10001)

    this.toast = this.add
      .text(this.scale.width / 2, 48, '', {
        fontFamily: 'monospace',
        fontSize: '14px',
        color: '#ffe08a',
        backgroundColor: '#3a2010cc',
        padding: { x: 12, y: 8 },
        align: 'center',
      })
      .setOrigin(0.5, 0)
      .setScrollFactor(0)
      .setDepth(10002)
      .setAlpha(0)

    this.add
      .text(12, 36, '滚轮缩放 · 拖拽平移 · 双击复位 · 点角色/建筑', {
        fontFamily: 'monospace',
        fontSize: '11px',
        color: '#d8e0d0',
        backgroundColor: '#00000066',
        padding: { x: 6, y: 3 },
      })
      .setScrollFactor(0)
      .setDepth(10001)

    this.scale.on('resize', this.onResize, this)
    this.onResize(this.scale.gameSize)
  }

  setSelectHandler(fn: (id: string) => void) {
    this.onSelectAgent = fn
  }

  setBuildingSelectHandler(fn: (id: string | null) => void) {
    this.onSelectBuilding = fn
  }

  setSelected(id: string | null) {
    this.selectedId = id
    if (id) {
      this.followSelected = true
      this.selectedBuildingId = null
      this.refreshBuildingHighlights()
    }
    for (const [aid, v] of this.agents) {
      const on = aid === id
      v.nameTag.setColor(on ? '#ffe08a' : '#f4efe6')
      v.sprite.setScale(on ? this.charScale * 1.1 : this.charScale)
      v.selectRing.setVisible(on)
    }
  }

  setSelectedBuilding(id: string | null) {
    this.selectedBuildingId = id
    this.refreshBuildingHighlights()
  }

  resetCamera() {
    this.userZoom = 1
    this.followSelected = false
    this.applyZoom()
    this.cameras.main.centerOn(this.mapPx / 2, this.mapPx / 2)
  }

  flashWorldEvent(content: string) {
    this.toast.setText(`⚡ 世界事件：${content.slice(0, 48)}${content.length > 48 ? '…' : ''}`)
    this.toast.setX(this.scale.width / 2)
    this.tweens.killTweensOf(this.toast)
    this.toast.setAlpha(1).setScale(0.92)
    this.tweens.add({
      targets: this.toast,
      scale: 1,
      duration: 180,
      yoyo: false,
    })
    this.tweens.add({
      targets: this.toast,
      alpha: 0,
      delay: 3200,
      duration: 600,
    })
    // 短暂全屏闪白提示
    this.cameras.main.flash(220, 255, 220, 160)
  }

  syncWorld(world: WorldState | null) {
    if (!world) return
    const key = `${world.mapSize}:${world.buildings.map((b) => `${b.id}@${b.x},${b.y}`).join('|')}`
    if (key !== this.lastWorldKey) {
      this.lastWorldKey = key
      this.buildings = world.buildings
      this.collision = buildCollisionGrid(world.buildings)
      this.buildTileLayers(world)
      this.rebuildWorldObjects(world)
    }
    this.syncAgents(world.agents)
    this.applyDayNight(world.gameTime?.hour ?? 8, world.gameTime?.minute ?? 0)

    const events = world.activeWorldEvents ?? []
    const sig = events.map((e) => e.id).join(',')
    if (sig && sig !== this.lastEventSig && this.lastEventSig !== '') {
      const newest = events[0]
      if (newest) this.flashWorldEvent(newest.content)
    }
    this.lastEventSig = sig
  }

  private applyDayNight(hour: number, minute: number) {
    this.dayState = resolveDayNight(hour, minute)
    this.phaseLabel.setText(
      `${this.dayState.label}  ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`,
    )
    this.cameras.main.setBackgroundColor(this.dayState.ambient)
    this.redrawLighting()
  }

  private redrawLighting() {
    this.veil.clear()
    this.lightsGfx.clear()
    const a = this.dayState.veilAlpha
    if (a <= 0.01) return

    this.veil.fillStyle(0x0a1428, a)
    this.veil.fillRect(-TILE * 2, -TILE * 2, this.mapPx + TILE * 4, this.mapPx + TILE * 4)

    const strength = this.dayState.lightStrength
    for (const L of this.lightPoints) {
      this.lightsGfx.fillStyle(0xffd27a, 0.18 * strength)
      this.lightsGfx.fillCircle(L.x, L.y, L.r * 1.6)
      this.lightsGfx.fillStyle(0xffe6a8, 0.28 * strength)
      this.lightsGfx.fillCircle(L.x, L.y, L.r * 0.85)
    }
    // 建筑窗户暖光
    for (const bv of this.buildingViews) {
      const b = bv.building
      const cx = (b.x + b.width / 2) * TILE
      const cy = (b.y + b.height * 0.55) * TILE
      this.lightsGfx.fillStyle(0xffc878, 0.22 * strength)
      this.lightsGfx.fillCircle(cx - 18, cy, 22)
      this.lightsGfx.fillCircle(cx + 18, cy, 22)
    }
  }

  private registerBuildingFrames() {
    const tex = this.textures.get('buildings')
    if (!tex || tex.key === '__MISSING') return
    ;[
      'house',
      'cafe',
      'library',
      'tree',
      'bench',
      'tree2',
      'tree3',
      'bush',
      'bush2',
      'flowers',
      'fence',
      'lamp',
      'flowers2',
    ].forEach((n) => {
      if (tex.has(n)) tex.remove(n)
    })
    const add = (name: string, x: number, y: number, w: number, h: number) => tex.add(name, 0, x, y, w, h)
    add('house', 0, 0, 64, 64)
    add('cafe', 64, 0, 96, 80)
    add('library', 160, 0, 96, 80)
    add('tree', 0, 80, 32, 48)
    add('bench', 40, 80, 32, 32)
    add('tree2', 80, 80, 32, 48)
    add('tree3', 120, 80, 32, 48)
    add('bush', 160, 80, 32, 32)
    add('bush2', 192, 80, 32, 32)
    add('flowers', 224, 80, 32, 32)
    add('fence', 0, 160, 32, 32)
    add('lamp', 64, 144, 32, 48)
    add('flowers2', 96, 160, 32, 32)
  }

  private createAnims() {
    for (const key of CHAR_KEYS) {
      const dirs = [
        { name: 'down', row: 0 },
        { name: 'left', row: 1 },
        { name: 'right', row: 2 },
        { name: 'up', row: 3 },
      ] as const
      for (const d of dirs) {
        const walkKey = `${key}-walk-${d.name}`
        if (!this.anims.exists(walkKey)) {
          this.anims.create({
            key: walkKey,
            frames: this.anims.generateFrameNumbers(key, { start: d.row * 4, end: d.row * 4 + 3 }),
            frameRate: 9,
            repeat: -1,
          })
        }
        const idleKey = `${key}-idle-${d.name}`
        if (!this.anims.exists(idleKey)) {
          this.anims.create({
            key: idleKey,
            frames: [{ key, frame: d.row * 4 }],
            frameRate: 1,
          })
        }
      }
    }
  }

  private buildTileLayers(world: WorldState | null) {
    this.groundLayer?.destroy()
    this.pathLayer?.destroy()

    const ground: number[][] = []
    const path: number[][] = []
    for (let y = 0; y < MAP_SIZE; y++) {
      const grow: number[] = []
      const prow: number[] = []
      for (let x = 0; x < MAP_SIZE; x++) {
        const { g, p } = this.pickTiles(x, y, world)
        grow.push(g)
        prow.push(p)
      }
      ground.push(grow)
      path.push(prow)
    }

    const gMap = this.make.tilemap({ data: ground, tileWidth: TILE, tileHeight: TILE })
    const gTiles = gMap.addTilesetImage('tileset', 'tileset', TILE, TILE)!
    this.groundLayer = gMap.createLayer(0, gTiles, 0, 0)
    this.groundLayer?.setDepth(0)

    const pMap = this.make.tilemap({ data: path, tileWidth: TILE, tileHeight: TILE })
    const pTiles = pMap.addTilesetImage('tileset', 'tileset', TILE, TILE)!
    this.pathLayer = pMap.createLayer(0, pTiles, 0, 0)
    this.pathLayer?.setDepth(1)
  }

  private pickTiles(x: number, y: number, world: WorldState | null): { g: number; p: number } {
    const b = world ? this.buildingAt(world.buildings, x, y) : null
    const onHRoad = y >= 8 && y <= 11
    const onVRoad = x >= 8 && x <= 11
    const toCafe = y >= 8 && y <= 11 && x >= 12
    const toLibrary = x >= 8 && x <= 11 && y <= 7
    const toParkW = y >= 8 && y <= 11 && x <= 7
    const toParkS = x >= 8 && x <= 11 && y >= 12
    const toHouseNW = x >= 2 && x <= 4 && y >= 4 && y <= 8
    const toHouseNE = x >= 15 && x <= 17 && y >= 4 && y <= 8
    const toHouseSW = x >= 2 && x <= 4 && y >= 11 && y <= 15
    const toHouseSE = x >= 15 && x <= 17 && y >= 11 && y <= 15
    const plaza = b?.type === 'PLAZA' || (x >= 7 && x <= 12 && y >= 7 && y <= 12)
    const spur =
      toCafe || toLibrary || toParkW || toParkS || toHouseNW || toHouseNE || toHouseSW || toHouseSE
    const grass = (x + y * 3) % 4
    const empty = -1

    if (plaza && x >= 9 && x <= 10 && y >= 9 && y <= 10) return { g: T.FOUNTAIN, p: empty }
    if (plaza && (x === 8 || x === 11) && (y === 8 || y === 11)) return { g: T.WATER, p: empty }
    if (onHRoad || onVRoad || plaza || spur) {
      const v = (x + y) % 3
      return { g: grass, p: v === 0 ? T.PATH0 : v === 1 ? T.PATH1 : T.PATH2 }
    }
    if ((y === 7 || y === 12) && x >= 6 && x <= 13 || (x === 7 || x === 12) && y >= 6 && y <= 13) {
      return { g: grass, p: T.PATH3 }
    }
    if (b?.type === 'PARK') return { g: (x + y) % 2 === 0 ? T.GRASS2 : T.GRASS3, p: empty }
    return { g: grass, p: empty }
  }

  private buildingAt(buildings: Building[], gx: number, gy: number) {
    return (
      buildings.find(
        (b) => gx >= b.x && gx < b.x + b.width && gy >= b.y && gy < b.y + b.height,
      ) ?? null
    )
  }

  private clearWorldObjects() {
    for (const o of this.worldObjects) o.destroy()
    this.worldObjects = []
    this.buildingViews = []
    this.lightPoints = []
  }

  private rebuildWorldObjects(world: WorldState) {
    this.clearWorldObjects()
    const place = (obj: Phaser.GameObjects.GameObject) => {
      this.worldObjects.push(obj)
    }

    for (const b of world.buildings) {
      if (b.type === 'PARK') {
        place(this.makeProp('tree', b.x + 1, b.y + 1, 1.2))
        place(this.makeProp('tree2', b.x + b.width - 2, b.y + 1, 1.15))
        place(this.makeProp('tree3', b.x + 2, b.y + b.height - 2, 1.1))
        place(this.makeProp('bush', b.x + 1, b.y + 3))
        place(this.makeProp('bench', b.x + Math.floor(b.width / 2), b.y + b.height - 2, 1.15))
        place(this.makeProp('flowers', b.x + b.width - 2, b.y + 3))
      }
      if (b.type === 'HOUSE' || b.type === 'CAFE' || b.type === 'LIBRARY') {
        const doorY = b.y + b.height - 1
        for (let gx = b.x; gx < b.x + b.width; gx++) {
          if (gx === b.x + Math.floor(b.width / 2)) continue
          place(this.makeProp('fence', gx, doorY, 0.95))
        }
        place(this.makeProp('flowers', b.x, doorY))
        place(this.makeProp('flowers2', b.x + b.width - 1, doorY))
      }
    }

    const roadside: [number, number, string, number?][] = [
      [5, 8, 'tree3', 1.15],
      [5, 11, 'tree', 1.1],
      [12, 5, 'tree2', 1.15],
      [14, 5, 'bush', 1],
      [4, 12, 'tree2', 1.1],
      [6, 14, 'bush2', 1],
      [15, 12, 'tree', 1.15],
      [17, 13, 'bush', 1],
      [2, 6, 'bush2', 1],
      [17, 6, 'flowers', 1],
      [3, 16, 'tree3', 1.05],
      [16, 16, 'tree2', 1.05],
      [7, 3, 'bush', 1],
      [12, 16, 'flowers2', 1],
      [8, 7, 'lamp', 1],
      [11, 7, 'lamp', 1],
      [8, 12, 'lamp', 1],
      [11, 12, 'lamp', 1],
      [13, 9, 'lamp', 1],
      [6, 9, 'lamp', 1],
    ]
    for (const [gx, gy, frame, sc] of roadside) {
      place(this.makeProp(frame, gx, gy, sc ?? 1))
      if (frame === 'lamp') {
        this.lightPoints.push({ x: gx * TILE + 16, y: gy * TILE + 8, r: 48 })
      }
    }

    for (let gy = 1; gy < MAP_SIZE - 1; gy += 3) {
      for (let gx = 1; gx < MAP_SIZE - 1; gx += 3) {
        if ((gx + gy) % 2 === 0) continue
        if ((gy >= 8 && gy <= 11) || (gx >= 8 && gx <= 11)) continue
        const b = this.buildingAt(world.buildings, gx, gy)
        if (b && b.type !== 'PARK') continue
        place(this.makeProp((gx + gy) % 5 === 0 ? 'bush' : 'flowers', gx, gy, 0.9))
      }
    }

    for (const b of world.buildings) {
      if (b.type === 'ROAD' || b.type === 'PLAZA' || b.type === 'PARK') continue
      place(this.makeBuilding(b))
    }
    this.refreshBuildingHighlights()
    this.redrawLighting()
  }

  private makeProp(frame: string, gx: number, gy: number, scale = 1) {
    const img = this.add.image(gx * TILE + 16, gy * TILE + 30, 'buildings', frame)
    img.setOrigin(0.5, 1)
    img.setScale(scale)
    img.setDepth(gy * TILE + 24)
    return img
  }

  private makeBuilding(b: Building) {
    const frame = this.buildingFrame(b.type)
    const cx = (b.x + b.width / 2) * TILE
    const footY = (b.y + b.height) * TILE
    const img = this.add.image(cx, footY - 2, 'buildings', frame)
    img.setOrigin(0.5, 1)
    const nativeW = frame === 'house' ? 64 : 96
    const nativeH = frame === 'house' ? 64 : 80
    const sx = (b.width * TILE * 1.08) / nativeW
    const sy = (b.height * TILE * 0.98) / nativeH
    img.setScale(Math.max(sx, sy))
    img.setDepth((b.y + b.height - 1) * TILE - 2)
    img.setInteractive({ useHandCursor: true })
    img.on('pointerdown', (pointer: Phaser.Input.Pointer) => {
      pointer.event?.stopPropagation?.()
      this.followSelected = false
      this.selectedBuildingId = b.id
      this.selectedId = null
      this.refreshBuildingHighlights()
      this.onSelectBuilding?.(b.id)
      this.cameras.main.pan(cx, footY - img.displayHeight * 0.4, 280, 'Sine.easeInOut')
    })

    const label = this.add
      .text(cx, footY - img.displayHeight - 4, b.name, {
        fontFamily: 'monospace',
        fontSize: '12px',
        color: '#f7f0e4',
        backgroundColor: '#000000aa',
        padding: { x: 4, y: 2 },
      })
      .setOrigin(0.5, 1)
      .setDepth((b.y + b.height - 1) * TILE + 1)

    const highlight = this.add.graphics().setDepth((b.y + b.height - 1) * TILE - 3)
    this.buildingViews.push({ building: b, image: img, label, highlight })
    this.worldObjects.push(label, highlight)
    this.lightPoints.push({ x: cx, y: footY - img.displayHeight * 0.45, r: 56 })
    return img
  }

  private refreshBuildingHighlights() {
    for (const bv of this.buildingViews) {
      bv.highlight.clear()
      const on = bv.building.id === this.selectedBuildingId
      bv.label.setColor(on ? '#ffe08a' : '#f7f0e4')
      if (!on) continue
      const b = bv.building
      bv.highlight.lineStyle(2, 0xffe08a, 0.95)
      bv.highlight.strokeRect(b.x * TILE + 2, b.y * TILE + 2, b.width * TILE - 4, b.height * TILE - 4)
    }
  }

  private buildingFrame(type: ZoneType): string {
    if (type === 'CAFE') return 'cafe'
    if (type === 'LIBRARY') return 'library'
    return 'house'
  }

  private syncAgents(list: Agent[]) {
    const seen = new Set<string>()
    for (const a of list) {
      seen.add(a.id)
      let view = this.agents.get(a.id)
      if (!view) {
        view = this.spawnAgent(a)
        this.agents.set(a.id, view)
      }
      this.applyAgentLogic(view, a)
      this.setBubble(view, this.bubbleText(a))
    }
    for (const [id, v] of this.agents) {
      if (seen.has(id)) continue
      v.sprite.destroy()
      v.nameTag.destroy()
      v.bubble.destroy()
      v.bubbleBg.destroy()
      v.thinkIcon.destroy()
      v.selectRing.destroy()
      this.agents.delete(id)
    }
    this.setSelected(this.selectedId)
  }

  private bubbleText(a: Agent) {
    return (
      (a.bubble && a.bubble.trim()) ||
      (a.lastSpeech && a.lastSpeech.trim()) ||
      (a.lastThought ? a.lastThought.slice(0, 28) : '')
    )
  }

  private applyAgentLogic(view: AgentView, a: Agent) {
    view.logicX = a.x
    view.logicY = a.y
    view.agentState = a.state
    const tx = a.targetX ?? a.x
    const ty = a.targetY ?? a.y
    view.targetX = tx
    view.targetY = ty

    const backendPath = (a.path ?? []).map((p) => ({ x: p.x, y: p.y }))
    const logicPx = a.x * TILE + TILE / 2
    const logicPy = a.y * TILE + TILE / 2
    const distLogic = Math.hypot(view.sprite.x - logicPx, view.sprite.y - logicPy)

    if (backendPath.length > 0) {
      view.followPath =
        distLogic <= 10
          ? dedupePath(backendPath)
          : dedupePath([{ x: a.x, y: a.y }, ...backendPath])
      view.repathCooldown = 0
      return
    }

    if ((tx !== a.x || ty !== a.y) && view.repathCooldown <= 0) {
      const from = {
        x: Math.round((view.sprite.x - TILE / 2) / TILE),
        y: Math.round((view.sprite.y - TILE / 2) / TILE),
      }
      const local = findPath(this.collision, from.x, from.y, tx, ty, this.buildings)
      view.followPath = local.length
        ? dedupePath([{ x: from.x, y: from.y }, ...local])
        : distLogic > TILE * 0.4
          ? [{ x: a.x, y: a.y }]
          : []
      view.repathCooldown = 20
    } else if (tx === a.x && ty === a.y && a.state !== 'WALKING' && distLogic > 2) {
      view.followPath = [{ x: a.x, y: a.y }]
    }
  }

  private spawnAgent(a: Agent): AgentView {
    const key = agentToChar(a.id)
    const px = a.x * TILE + TILE / 2
    const py = a.y * TILE + TILE / 2
    const sprite = this.add.sprite(px, py, key, 0)
    sprite.setOrigin(0.5, 0.85)
    sprite.setScale(this.charScale)
    sprite.setDepth(py + 8)
    sprite.setInteractive({ useHandCursor: true })
    sprite.on('pointerdown', (pointer: Phaser.Input.Pointer) => {
      pointer.event?.stopPropagation?.()
      this.followSelected = true
      this.selectedBuildingId = null
      this.refreshBuildingHighlights()
      this.onSelectAgent?.(a.id)
    })

    const nameTag = this.add
      .text(px, py - 36, a.name, {
        fontFamily: 'monospace',
        fontSize: '13px',
        color: '#f4efe6',
        stroke: '#1a1a14',
        strokeThickness: 4,
      })
      .setOrigin(0.5, 1)

    const bubbleBg = this.add.graphics()
    const bubble = this.add
      .text(px, py - 36, '', {
        fontFamily: 'monospace',
        fontSize: '10px',
        color: '#2a2418',
        wordWrap: { width: 130 },
        align: 'center',
      })
      .setOrigin(0.5, 1)
      .setVisible(false)

    const thinkIcon = this.add
      .text(px + 14, py - 30, '', {
        fontFamily: 'monospace',
        fontSize: '12px',
        color: '#c8e0ff',
        stroke: '#1a1a14',
        strokeThickness: 2,
      })
      .setOrigin(0.5, 1)

    const selectRing = this.add.graphics().setVisible(false)

    sprite.play(`${key}-idle-down`)

    return {
      sprite,
      nameTag,
      bubble,
      bubbleBg,
      thinkIcon,
      selectRing,
      fsm: new AnimFSM(),
      logicX: a.x,
      logicY: a.y,
      followPath: [],
      targetX: a.x,
      targetY: a.y,
      agentState: a.state,
      arrivePause: 0,
      repathCooldown: 0,
      bobPhase: Math.random() * Math.PI * 2,
      lastBubble: '',
      baseY: py,
    }
  }

  private setBubble(view: AgentView, text: string) {
    view.bubbleBg.clear()
    if (!text) {
      view.bubble.setVisible(false)
      return
    }
    const short = text.length > 40 ? `${text.slice(0, 38)}…` : text
    const changed = short !== view.lastBubble
    view.lastBubble = short
    view.bubble.setText(short)
    view.bubble.setVisible(true)
    if (changed) {
      view.bubble.setScale(0.85)
      this.tweens.add({ targets: view.bubble, scale: 1, duration: 140, ease: 'Back.easeOut' })
    }
    const b = view.bubble.getBounds()
    const pad = 4
    view.bubbleBg.fillStyle(0xf7f1e4, 0.94)
    view.bubbleBg.lineStyle(1, 0x5a4a38, 1)
    view.bubbleBg.fillRoundedRect(b.x - pad, b.y - pad, b.width + pad * 2, b.height + pad * 2, 4)
    view.bubbleBg.strokeRoundedRect(b.x - pad, b.y - pad, b.width + pad * 2, b.height + pad * 2, 4)
  }

  update(_t: number, dt: number) {
    const dtSec = Math.min(dt, 50) / 1000
    this.timeMs += dt

    if (this.followSelected && this.selectedId) {
      const v = this.agents.get(this.selectedId)
      if (v) {
        const cam = this.cameras.main
        const tx = v.sprite.x - cam.width / (2 * cam.zoom)
        const ty = v.sprite.y - cam.height / (2 * cam.zoom)
        cam.scrollX = Phaser.Math.Linear(cam.scrollX, tx, 0.14)
        cam.scrollY = Phaser.Math.Linear(cam.scrollY, ty, 0.14)
      }
    }

    for (const [id, v] of this.agents) {
      if (v.repathCooldown > 0) v.repathCooldown--
      if (v.arrivePause > 0) {
        v.arrivePause -= dt
        this.applyAnim(id, v, 0, 0, false)
        this.layoutHud(v, false)
        continue
      }

      let moving = false
      let dx = 0
      let dy = 0

      while (v.followPath.length > 0) {
        const next = v.followPath[0]
        if (!isWalkable(this.collision, next.x, next.y)) {
          v.followPath.shift()
          if (v.repathCooldown <= 0) {
            const from = {
              x: Math.round((v.sprite.x - TILE / 2) / TILE),
              y: Math.round((v.sprite.y - TILE / 2) / TILE),
            }
            v.followPath = findPath(
              this.collision,
              from.x,
              from.y,
              v.targetX,
              v.targetY,
              this.buildings,
            )
            v.repathCooldown = 15
          }
          break
        }

        const tx = next.x * TILE + TILE / 2
        const ty = next.y * TILE + TILE / 2
        dx = tx - v.sprite.x
        dy = ty - v.baseY
        // baseY tracks logical foot without bob
        const footDx = tx - v.sprite.x
        const footDy = ty - v.baseY
        const dist = Math.hypot(footDx, footDy)
        if (dist < 1.2) {
          v.sprite.x = tx
          v.baseY = ty
          v.followPath.shift()
          if (v.followPath.length === 0) v.arrivePause = 200
          continue
        }
        const step = this.pixelsPerSec * dtSec
        v.sprite.x += (footDx / dist) * Math.min(step, dist)
        v.baseY += (footDy / dist) * Math.min(step, dist)
        moving = true
        dx = footDx
        dy = footDy
        break
      }

      // 步伐 bob
      if (moving) {
        v.bobPhase += dtSec * 14
        v.sprite.y = v.baseY + Math.sin(v.bobPhase) * 1.8
      } else {
        v.sprite.y = Phaser.Math.Linear(v.sprite.y, v.baseY, 0.25)
      }

      this.applyAnim(id, v, dx, dy, moving)
      this.layoutHud(v, moving)
    }

    // 夜灯轻微呼吸
    if (this.dayState.lightStrength > 0.05 && Math.floor(this.timeMs / 400) % 2 === 0) {
      this.redrawLighting()
    }
  }

  private applyAnim(id: string, v: AgentView, dx: number, dy: number, moving: boolean) {
    const key = agentToChar(id)
    const animKey = v.fsm.update(key, dx, dy, moving, v.agentState)
    if (v.sprite.anims.currentAnim?.key !== animKey) v.sprite.play(animKey, true)
    if (v.fsm.mode === 'think') {
      v.thinkIcon.setText('…').setVisible(true)
    } else if (v.fsm.mode === 'talk') {
      v.thinkIcon.setText('♪').setVisible(true)
    } else {
      v.thinkIcon.setVisible(false)
    }
  }

  private layoutHud(v: AgentView, _moving: boolean) {
    const depth = v.sprite.y + 8
    v.sprite.setDepth(depth)
    const nameLift = 18 * this.charScale
    v.nameTag.setPosition(v.sprite.x, v.sprite.y - nameLift).setDepth(depth + 2)
    v.bubble.setPosition(v.sprite.x, v.sprite.y - nameLift - 16).setDepth(depth + 4)
    v.bubbleBg.setDepth(depth + 3)
    v.thinkIcon.setPosition(v.sprite.x + 16, v.sprite.y - nameLift - 4).setDepth(depth + 5)
    v.selectRing.clear()
    if (v.selectRing.visible) {
      v.selectRing.lineStyle(2, 0xffe08a, 0.9)
      v.selectRing.strokeCircle(v.sprite.x, v.baseY + 4, 16)
      v.selectRing.setDepth(depth + 1)
    }
    if (v.bubble.visible) {
      const b = v.bubble.getBounds()
      const pad = 4
      v.bubbleBg.clear()
      v.bubbleBg.fillStyle(0xf7f1e4, 0.94)
      v.bubbleBg.lineStyle(1, 0x5a4a38, 1)
      v.bubbleBg.fillRoundedRect(b.x - pad, b.y - pad, b.width + pad * 2, b.height + pad * 2, 4)
      v.bubbleBg.strokeRoundedRect(b.x - pad, b.y - pad, b.width + pad * 2, b.height + pad * 2, 4)
    }
  }

  private setupCameraControls() {
    this.input.on('wheel', (_p: unknown, _over: unknown, _dx: number, dy: number) => {
      this.followSelected = false
      this.userZoom = Phaser.Math.Clamp(this.userZoom * (dy > 0 ? 0.9 : 1.12), 0.55, 3.5)
      this.applyZoom()
    })

    this.input.on('pointerdown', (pointer: Phaser.Input.Pointer) => {
      if (pointer.event?.detail === 2) {
        this.resetCamera()
        return
      }
      this.dragging = true
      this.dragLast = { x: pointer.x, y: pointer.y }
    })

    this.input.on('pointerup', () => {
      this.dragging = false
    })

    this.input.on('pointermove', (pointer: Phaser.Input.Pointer) => {
      if (!this.dragging || !pointer.isDown) return
      const cam = this.cameras.main
      const z = cam.zoom || 1
      const dx = (this.dragLast.x - pointer.x) / z
      const dy = (this.dragLast.y - pointer.y) / z
      if (Math.abs(dx) + Math.abs(dy) > 0.5) {
        this.followSelected = false
      }
      cam.scrollX += dx
      cam.scrollY += dy
      this.dragLast = { x: pointer.x, y: pointer.y }
    })
  }

  private applyZoom() {
    this.cameras.main.setZoom(Phaser.Math.Clamp(this.fitZoom * this.userZoom, 0.35, 4.5))
  }

  private onResize(size: Phaser.Structs.Size) {
    this.fitZoom = Math.max(size.width / this.mapPx, size.height / this.mapPx) * 1.1
    this.applyZoom()
    this.cameras.main.centerOn(this.mapPx / 2, this.mapPx / 2)
    this.toast?.setX(size.width / 2)
  }
}

function dedupePath(path: Point[]): Point[] {
  const out: Point[] = []
  for (const p of path) {
    const last = out[out.length - 1]
    if (last && last.x === p.x && last.y === p.y) continue
    out.push(p)
  }
  return out
}
