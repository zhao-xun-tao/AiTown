import type { Building, ZoneType } from '../types/world'

/** 与后端 CollisionMap 对齐的碰撞规则 */
export const MAP_SIZE = 20

const TREE_SPOTS: [number, number][] = [
  [5, 9],
  [12, 5],
  [4, 12],
  [15, 12],
]

export function buildCollisionGrid(buildings: Building[]): boolean[][] {
  const n = MAP_SIZE
  const blocked: boolean[][] = Array.from({ length: n }, () => Array(n).fill(false))

  for (let y = 9; y <= 10; y++) {
    for (let x = 9; x <= 10; x++) {
      blocked[y][x] = true
    }
  }
  for (const [x, y] of TREE_SPOTS) {
    if (inBounds(x, y)) blocked[y][x] = true
  }

  for (const b of buildings) {
    if (b.type === 'PARK') {
      mark(blocked, b.x + 1, b.y + 1)
      mark(blocked, b.x + b.width - 2, b.y + 2)
      continue
    }
    if (!isSolidBuilding(b.type)) continue
    const doorY = b.y + b.height - 1
    for (let gy = b.y; gy < b.y + b.height; gy++) {
      for (let gx = b.x; gx < b.x + b.width; gx++) {
        if (gy < doorY) mark(blocked, gx, gy)
      }
    }
  }
  return blocked
}

export function isWalkable(grid: boolean[][], x: number, y: number): boolean {
  if (!inBounds(x, y)) return false
  return !grid[y][x]
}

export function snapToWalkable(
  grid: boolean[][],
  x: number,
  y: number,
  buildings: Building[],
): { x: number; y: number } {
  if (isWalkable(grid, x, y)) return { x, y }

  for (const b of buildings) {
    if (x < b.x || x >= b.x + b.width || y < b.y || y >= b.y + b.height) continue
    if (!isSolidBuilding(b.type)) break
    const doorY = b.y + b.height - 1
    let bestX = b.x + Math.floor(b.width / 2)
    let best = Infinity
    for (let gx = b.x; gx < b.x + b.width; gx++) {
      if (!isWalkable(grid, gx, doorY)) continue
      const d = Math.abs(gx - x)
      if (d < best) {
        best = d
        bestX = gx
      }
    }
    if (isWalkable(grid, bestX, doorY)) return { x: bestX, y: doorY }
  }

  for (let r = 1; r < MAP_SIZE; r++) {
    for (let dy = -r; dy <= r; dy++) {
      for (let dx = -r; dx <= r; dx++) {
        if (Math.abs(dx) !== r && Math.abs(dy) !== r) continue
        const nx = x + dx
        const ny = y + dy
        if (isWalkable(grid, nx, ny)) return { x: nx, y: ny }
      }
    }
  }
  return { x, y }
}

function isSolidBuilding(type: ZoneType) {
  return type === 'HOUSE' || type === 'CAFE' || type === 'LIBRARY'
}

function mark(g: boolean[][], x: number, y: number) {
  if (inBounds(x, y)) g[y][x] = true
}

function inBounds(x: number, y: number) {
  return x >= 0 && y >= 0 && x < MAP_SIZE && y < MAP_SIZE
}
