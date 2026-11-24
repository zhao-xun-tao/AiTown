/**
 * 程序化像素素材生成器
 * 运行：npm run assets
 * 输出：frontend/public/assets/{tileset,buildings,characters/*}.png
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { PNG } from 'pngjs'
import { PixelBuf, hex, type RGBA } from './pixel-buf.ts'

const __dirname = dirname(fileURLToPath(import.meta.url))
const OUT = join(__dirname, '..', 'public', 'assets')
const TILE = 32

function savePng(buf: PixelBuf, path: string) {
  const png = new PNG({ width: buf.w, height: buf.h })
  png.data = Buffer.from(buf.data.buffer.slice(
    buf.data.byteOffset,
    buf.data.byteOffset + buf.data.byteLength,
  ))
  mkdirSync(dirname(path), { recursive: true })
  writeFileSync(path, PNG.sync.write(png))
  console.log('✓', path.replace(/\\/g, '/').split('/public/')[1] ?? path)
}

// ---------- 瓦片绘制 ----------

function drawGrass(seed: number): PixelBuf {
  const b = new PixelBuf(TILE, TILE)
  const base = hex('#3d7a38')
  const dark = hex('#2f5f2c')
  const light = hex('#56a34c')
  const tip = hex('#7bc96a')
  const flower = [hex('#e8c96a'), hex('#e07a8a'), hex('#f0e8d8')]
  b.clear(base)
  for (let y = 0; y < TILE; y++) {
    for (let x = 0; x < TILE; x++) {
      const n = PixelBuf.hash(x, y, seed)
      if (n > 0.72) b.set(x, y, light)
      else if (n < 0.18) b.set(x, y, dark)
    }
  }
  // 草簇：竖向短笔画
  for (let i = 0; i < 14; i++) {
    const x = Math.floor(PixelBuf.hash(i, seed, 1) * 28) + 2
    const y = Math.floor(PixelBuf.hash(i, seed, 2) * 26) + 4
    b.set(x, y, tip)
    b.set(x, y - 1, light)
    if (PixelBuf.hash(i, seed, 3) > 0.5) b.set(x + 1, y - 1, tip)
  }
  // 小花
  if (seed % 3 === 0) {
    const fx = 8 + (seed % 5) * 4
    const fy = 10 + (seed % 4) * 5
    const fc = flower[seed % flower.length]
    b.set(fx, fy, fc)
    b.set(fx + 1, fy, fc)
    b.set(fx, fy + 1, hex('#3d7a38'))
  }
  return b
}

function drawPath(variant: number): PixelBuf {
  const b = new PixelBuf(TILE, TILE)
  const mortar = hex('#6b5a42')
  const stoneA = hex('#b8a888')
  const stoneB = hex('#cfc0a0')
  const stoneC = hex('#9e8c6c')
  const hilite = hex('#e4d8bc')
  const shade = hex('#7a6a4e')
  b.clear(mortar)

  // 2x2 石砖带缝
  const cells = [
    [1, 1, 14, 14],
    [17, 1, 14, 14],
    [1, 17, 14, 14],
    [17, 17, 14, 14],
  ]
  cells.forEach(([sx, sy, w, h], i) => {
    const fill = [stoneA, stoneB, stoneC, stoneA][(i + variant) % 4]
    b.fillRect(sx, sy, w, h, fill)
    // 顶边高光 / 底边阴影
    for (let x = 0; x < w; x++) {
      b.set(sx + x, sy, hilite)
      b.set(sx + x, sy + h - 1, shade)
    }
    for (let y = 0; y < h; y++) {
      b.set(sx, sy + y, hilite)
      b.set(sx + w - 1, sy + y, shade)
    }
    // 石面噪点
    for (let k = 0; k < 6; k++) {
      const px = sx + 2 + Math.floor(PixelBuf.hash(i, k, variant) * (w - 4))
      const py = sy + 2 + Math.floor(PixelBuf.hash(k, i, variant + 1) * (h - 4))
      b.set(px, py, shade)
    }
  })
  return b
}

/** 草地→石板路过渡边缘瓦片 */
function drawPathEdge(): PixelBuf {
  const grass = drawGrass(7)
  const path = drawPath(2)
  const b = new PixelBuf(TILE, TILE)
  for (let y = 0; y < TILE; y++) {
    for (let x = 0; x < TILE; x++) {
      // 对角线过渡：左上草、右下路
      const usePath = x + y > TILE + (PixelBuf.hash(x, y, 9) > 0.7 ? 2 : 0)
      const src = usePath ? path : grass
      const i = (y * TILE + x) * 4
      b.set(x, y, [src.data[i], src.data[i + 1], src.data[i + 2], 255])
    }
  }
  // 边缘碎石点缀
  for (let i = 0; i < 8; i++) {
    const x = 6 + Math.floor(PixelBuf.hash(i, 3, 1) * 18)
    const y = 6 + Math.floor(PixelBuf.hash(i, 4, 2) * 18)
    b.set(x, y, hex('#a09070'))
  }
  return b
}

