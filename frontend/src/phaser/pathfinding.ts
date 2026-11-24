import { isWalkable, snapToWalkable } from './collision'
import type { Building } from '../types/world'

export type Point = { x: number; y: number }

const DIRS: Point[] = [
  { x: 1, y: 0 },
  { x: -1, y: 0 },
  { x: 0, y: 1 },
  { x: 0, y: -1 },
]

/** 前端局部 A*（堵路重寻路 / 无后端 path 时兜底） */
export function findPath(
  grid: boolean[][],
  sx: number,
  sy: number,
  gx: number,
  gy: number,
  buildings: Building[],
): Point[] {
  const start = snapToWalkable(grid, sx, sy, buildings)
  const goal = snapToWalkable(grid, gx, gy, buildings)
  sx = start.x
  sy = start.y
  gx = goal.x
  gy = goal.y

  if (sx === gx && sy === gy) return [{ x: gx, y: gy }]
  if (!isWalkable(grid, gx, gy)) return []

  type Node = { x: number; y: number; g: number; f: number }
  const open: Node[] = [{ x: sx, y: sy, g: 0, f: heuristic(sx, sy, gx, gy) }]
  const gScore = new Map<string, number>([[key(sx, sy), 0]])
  const came = new Map<string, string>()

  while (open.length) {
    open.sort((a, b) => a.f - b.f)
    const cur = open.shift()!
    if (cur.x === gx && cur.y === gy) {
      return reconstruct(came, gx, gy)
    }
    const ck = key(cur.x, cur.y)
    const cg = gScore.get(ck) ?? Infinity
    if (cur.g > cg) continue

    for (const d of DIRS) {
      const nx = cur.x + d.x
      const ny = cur.y + d.y
      if (!isWalkable(grid, nx, ny)) continue
      const nk = key(nx, ny)
      const ng = cg + 1
      if (ng >= (gScore.get(nk) ?? Infinity)) continue
      came.set(nk, ck)
      gScore.set(nk, ng)
      open.push({ x: nx, y: ny, g: ng, f: ng + heuristic(nx, ny, gx, gy) })
    }
  }
  return []
}

function heuristic(x: number, y: number, gx: number, gy: number) {
  return Math.abs(x - gx) + Math.abs(y - gy)
}

function key(x: number, y: number) {
  return `${x},${y}`
}

function reconstruct(came: Map<string, string>, gx: number, gy: number): Point[] {
  const path: Point[] = [{ x: gx, y: gy }]
  let cur = key(gx, gy)
  while (came.has(cur)) {
    cur = came.get(cur)!
    const [x, y] = cur.split(',').map(Number)
    path.push({ x, y })
  }
  path.reverse()
  if (path.length > 1) path.shift()
  return path
}
