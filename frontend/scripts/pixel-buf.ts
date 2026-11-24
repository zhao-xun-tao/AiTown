/** 简易像素画布：RGBA 缓冲 + PNG 导出辅助 */

export type RGBA = [number, number, number, number]

export class PixelBuf {
  readonly w: number
  readonly h: number
  readonly data: Uint8ClampedArray

  constructor(w: number, h: number) {
    this.w = w
    this.h = h
    this.data = new Uint8ClampedArray(w * h * 4)
  }

  clear(c: RGBA = [0, 0, 0, 0]) {
    for (let i = 0; i < this.data.length; i += 4) {
      this.data[i] = c[0]
      this.data[i + 1] = c[1]
      this.data[i + 2] = c[2]
      this.data[i + 3] = c[3]
    }
  }

  set(x: number, y: number, c: RGBA) {
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return
    const i = (y * this.w + x) * 4
    // alpha 混合（源 atop）
    const a = c[3] / 255
    if (a >= 1) {
      this.data[i] = c[0]
      this.data[i + 1] = c[1]
      this.data[i + 2] = c[2]
      this.data[i + 3] = 255
      return
    }
    if (a <= 0) return
    const oa = this.data[i + 3] / 255
    const outA = a + oa * (1 - a)
    this.data[i] = Math.round((c[0] * a + this.data[i] * oa * (1 - a)) / outA)
    this.data[i + 1] = Math.round((c[1] * a + this.data[i + 1] * oa * (1 - a)) / outA)
    this.data[i + 2] = Math.round((c[2] * a + this.data[i + 2] * oa * (1 - a)) / outA)
    this.data[i + 3] = Math.round(outA * 255)
  }

  fillRect(x: number, y: number, w: number, h: number, c: RGBA) {
    for (let j = 0; j < h; j++) {
      for (let i = 0; i < w; i++) this.set(x + i, y + j, c)
    }
  }

  /** 将 src 贴到本缓冲 (dx,dy)，可裁剪 */
  blit(src: PixelBuf, dx: number, dy: number, sx = 0, sy = 0, sw = src.w, sh = src.h) {
    for (let j = 0; j < sh; j++) {
      for (let i = 0; i < sw; i++) {
        const si = ((sy + j) * src.w + (sx + i)) * 4
        const a = src.data[si + 3]
        if (a === 0) continue
        this.set(dx + i, dy + j, [
          src.data[si],
          src.data[si + 1],
          src.data[si + 2],
          a,
        ])
      }
    }
  }

  /** 简单抖动噪声 */
  static hash(x: number, y: number, seed = 0) {
    const n = Math.sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453
    return n - Math.floor(n)
  }
}

export function hex(rgb: string, a = 255): RGBA {
  const n = parseInt(rgb.replace('#', ''), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255, a]
}