function drawWater(): PixelBuf {
  const b = new PixelBuf(TILE, TILE)
  const deep = hex('#2a6a8a')
  const mid = hex('#3d8fad')
  const lite = hex('#7ec8e0')
  const foam = hex('#d8f0f8')
  b.clear(deep)
  for (let y = 0; y < TILE; y++) {
    for (let x = 0; x < TILE; x++) {
      const wave = Math.sin((x + y * 0.6) * 0.55) * 0.5 + 0.5
      if (wave > 0.65) b.set(x, y, mid)
      if (wave > 0.82) b.set(x, y, lite)
    }
  }
  // 高光波纹
  for (let x = 4; x < 28; x++) {
    const y = 8 + Math.floor(Math.sin(x * 0.7) * 2)
    b.set(x, y, foam)
    b.set(x, 20 + Math.floor(Math.sin(x * 0.5 + 1) * 2), lite)
  }
  return b
}

function drawFountainCenter(): PixelBuf {
  const b = drawWater()
  const stone = hex('#b0b8c0')
  const dark = hex('#6a7380')
  // 中央柱
  b.fillRect(13, 6, 6, 16, stone)
  for (let y = 6; y < 22; y++) b.set(13, y, dark)
  b.fillRect(12, 4, 8, 4, stone)
  b.fillRect(14, 2, 4, 3, hex('#d0d8e0'))
  // 水花
  b.set(12, 3, hex('#e8f6fc'))
  b.set(19, 3, hex('#e8f6fc'))
  b.set(15, 1, hex('#ffffff'))
  return b
}

/** tileset：8 列 x 4 行 */
function buildTileset(): PixelBuf {
  const cols = 8
  const rows = 4
  const sheet = new PixelBuf(cols * TILE, rows * TILE)
  const tiles: PixelBuf[] = [
    drawGrass(0),
    drawGrass(1),
    drawGrass(2),
    drawGrass(3),
    drawPath(0),
    drawPath(1),
    drawPath(2),
    drawWater(),
    drawFountainCenter(),
    drawPathEdge(), // index 9 = PATH3 过渡
    drawGrass(4),
    drawGrass(5),
    // 填充其余为草地变体
    ...Array.from({ length: cols * rows - 12 }, (_, i) => drawGrass(10 + i)),
  ]
  tiles.forEach((t, i) => {
    const col = i % cols
    const row = Math.floor(i / cols)
    sheet.blit(t, col * TILE, row * TILE)
  })
  return sheet
}

// ---------- 建筑（多瓦片拼成，带墙/顶/门窗/投影） ----------

