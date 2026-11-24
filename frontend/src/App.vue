<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import ControlBar from './components/ControlBar.vue'
import TownPhaser from './components/TownPhaser.vue'
import ObserverPanel from './components/ObserverPanel.vue'
import { fetchWorldState, pauseSim, startSim, stepSim } from './api/sim'
import { injectWorldEvent } from './api/events'
import { useWorldSocket } from './composables/useWorldSocket'
import { createSkeletonWorld } from './data/skeletonWorld'
import type { SimStatus, WorldEvent, WorldState } from './types/world'

/** 首屏立刻用骨架地图，避免干等「唤醒小镇」 */
const world = ref<WorldState | null>(createSkeletonWorld())
const selectedId = ref<string | null>('agent-linyue')
const selectedBuildingId = ref<string | null>(null)
const busy = ref(false)
const errorMsg = ref('')
const syncing = ref(true)
const synced = ref(false)

const timeDisplay = ref('连接中…')
const simStatus = ref<SimStatus>('PAUSED')

const activeEvents = computed<WorldEvent[]>(() => world.value?.activeWorldEvents || [])

function applyState(state: WorldState) {
  world.value = state
  timeDisplay.value = state.timeDisplay
  simStatus.value = state.simStatus
  synced.value = true
  syncing.value = false
  errorMsg.value = ''
  if (!selectedId.value && state.agents.length > 0) {
    selectedId.value = state.agents[0].id
  }
}

const { connected } = useWorldSocket((state) => {
  // 只有真正连上并收到快照后才覆盖骨架
  applyState(state)
})

async function loadWorld(retry = 0) {
  syncing.value = true
  try {
    const state = await fetchWorldState()
    applyState(state)
  } catch (e) {
    const msg = e instanceof Error ? e.message : '无法连接后端'
    if (retry < 2) {
      // 后端冷启动时多试两次
      await new Promise((r) => setTimeout(r, 800 * (retry + 1)))
      return loadWorld(retry + 1)
    }
    errorMsg.value = msg + '（已显示本地预览地图，启动后端后会自动同步）'
    syncing.value = false
    console.error(e)
  }
}

onMounted(() => {
  void loadWorld()
})

async function onStart() {
  busy.value = true
  errorMsg.value = ''
  try {
    await startSim()
    simStatus.value = 'RUNNING'
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '启动失败'
  } finally {
    busy.value = false
  }
}

async function onPause() {
  busy.value = true
  errorMsg.value = ''
  try {
    await pauseSim()
    simStatus.value = 'PAUSED'
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '暂停失败'
  } finally {
    busy.value = false
  }
}

async function onStep() {
  busy.value = true
  errorMsg.value = ''
  try {
    const state = await stepSim()
    applyState(state)
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '单步失败'
  } finally {
    busy.value = false
  }
}

async function onInject(content: string) {
  busy.value = true
  errorMsg.value = ''
  try {
    await injectWorldEvent(content)
    const state = await fetchWorldState()
    applyState(state)
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '世界事件投放失败'
  } finally {
    busy.value = false
  }
}

function onSelect(id: string) {
  selectedId.value = id
  selectedBuildingId.value = null
}

function onSelectBuilding(id: string | null) {
  selectedBuildingId.value = id
  if (id) selectedId.value = null
}

function onRetry() {
  void loadWorld()
}
</script>

<template>
  <div class="workbench">
    <ControlBar
      :time-display="timeDisplay"
      :sim-status="simStatus"
      :connected="connected"
      :busy="busy || syncing"
      :active-events="activeEvents"
      @start="onStart"
      @pause="onPause"
      @step="onStep"
      @inject="onInject"
    />

    <p v-if="errorMsg" class="error-banner">
      {{ errorMsg }}
      <button class="retry" type="button" @click="onRetry">重试同步</button>
    </p>
    <p v-else-if="syncing && !synced" class="sync-banner">
      正在同步后端世界状态…
    </p>

    <div class="body">
      <main class="stage">
        <TownPhaser
          :world="world"
          :selected-id="selectedId"
          :selected-building-id="selectedBuildingId"
          :syncing="syncing && !synced"
          @select="onSelect"
          @select-building="onSelectBuilding"
        />
      </main>
      <ObserverPanel
        class="side"
        :world="world"
        :selected-id="selectedId"
        :selected-building-id="selectedBuildingId"
        :observability-enabled="synced"
        @select="onSelect"
        @select-building="onSelectBuilding"
      />
    </div>
  </div>
</template>

<style scoped>
.workbench {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100vh;
}

.error-banner,
.sync-banner {
  margin: 0;
  padding: 8px 20px;
  font-size: 0.85rem;
  font-weight: 600;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 12px;
}

.error-banner {
  background: rgba(90, 35, 30, 0.9);
  color: #f0c0c0;
  border-bottom-color: #8a4a4a;
}

.sync-banner {
  background: rgba(40, 55, 42, 0.95);
  color: var(--accent-strong);
}

.retry {
  border: 1px solid #c08080;
  background: rgba(0, 0, 0, 0.2);
  color: #f0c0c0;
  padding: 3px 10px;
  font-weight: 700;
  font-size: 0.8rem;
}

.body {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 380px);
  min-height: 0;
  background: var(--bg-deep);
}

.stage {
  min-width: 0;
  min-height: 0;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.side {
  min-width: 0;
}

@media (max-width: 960px) {
  .body {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(360px, 1fr) 480px;
  }

  .side {
    border-left: none;
    border-top: 1px solid var(--border);
  }
}
</style>
