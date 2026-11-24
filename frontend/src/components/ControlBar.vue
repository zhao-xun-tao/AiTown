<script setup lang="ts">
import type { SimStatus } from '../types/world'

defineProps<{
  timeDisplay: string
  simStatus: SimStatus
  connected: boolean
  busy: boolean
}>()

const emit = defineEmits<{
  start: []
  pause: []
  step: []
}>()
</script>

<template>
  <header class="bar">
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
  </header>
</template>

<style scoped>
.bar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  padding: 12px 20px;
  background: linear-gradient(180deg, #2a333c 0%, var(--bg-panel) 100%);
  border-bottom: 1px solid var(--border);
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
