<script setup lang="ts">
import type { Agent, WorldState } from '../types/world'
import { computed } from 'vue'

const props = defineProps<{
  world: WorldState | null
  selectedId: string | null
}>()

const emit = defineEmits<{
  select: [id: string]
}>()

const selected = computed<Agent | null>(() => {
  if (!props.world || !props.selectedId) return null
  return props.world.agents.find((a) => a.id === props.selectedId) ?? null
})
</script>

<template>
  <aside class="panel">
    <div class="panel-head">
      <h2>观察面板</h2>
      <p class="hint">研究可观测性：记忆 / CoT / Prompt / Token（后续阶段接入）</p>
    </div>

    <section class="section">
      <h3>居民列表</h3>
      <ul v-if="world" class="agent-list">
        <li
          v-for="agent in world.agents"
          :key="agent.id"
          :class="{ active: agent.id === selectedId }"
          @click="emit('select', agent.id)"
        >
          <span class="dot" :style="{ background: agent.color }" />
          <span class="name">{{ agent.name }}</span>
          <span class="role">{{ agent.role }}</span>
          <span class="state">{{ agent.state }}</span>
        </li>
      </ul>
      <p v-else class="empty">等待世界状态…</p>
    </section>

    <section class="section grow">
      <h3>Agent 详情</h3>
      <template v-if="selected">
        <div class="detail">
          <div class="row">
            <span class="label">姓名</span>
            <span>{{ selected.name }}（{{ selected.role }}）</span>
          </div>
          <div class="row">
            <span class="label">坐标</span>
            <span class="mono">({{ selected.x }}, {{ selected.y }})</span>
          </div>
          <div class="row">
            <span class="label">状态</span>
            <span>{{ selected.state }}</span>
          </div>
          <div class="row">
            <span class="label">动作</span>
            <span class="mono">{{ selected.lastAction || '—' }}</span>
          </div>
          <div class="row block">
            <span class="label">气泡</span>
            <span>{{ selected.bubble }}</span>
          </div>
          <div class="row block">
            <span class="label">发言</span>
            <span>{{ selected.lastSpeech || '—' }}</span>
          </div>
          <div class="row block">
            <span class="label">人设</span>
            <span class="muted">{{ selected.persona }}</span>
          </div>
          <div class="row block">
            <span class="label">最近思考</span>
            <pre class="thought">{{ selected.lastThought }}</pre>
          </div>
          <div v-if="selected.lastError" class="row block">
            <span class="label">决策错误</span>
            <span class="err">{{ selected.lastError }}</span>
          </div>
        </div>
        <div class="speech-log" v-if="world?.recentSpeeches?.length">
          <h4>可感知对话（上一 Tick）</h4>
          <ul>
            <li v-for="(ev, i) in world.recentSpeeches" :key="i">
              <strong>{{ ev.speakerName }}</strong>
              <template v-if="ev.targetAgentName"> → {{ ev.targetAgentName }}</template>
              @{{ ev.areaName }}：「{{ ev.speech }}」
            </li>
          </ul>
        </div>
        <div class="placeholders">
          <div class="ph">记忆检索流 · 占位</div>
          <div class="ph">Prompt / Token · 占位</div>
        </div>
      </template>
      <p v-else class="empty">点击上方居民或地图上的角色查看详情</p>
    </section>
  </aside>
</template>

<style scoped>
.panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-panel);
  border-left: 1px solid var(--border);
  overflow: hidden;
}

.panel-head {
  padding: 16px 16px 8px;
  border-bottom: 1px solid var(--border);
}

.panel-head h2 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}

.hint {
  margin: 6px 0 8px;
  font-size: 0.75rem;
  color: var(--text-muted);
  line-height: 1.4;
}

.section {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}

.section.grow {
  flex: 1;
  overflow: auto;
  border-bottom: none;
}

.section h3 {
  margin: 0 0 10px;
  font-size: 0.75rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.agent-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.agent-list li {
  display: grid;
  grid-template-columns: 10px 1fr auto auto;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: var(--bg-elevated);
  border: 1px solid transparent;
  cursor: pointer;
  font-size: 0.85rem;
}

.agent-list li:hover,
.agent-list li.active {
  border-color: var(--accent-dim);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.name {
  font-weight: 600;
}

.role,
.state {
  color: var(--text-muted);
  font-size: 0.75rem;
}

.state {
  font-family: var(--font-mono);
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 14px;
}

.row {
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 8px;
  font-size: 0.85rem;
}

.row.block {
  grid-template-columns: 1fr;
}

.label {
  color: var(--text-muted);
  font-size: 0.75rem;
}

.mono {
  font-family: var(--font-mono);
}

.muted {
  color: var(--text-muted);
  line-height: 1.45;
  font-size: 0.8rem;
}

.thought {
  margin: 0;
  padding: 10px;
  background: #1b2228;
  border: 1px solid var(--border);
  font-family: var(--font-mono);
  font-size: 0.75rem;
  white-space: pre-wrap;
  line-height: 1.45;
  color: #c5d4e0;
}

.placeholders {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ph {
  padding: 18px 12px;
  border: 1px dashed var(--border);
  color: var(--text-muted);
  font-size: 0.8rem;
  text-align: center;
  background: rgba(0, 0, 0, 0.15);
}

.err {
  color: var(--danger);
  font-size: 0.8rem;
}

.speech-log {
  margin-bottom: 14px;
}

.speech-log h4 {
  margin: 0 0 8px;
  font-size: 0.75rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.speech-log ul {
  margin: 0;
  padding-left: 18px;
  font-size: 0.8rem;
  line-height: 1.45;
  color: var(--text-muted);
}

.empty {
  color: var(--text-muted);
  font-size: 0.85rem;
}
</style>