function drawHouse(): PixelBuf {
  const w = 64
  const h = 64
  const b = new PixelBuf(w, h)
  b.clear([0, 0, 0, 0])
  // 投影
  b.fillRect(10, 50, 48, 6, [20, 30, 20, 60])
  // 墙
  b.fillRect(8, 28, 48, 28, hex('#e8d5b8'))
  b.fillRect(8, 28, 48, 3, hex('#d4c09e'))
  // 屋顶
  for (let row = 0; row < 14; row++) {
    const half = 24 - row
    const y = 14 + row
    b.fillRect(32 - half, y, half * 2, 1, hex('#b54a3c'))
    b.set(32 - half, y, hex('#8a3228'))
    b.set(32 + half - 1, y, hex('#8a3228'))
  }
  b.fillRect(8, 28, 48, 2, hex('#9a3a30'))
  // 烟囱
  b.fillRect(42, 12, 8, 16, hex('#8a6a5a'))
  b.fillRect(41, 10, 10, 3, hex('#6a4a3a'))
  // 门
  b.fillRect(28, 38, 10, 18, hex('#6b4428'))
  b.set(35, 47, hex('#e8c96a'))
  // 窗
  b.fillRect(14, 34, 10, 10, hex('#7ec8e8'))
  b.fillRect(42, 34, 10, 10, hex('#7ec8e8'))
  for (const ox of [14, 42]) {
    b.fillRect(ox, 34, 10, 1, hex('#5a4636'))
    b.fillRect(ox, 43, 10, 1, hex('#5a4636'))
    b.fillRect(ox, 34, 1, 10, hex('#5a4636'))
    b.fillRect(ox + 9, 34, 1, 10, hex('#5a4636'))
    b.fillRect(ox + 5, 34, 1, 10, hex('#5a4636'))
    b.fillRect(ox, 39, 10, 1, hex('#5a4636'))
  }
  // 窗台花
  b.fillRect(13, 44, 12, 2, hex('#6b4428'))
  b.set(15, 43, hex('#e07a8a'))
  b.set(18, 42, hex('#e8c96a'))
  b.set(21, 43, hex('#6bc96a'))
  return b
}

function drawCafe(): PixelBuf {
  const b = new PixelBuf(96, 80)
  b.clear([0, 0, 0, 0])
  b.fillRect(12, 62, 72, 8, [20, 30, 20, 55])
  // 墙
  b.fillRect(10, 30, 76, 40, hex('#f0e0cc'))
  // 遮阳棚条纹
  for (let i = 0; i < 10; i++) {
    const c = i % 2 === 0 ? hex('#d4453a') : hex('#f5e6d0')
    b.fillRect(8 + i * 8, 22, 8, 12, c)
  }
  b.fillRect(8, 20, 80, 3, hex('#a03028'))
  // 招牌
  b.fillRect(28, 36, 40, 12, hex('#5c3a28'))
  // 像素字 CAFE（简化色块）
  const ink = hex('#f7e8c4')
  ;[[30, 39], [31, 39], [32, 40], [30, 41], [31, 42], [32, 43], [30, 43],
    [35, 39], [36, 39], [37, 40], [37, 41], [36, 42], [35, 43], [37, 43],
    [40, 39], [41, 39], [42, 40], [40, 41], [41, 41], [40, 42], [40, 43],
    [45, 39], [46, 39], [47, 40], [47, 41], [47, 42], [46, 43], [45, 43],
  ].forEach(([x, y]) => b.set(x, y, ink))
  // 门台阶
  b.fillRect(18, 52, 12, 18, hex('#6b4428'))
  b.fillRect(16, 68, 16, 3, hex('#8a6a4a'))
  b.fillRect(16, 70, 16, 2, hex('#6b4428'))
  // 窗
  b.fillRect(58, 40, 18, 16, hex('#8ec8e8'))
  b.fillRect(58, 40, 18, 1, hex('#5c3a28'))
  b.fillRect(58, 55, 18, 1, hex('#5c3a28'))
  b.fillRect(58, 40, 1, 16, hex('#5c3a28'))
  b.fillRect(75, 40, 1, 16, hex('#5c3a28'))
  b.fillRect(66, 40, 1, 16, hex('#5c3a28'))
  b.fillRect(58, 48, 18, 1, hex('#5c3a28'))
  // 户外桌
  b.fillRect(48, 60, 14, 3, hex('#8b5a2b'))
  b.fillRect(50, 63, 2, 6, hex('#6b4428'))
  b.fillRect(58, 63, 2, 6, hex('#6b4428'))
  return b
}

function drawLibrary(): PixelBuf {
  const b = new PixelBuf(96, 80)
  b.clear([0, 0, 0, 0])
  b.fillRect(10, 64, 76, 8, [20, 30, 20, 55])
  b.fillRect(12, 28, 72, 44, hex('#dfe6f0'))
  // 三角屋顶
  for (let row = 0; row < 16; row++) {
    const half = 40 - row * 2
    b.fillRect(48 - half, 12 + row, half * 2, 1, hex('#3d5a80'))
    b.set(48 - half, 12 + row, hex('#2a4060'))
    b.set(48 + half - 1, 12 + row, hex('#2a4060'))
  }
  // 柱
  for (const x of [18, 44, 70]) {
    b.fillRect(x, 30, 6, 42, hex('#c8d0dc'))
    b.fillRect(x - 1, 28, 8, 3, hex('#b0b8c8'))
  }
  // 窗=书架感
  for (const x of [28, 54]) {
    b.fillRect(x, 34, 12, 28, hex('#6a8fad'))
    for (let y = 36; y < 60; y += 4) {
      b.fillRect(x + 1, y, 10, 2, hex('#8aa8c0'))
      b.fillRect(x + 1, y + 2, 10, 1, hex('#c45c48')) // 书脊色
    }
  }
  // 门
  b.fillRect(42, 48, 12, 24, hex('#3d2a1c'))
  b.set(50, 60, hex('#d4b483'))
  return b
}

