<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import Phaser from 'phaser'
import { TownScene } from '../phaser/TownScene'
import type { WorldState } from '../types/world'

const props = defineProps<{
  world: WorldState | null
  selectedId: string | null
  selectedBuildingId?: string | null
  syncing?: boolean
}>()

const emit = defineEmits<{
  select: [id: string]
  selectBuilding: [id: string | null]
}>()

const hostRef = ref<HTMLDivElement | null>(null)
let game: Phaser.Game | null = null
let sceneReady = false
let bootTimer: ReturnType<typeof setInterval> | null = null

function getScene(): TownScene | null {
  if (!game || !sceneReady) return null
  return game.scene.getScene('TownScene') as TownScene
}

function pushWorld() {
  const scene = getScene()
  if (!scene) return
  scene.syncWorld(props.world)
  scene.setSelected(props.selectedId)
  scene.setSelectedBuilding(props.selectedBuildingId ?? null)
}

function onResetView() {
  getScene()?.resetCamera()
}

onMounted(() => {
  const host = hostRef.value
  if (!host) return
  const w = Math.max(host.clientWidth, 400)
  const h = Math.max(host.clientHeight, 400)

  game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: host,
    backgroundColor: '#3d7a38',
    pixelArt: true,
    antialias: false,
    roundPixels: true,
    width: w,
    height: h,
    scale: {
      mode: Phaser.Scale.RESIZE,
      autoCenter: Phaser.Scale.NO_CENTER,
      width: w,
      height: h,
    },
    scene: [TownScene],
    banner: false,
  })

  bootTimer = setInterval(() => {
    const scene = game?.scene.getScene('TownScene') as TownScene | undefined
    if (!scene?.sys?.isActive()) return
    if (bootTimer) clearInterval(bootTimer)
    bootTimer = null
    sceneReady = true
    scene.setSelectHandler((id) => emit('select', id))
    scene.setBuildingSelectHandler((id) => emit('selectBuilding', id))
    requestAnimationFrame(() => {
      pushWorld()
      scene.resetCamera()
    })
  }, 40)
})

onUnmounted(() => {
  if (bootTimer) clearInterval(bootTimer)
  bootTimer = null
  sceneReady = false
  if (game) {
    game.destroy(true)
    game = null
  }
})

watch(() => props.world, () => pushWorld(), { deep: true })
watch(() => props.selectedId, (id) => getScene()?.setSelected(id))
watch(
  () => props.selectedBuildingId,
  (id) => getScene()?.setSelectedBuilding(id ?? null),
)
</script>

<template>
  <div class="town-phaser">
    <div ref="hostRef" class="phaser-host" />
    <div class="toolbar">
      <button type="button" class="tool-btn" @click="onResetView">复位全景</button>
      <span v-if="syncing" class="sync">同步中…</span>
    </div>
  </div>
</template>

<style scoped>
.town-phaser {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: #2f5f2c;
}
.phaser-host {
  position: absolute;
  inset: 0;
}
.phaser-host :deep(canvas) {
  display: block;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
}
.toolbar {
  position: absolute;
  right: 12px;
  top: 12px;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 10px;
  pointer-events: none;
}
.tool-btn {
  pointer-events: auto;
  border: 1px solid rgba(240, 230, 200, 0.45);
  background: rgba(20, 28, 22, 0.82);
  color: #f4efe6;
  font-size: 0.78rem;
  font-weight: 700;
  padding: 6px 12px;
  cursor: pointer;
}
.sync {
  font-size: 0.75rem;
  font-weight: 700;
  color: #e8f0e4;
  background: rgba(20, 30, 22, 0.75);
  padding: 5px 8px;
}
</style>
