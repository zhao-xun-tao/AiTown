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
  background:
    linear-gradient(180deg, rgba(212, 180, 131, 0.08), transparent 40%),
    linear-gradient(180deg, #2a3b33 0%, var(--bg-panel) 100%);
  border-bottom: 1px solid var(--border);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.18);
}

.bar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  padding: 14px 20px 8px;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.brand-mark {
  font-weight: 800;
  font-size: 1.25rem;
  letter-spacing: 0.02em;
  color: var(--accent-strong);
}

.brand-sub {
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 600;
}

.clock {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
}

.clock-label {
  color: var(--text-muted);
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.clock-value {
  font-family: var(--font-mono);
  font-size: 1.05rem;
  font-weight: 500;
  min-width: 9.5rem;
  color: var(--text);
}

.pill {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 3px 9px;
  border: 1px solid var(--border);
  background: var(--bg-elevated);
  color: var(--text-muted);
}

.pill-run {
  border-color: #4a8f55;
  color: var(--success);
  background: rgba(125, 190, 122, 0.1);
}

.pill-pause {
  border-color: #8a6a3a;
  color: var(--warn);
  background: rgba(224, 164, 95, 0.1);
}

.pill-ok {
  border-color: #7a6a45;
  color: var(--accent);
  background: rgba(212, 180, 131, 0.1);
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
  padding: 8px 14px;
  border: 1px solid var(--border);
  background: var(--bg-elevated);
  color: var(--text);
  font-size: 0.85rem;
  font-weight: 700;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.1s ease;
}

.btn:hover:not(:disabled) {
  border-color: var(--accent-dim);
  background: var(--bg-soft);
  transform: translateY(-1px);
}

.btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(180deg, #c9a86a, var(--accent-dim));
  border-color: var(--accent);
  color: #1a2018;
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(180deg, #e0bf7e, #c9a86a);
  color: #1a2018;
}

.god-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 20px 14px;
  flex-wrap: wrap;
}

.god-label {
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--accent);
  white-space: nowrap;
}

.god-input {
  flex: 1;
  min-width: 220px;
  padding: 9px 12px;
  border: 1px solid var(--border);
  background: rgba(15, 25, 20, 0.55);
  color: var(--text);
  font-family: var(--font-ui);
  font-size: 0.85rem;
  font-weight: 600;
  outline: none;
}

.god-input:focus {
  border-color: var(--accent-dim);
  box-shadow: 0 0 0 3px rgba(212, 180, 131, 0.15);
}

.btn-god {
  background: rgba(168, 137, 85, 0.25);
  border-color: var(--accent-dim);
  color: var(--accent-strong);
}

.god-active {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  width: 100%;
}

.event-chip {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 4px 9px;
  background: rgba(80, 55, 25, 0.45);
  border: 1px solid rgba(212, 180, 131, 0.35);
  color: #f0e2c4;
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