function drawTree(): PixelBuf {
  const b = new PixelBuf(32, 48)
  b.clear([0, 0, 0, 0])
  // 影
  b.fillRect(8, 42, 16, 4, [20, 40, 20, 50])
  // 树干
  b.fillRect(13, 28, 6, 16, hex('#6b4428'))
  b.fillRect(12, 28, 1, 16, hex('#4a2e18'))
  b.fillRect(18, 28, 1, 16, hex('#8a5a38'))
  // 树冠分层
  const layers: [number, number, number, RGBA][] = [
    [6, 14, 10, hex('#1f6b2e')],
    [4, 10, 12, hex('#2f8f3a')],
    [8, 6, 11, hex('#3da34a')],
    [10, 4, 9, hex('#57c466')],
  ]
  layers.forEach(([x, y, r, c]) => {
    for (let j = -r; j <= r; j++) {
      for (let i = -r; i <= r; i++) {
        if (i * i + j * j <= r * r) b.set(16 + i + (x - 8), y + 8 + j, c)
      }
    }
  })
  // 高光叶
  b.set(12, 10, hex('#8fd47f'))
  b.set(18, 8, hex('#8fd47f'))
  return b
}

function drawBench(): PixelBuf {
  const b = new PixelBuf(32, 32)
  b.clear([0, 0, 0, 0])
  b.fillRect(4, 14, 24, 4, hex('#9a6b2f'))
  b.fillRect(4, 12, 24, 2, hex('#b8843a'))
  b.fillRect(6, 18, 3, 8, hex('#6b4428'))
  b.fillRect(23, 18, 3, 8, hex('#6b4428'))
  b.fillRect(4, 18, 24, 1, hex('#6b4428'))
  return b
}

function drawBush(): PixelBuf {
  const b = new PixelBuf(32, 32)
  b.clear([0, 0, 0, 0])
  b.fillRect(8, 22, 16, 4, [20, 40, 20, 45])
  const layers: [number, number, number, RGBA][] = [
    [16, 18, 9, hex('#1a5a28')],
    [16, 15, 8, hex('#2a7a38')],
    [16, 12, 6, hex('#3d9a48')],
  ]
  for (const [cx, cy, r, c] of layers) {
    for (let j = -r; j <= r; j++) {
      for (let i = -r; i <= r; i++) {
        if (i * i + j * j <= r * r) b.set(cx + i, cy + j, c)
      }
    }
  }
  b.set(12, 11, hex('#6bc96a'))
  b.set(18, 13, hex('#e07a8a'))
  return b
}

function drawFlowerBed(): PixelBuf {
  const b = new PixelBuf(32, 32)
  b.clear([0, 0, 0, 0])
  b.fillRect(4, 18, 24, 8, hex('#5a4030'))
  b.fillRect(5, 19, 22, 6, hex('#3d6b2e'))
  const flowers = [hex('#e07a8a'), hex('#e8c96a'), hex('#f0e8d8'), hex('#c45c8a')]
  for (let i = 0; i < 8; i++) {
    const x = 7 + (i % 4) * 5
    const y = 20 + Math.floor(i / 4) * 3
    b.set(x, y, flowers[i % flowers.length])
    b.set(x + 1, y, flowers[i % flowers.length])
  }
  return b
}

function drawFence(): PixelBuf {
  const b = new PixelBuf(32, 32)
  b.clear([0, 0, 0, 0])
  b.fillRect(2, 14, 28, 3, hex('#8a6a48'))
  b.fillRect(2, 13, 28, 1, hex('#b89060'))
  for (const x of [4, 12, 20, 27]) {
    b.fillRect(x, 10, 2, 12, hex('#6b4428'))
  }
  return b
}

