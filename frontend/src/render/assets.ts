/** 加载小镇高清游戏美术（生成的 JPG 精灵） */

export type TownAssets = {
  grass: HTMLImageElement
  path: HTMLImageElement
  house: HTMLImageElement
  cafe: HTMLImageElement
  library: HTMLImageElement
  park: HTMLImageElement
  plaza: HTMLImageElement
  chars: Record<string, HTMLImageElement>
  ready: boolean
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.decoding = 'async'
    img.onload = () => resolve(img)
    img.onerror = () => reject(new Error(`加载失败: ${src}`))
    img.src = src
  })
}

let cache: TownAssets | null = null
let loading: Promise<TownAssets> | null = null

export function loadTownAssets(): Promise<TownAssets> {
  if (cache?.ready) return Promise.resolve(cache)
  if (loading) return loading

  loading = (async () => {
    const [grass, path, house, cafe, library, park, plaza, linyue, chenmo, suqing] =
      await Promise.all([
        loadImage('/assets/tiles/grass.jpg'),
        loadImage('/assets/tiles/path.jpg'),
        loadImage('/assets/buildings/house.jpg'),
        loadImage('/assets/buildings/cafe.jpg'),
        loadImage('/assets/buildings/library.jpg'),
        loadImage('/assets/buildings/park.jpg'),
        loadImage('/assets/buildings/plaza.jpg'),
        loadImage('/assets/chars/linyue.jpg'),
        loadImage('/assets/chars/chenmo.jpg'),
        loadImage('/assets/chars/suqing.jpg'),
      ])

    cache = {
      grass,
      path,
      house,
      cafe,
      library,
      park,
      plaza,
      chars: {
        'agent-linyue': linyue,
        'agent-chenmo': chenmo,
        'agent-suqing': suqing,
      },
      ready: true,
    }
    return cache
  })()

  return loading
}

/**
 * 将角色图近白/近黑背景抠掉，得到带透明通道的离屏图。
 */
export function knockoutBackground(img: HTMLImageElement): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = img.naturalWidth || img.width
  c.height = img.naturalHeight || img.height
  const ctx = c.getContext('2d')!
  ctx.drawImage(img, 0, 0)
  const data = ctx.getImageData(0, 0, c.width, c.height)
  const d = data.data
  for (let i = 0; i < d.length; i += 4) {
    const r = d[i]
    const g = d[i + 1]
    const b = d[i + 2]
    // 白底 / 浅灰底
    const bright = r > 235 && g > 235 && b > 235
    // 纯黑影棚底
    const dark = r < 18 && g < 18 && b < 18
    if (bright || dark) d[i + 3] = 0
  }
  ctx.putImageData(data, 0, 0)
  return c
}
