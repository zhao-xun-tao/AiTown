<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ControlBar from './components/ControlBar.vue'
import TownCanvas from './components/TownCanvas.vue'
import ObserverPanel from './components/ObserverPanel.vue'
import { fetchWorldState, pauseSim, startSim, stepSim } from './api/sim'
import { useWorldSocket } from './composables/useWorldSocket'
import type { SimStatus, WorldState } from './types/world'

const world = ref<WorldState | null>(null)
const selectedId = ref<string | null>(null)
const busy = ref(false)
const errorMsg = ref('')

const timeDisplay = ref('—')
const simStatus = ref<SimStatus>('PAUSED')

function applyState(state: WorldState) {
  world.value = state
  timeDisplay.value = state.timeDisplay
  simStatus.value = state.simStatus
  if (!selectedId.value && state.agents.length > 0) {
    selectedId.value = state.agents[0].id
  }
}

const { connected } = useWorldSocket(applyState)

onMounted(async () => {
  try {
    const state = await fetchWorldState()
    applyState(state)
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : '无法连接后端'
    console.error(e)
  }
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

function onSelect(id: string) {
  selectedId.value = id
}
</script>

<template>
  <div class="workbench">
    <ControlBar
      :time-display="timeDisplay"
      :sim-status="simStatus"
      :connected="connected"
      :busy="busy"
      @start="onStart"
      @pause="onPause"
      @step="onStep"
    />

    <p v-if="errorMsg" class="error-banner">{{ errorMsg }}</p>

    <div class="body">
      <main class="stage">
        <TownCanvas :world="world" :selected-id="selectedId" @select="onSelect" />
      </main>
      <ObserverPanel
        class="side"
        :world="world"
        :selected-id="selectedId"
        @select="onSelect"
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

.error-banner {
  margin: 0;
  padding: 8px 20px;
  background: #4a2a2a;
  color: #f0c0c0;
  font-size: 0.85rem;
  border-bottom: 1px solid #6a3a3a;
}

.body {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 340px;
  min-height: 0;
}

.stage {
  min-width: 0;
  min-height: 0;
}

.side {
  min-width: 0;
}

@media (max-width: 960px) {
  .body {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(360px, 1fr) 420px;
  }

  .side {
    border-left: none;
    border-top: 1px solid var(--border);
  }
}
</style>
