<script setup lang="ts">
import { ref } from 'vue'
import type { SimStatus, WorldEvent } from '../types/world'

defineProps<{
  timeDisplay: string
  simStatus: SimStatus
  connected: boolean
  busy: boolean
  activeEvents: WorldEvent[]
}>()

const emit = defineEmits<{
  start: []
  pause: []
  step: []
  inject: [content: string]
}>()

const eventText = ref('')

function sendEvent() {
  const t = eventText.value.trim()
  if (!t) return
  emit('inject', t)
  eventText.value = ''
}
</script>

<template>
  <header class="bar-wrap">
    <div class="bar">
      <div class="brand">
        <span class="brand-mark">AI Town</span>
        <span class="brand-sub">多智能体社会模拟沙盒</span>
      </div>

      <div class="clock">
        <span class="clock-label">世界时间</span>
        <span class="clock-value">{{ timeDisplay || '—' }}</span>
        <span class="pill" :class="simStatus === 'RUNNING' ? 'pill-run' : 'pill-pause'">
          {{ simStatus === 'RUNNING' ? '运行中' : '已暂停' }}
        </span>
        <span class="pill" :class="connected ? 'pill-ok' : 'pill-off'">
          {{ connected ? 'WS 已连接' : 'WS 断开' }}
        </span>
      </div>

      <div class="actions">
        <button
          class="btn btn-primary"
          :disabled="busy || simStatus === 'RUNNING'"
          @click="emit('start')"
        >
          开始
        </button>
        <button
          class="btn"
          :disabled="busy || simStatus !== 'RUNNING'"
          @click="emit('pause')"
        >
          暂停
        </button>
        <button class="btn" :disabled="busy" @click="emit('step')">单步</button>
      </div>
    </div>

    <div class="god-row">
      <span class="god-label">God Mode</span>
      <input
        v-model="eventText"
        class="god-input"
        type="text"
        placeholder="投放世界事件，例如：咖啡馆突然宣布今天所有饮品免费"
        :disabled="busy"
        @keyup.enter="sendEvent"
      />
      <button class="btn btn-god" :disabled="busy || !eventText.trim()" @click="sendEvent">
        发送
      </button>
      <div v-if="activeEvents?.length" class="god-active">
        <span v-for="ev in activeEvents" :key="ev.id" class="event-chip" :title="ev.content">
          {{ ev.content.length > 18 ? ev.content.slice(0, 18) + '…' : ev.content }}
          <em>{{ ev.remainingTicks }}T</em>
        </span>
      </div>
    </div>
  </header>
</template>

<style scoped>
.bar-wrap {
  background: linear-gradient(180deg, #2a333c 0%, var(--bg-panel) 100%);
  border-bottom: 1px solid var(--border);
}

.bar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  padding: 12px 20px 8px;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.brand-mark {
  font-weight: 700;
  font-size: 1.15rem;
  letter-spacing: 0.02em;
}

.brand-sub {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.clock {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
}

.clock-label {
  color: var(--text-muted);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.clock-value {
  font-family: var(--font-mono);
  font-size: 1.05rem;
  font-weight: 500;
  min-width: 9.5rem;
}

.pill {
  font-size: 0.7rem;
  padding: 2px 8px;
  border: 1px solid var(--border);
  background: var(--bg-elevated);
  color: var(--text-muted);
}

.pill-run {
  border-color: #4a8f55;
  color: var(--success);
}

.pill-pause {
  border-color: #8a6a3a;
  color: var(--warn);
}

.pill-ok {
  border-color: #3d6a8a;
  color: var(--accent);
}

.pill-off {
  border-color: #8a4a4a;
  color: var(--danger);
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.btn {
  padding: 7px 14px;
  border: 1px solid var(--border);
  background: var(--bg-elevated);
  color: var(--text);
  font-size: 0.85rem;
  font-weight: 500;
}

.btn:hover:not(:disabled) {
  border-color: var(--accent-dim);
  background: #33404b;
}

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-primary {
  background: var(--accent-dim);
  border-color: var(--accent);
}

.btn-primary:hover:not(:disabled) {
  background: var(--accent);
  color: #0f1418;
}

.god-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 20px 12px;
  flex-wrap: wrap;
}

.god-label {
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--warn);
  white-space: nowrap;
}

.god-input {
  flex: 1;
  min-width: 220px;
  padding: 8px 12px;
  border: 1px solid var(--border);
  background: #1b2228;
  color: var(--text);
  font-family: var(--font-ui);
  font-size: 0.85rem;
  outline: none;
}

.god-input:focus {
  border-color: var(--warn);
}

.btn-god {
  background: #6a4a2a;
  border-color: var(--warn);
  color: #f0d9b5;
}

.god-active {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}

.event-chip {
  font-size: 0.72rem;
  padding: 3px 8px;
  background: #3a2e1f;
  border: 1px solid #8a6a3a;
  color: #e8d4b0;
}

.event-chip em {
  font-style: normal;
  margin-left: 6px;
  opacity: 0.7;
  font-family: var(--font-mono);
}

@media (max-width: 900px) {
  .bar {
    grid-template-columns: 1fr;
    justify-items: start;
  }

  .clock,
  .actions {
    justify-content: flex-start;
  }
}
</style>