function drawLamp(): PixelBuf {
  const b = new PixelBuf(32, 48)
  b.clear([0, 0, 0, 0])
  b.fillRect(14, 18, 4, 22, hex('#4a4a55'))
  b.fillRect(13, 16, 6, 4, hex('#3a3a45'))
  b.fillRect(11, 8, 10, 10, hex('#e8d080'))
  b.fillRect(12, 9, 8, 8, hex('#fff0a8'))
  b.fillRect(14, 40, 4, 4, hex('#2a2a32'))
  return b
}

function buildBuildingsSheet(): PixelBuf {
  // 288x208：建筑 + 树/椅/灌木/花坛/围栏/路灯
  const sheet = new PixelBuf(288, 208)
  sheet.clear([0, 0, 0, 0])
  sheet.blit(drawHouse(), 0, 0)
  sheet.blit(drawCafe(), 64, 0)
  sheet.blit(drawLibrary(), 160, 0)
  sheet.blit(drawTree(), 0, 80)
  sheet.blit(drawBench(), 40, 80)
  sheet.blit(drawTree(), 80, 80)
  sheet.blit(drawTree(), 120, 80)
  sheet.blit(drawBush(), 160, 80)
  sheet.blit(drawBush(), 192, 80)
  sheet.blit(drawFlowerBed(), 224, 80)
  sheet.blit(drawFence(), 0, 160)
  sheet.blit(drawFence(), 32, 160)
  sheet.blit(drawLamp(), 64, 144)
  sheet.blit(drawFlowerBed(), 96, 160)
  return sheet
}

// ---------- 角色精灵（4 行方向 × 4 列走帧，每格 32x32） ----------

type CharPalette = {
  hair: RGBA
  skin: RGBA
  shirt: RGBA
  shirtDark: RGBA
  pants: RGBA
  shoes: RGBA
  accent: RGBA // 围裙/笔记本等
  eyes: RGBA
}

const PALETTES: Record<string, CharPalette> = {
  linyue: {
    hair: hex('#6b4428'),
    skin: hex('#f0c8a8'),
    shirt: hex('#E07A5F'),
    shirtDark: hex('#c45c48'),
    pants: hex('#3d342e'),
    shoes: hex('#2a2218'),
    accent: hex('#f4e6d8'), // 围裙
    eyes: hex('#2a2218'),
  },
  chenmo: {
    hair: hex('#1f2430'),
    skin: hex('#e8c4a4'),
    shirt: hex('#3D5A80'),
    shirtDark: hex('#2f4868'),
    pants: hex('#2a3038'),
    shoes: hex('#1a1a1a'),
    accent: hex('#1a222c'), // 笔记本/电脑
    eyes: hex('#1a1a1a'),
  },
  suqing: {
    hair: hex('#5c3d2e'),
    skin: hex('#f3d2b3'),
    shirt: hex('#e8e0d4'), // 浅色连衣裙
    shirtDark: hex('#cfc4b4'),
    pants: hex('#ddd4c6'),
    shoes: hex('#4a3a48'),
    accent: hex('#81B29A'), // 书本点缀
    eyes: hex('#2a2218'),
  },
}

/** dir: 0下 1左 2右 3上；frame: 0-3 走步 */
function drawCharFrame(p: CharPalette, dir: number, frame: number, who: string): PixelBuf {
  const b = new PixelBuf(TILE, TILE)
  b.clear([0, 0, 0, 0])
  const walk = frame % 4
  // 脚交替
  const legL = walk === 1 || walk === 2 ? 1 : 0
  const legR = walk === 3 || walk === 2 ? 1 : 0
  const armSwing = walk === 1 ? 1 : walk === 3 ? -1 : 0

  // 影子
  b.fillRect(10, 28, 12, 3, [0, 0, 0, 50])

  const facingSide = dir === 1 || dir === 2
  const facingBack = dir === 3

  // 腿
  b.fillRect(12, 22 + legL, 3, 7 - legL, p.pants)
  b.fillRect(17, 22 + legR, 3, 7 - legR, p.pants)
  b.fillRect(12, 28 + legL, 3, 2, p.shoes)
  b.fillRect(17, 28 + legR, 3, 2, p.shoes)

  // 身体
  b.fillRect(11, 14, 10, 10, p.shirt)
  b.fillRect(11, 14, 10, 2, p.shirtDark)
  if (who === 'linyue') {
    // 围裙
    b.fillRect(13, 17, 6, 7, p.accent)
    b.set(15, 19, p.shirt)
  }
  if (who === 'suqing') {
    // 连衣裙下摆加宽
    b.fillRect(10, 20, 12, 5, p.shirt)
  }

  // 手臂
  if (!facingBack) {
    b.fillRect(8, 15 + armSwing, 3, 7, p.skin)
    b.fillRect(21, 15 - armSwing, 3, 7, p.skin)
  } else {
    b.fillRect(8, 15 - armSwing, 3, 7, p.skin)
    b.fillRect(21, 15 + armSwing, 3, 7, p.skin)
  }

  // 头
  b.fillRect(12, 4, 8, 8, p.skin)
  // 头发
  if (who === 'suqing') {
    b.fillRect(11, 3, 10, 5, p.hair)
    b.fillRect(10, 8, 3, 10, p.hair)
    b.fillRect(19, 8, 3, 10, p.hair)
  } else if (who === 'chenmo') {
    b.fillRect(12, 3, 8, 4, p.hair)
    b.fillRect(11, 4, 10, 2, p.hair)
  } else {
    b.fillRect(11, 3, 10, 5, p.hair)
    b.fillRect(11, 7, 2, 3, p.hair)
    b.fillRect(19, 7, 2, 3, p.hair)
  }

  // 脸（非背面）
  if (!facingBack) {
    const eyeY = 7
    if (dir === 1) {
      // 朝左：眼睛偏左
      b.set(13, eyeY, p.eyes)
      b.set(14, eyeY, hex('#ffffff'))
    } else if (dir === 2) {
      b.set(18, eyeY, p.eyes)
      b.set(17, eyeY, hex('#ffffff'))
    } else {
      // 朝下：双眼 + 高光
      b.set(14, eyeY, p.eyes)
      b.set(17, eyeY, p.eyes)
      b.set(15, eyeY, hex('#ffffff'))
      b.set(18, eyeY, hex('#ffffff'))
    }
    b.set(15, 10, hex('#c47a6a'))
    b.set(16, 10, hex('#c47a6a'))
    if (who === 'chenmo') {
      b.fillRect(13, 6, 3, 2, [40, 40, 50, 200])
      b.fillRect(17, 6, 3, 2, [40, 40, 50, 200])
      b.set(16, 7, hex('#2a3038'))
    }
  } else {
    b.fillRect(12, 4, 8, 8, p.hair)
  }

  // 道具
  if (who === 'chenmo' && !facingBack) {
    b.fillRect(20, 18, 5, 4, p.accent)
    b.fillRect(21, 19, 3, 2, hex('#5b9fd4'))
  }
  if (who === 'suqing' && dir !== 1) {
    b.fillRect(22, 17, 4, 6, p.accent)
    b.set(23, 19, hex('#a89070'))
  }

  // 侧身压缩感：左侧时略偏
  if (facingSide) {
    // 已通过眼睛位置区分
  }

  return b
}

function buildCharacterSheet(who: string): PixelBuf {
  const p = PALETTES[who]
  const sheet = new PixelBuf(TILE * 4, TILE * 4)
  sheet.clear([0, 0, 0, 0])
  // rows: down left right up
  for (let dir = 0; dir < 4; dir++) {
    for (let frame = 0; frame < 4; frame++) {
      const fr = drawCharFrame(p, dir, frame, who)
      sheet.blit(fr, frame * TILE, dir * TILE)
    }
  }
  return sheet
}

// ---------- main ----------

function main() {
  console.log('生成像素素材 →', OUT)
  mkdirSync(join(OUT, 'characters'), { recursive: true })

  savePng(buildTileset(), join(OUT, 'tileset.png'))
  savePng(buildBuildingsSheet(), join(OUT, 'buildings.png'))
  savePng(buildCharacterSheet('linyue'), join(OUT, 'characters', 'linyue.png'))
  savePng(buildCharacterSheet('chenmo'), join(OUT, 'characters', 'chenmo.png'))
  savePng(buildCharacterSheet('suqing'), join(OUT, 'characters', 'suqing.png'))

  console.log('\n完成。接下来：npm run dev 启动，Phaser 将加载这些 PNG。')
}

main()
