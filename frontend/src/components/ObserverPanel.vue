<script setup lang="ts">
import type { Agent, WorldState } from '../types/world'
import { computed, nextTick, ref, watch } from 'vue'
import {
  fetchAgentMemories,
  type LongTermMemoryDto,
  type WorkingMemoryDto,
} from '../api/memory'
import {
  fetchCot,
  fetchRelations,
  type AgentRelation,
  type CotTurnLog,
} from '../api/observability'

const props = defineProps<{
  world: WorldState | null
  selectedId: string | null
}>()

const emit = defineEmits<{
  select: [id: string]
}>()

type TabKey = 'profile' | 'cot' | 'memory' | 'relation'

const tab = ref<TabKey>('cot')
const selected = computed<Agent | null>(() => {
  if (!props.world || !props.selectedId) return null
  return props.world.agents.find((a) => a.id === props.selectedId) ?? null
})

const working = ref<WorkingMemoryDto[]>([])
const longTerm = ref<LongTermMemoryDto[]>([])
const cotLogs = ref<CotTurnLog[]>([])
const relations = ref<AgentRelation[]>([])
const loadError = ref('')
const cotScrollRef = ref<HTMLElement | null>(null)

async function refreshObservability(agentId: string) {
  loadError.value = ''
  try {
    const [mem, cot, rel] = await Promise.all([
      fetchAgentMemories(agentId),
      fetchCot(agentId),
      fetchRelations(agentId),
    ])
    working.value = mem.working || []
    longTerm.value = mem.longTerm || []
    cotLogs.value = cot || []
    relations.value = rel || []
    await nextTick()
    // 保持滚动在顶部（最新回合）
    if (cotScrollRef.value) cotScrollRef.value.scrollTop = 0
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '观察数据加载失败'
  }
}

watch(
  () => [props.selectedId, props.world?.gameTime?.tick] as const,
  ([id]) => {
    if (id) refreshObservability(id)
    else {
      working.value = []
      longTerm.value = []
      cotLogs.value = []
      relations.value = []
    }
  },
  { immediate: true },
)

function stars(n: number) {
  const v = Math.max(0, Math.min(10, n || 0))
  return '★'.repeat(v) + '☆'.repeat(10 - v)
}

function favorColor(v: number) {
  if (v >= 70) return 'var(--success)'
  if (v <= 35) return 'var(--danger)'
  return 'var(--warn)'
}
</script>

<template>
  <aside class="panel">
    <div class="panel-head">
      <h2>科研观察面板</h2>
      <p class="hint">点击地图居民 · 实时 CoT · 记忆流 · 关系图谱</p>
    </div>

    <section class="section">
      <h3>居民</h3>
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

    <div class="tabs">
      <button :class="{ on: tab === 'profile' }" @click="tab = 'profile'">档案</button>
      <button :class="{ on: tab === 'cot' }" @click="tab = 'cot'">CoT</button>
      <button :class="{ on: tab === 'memory' }" @click="tab = 'memory'">记忆</button>
      <button :class="{ on: tab === 'relation' }" @click="tab = 'relation'">关系</button>
    </div>

    <section class="section grow">
      <p v-if="loadError" class="err">{{ loadError }}</p>
      <template v-if="selected">
        <!-- 档案 -->
        <div v-show="tab === 'profile'" class="tab-body">
          <div class="profile-card">
            <div class="avatar" :style="{ background: selected.color }" />
            <div>
              <div class="profile-name">{{ selected.name }}</div>
              <div class="profile-role">{{ selected.role }}</div>
            </div>
          </div>
          <div class="detail">
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
              <span class="label">错误</span>
              <span class="err">{{ selected.lastError }}</span>
            </div>
          </div>
          <div v-if="world?.activeWorldEvents?.length" class="block-box">
            <h4>当前世界事件</h4>
            <ul>
              <li v-for="ev in world.activeWorldEvents" :key="ev.id">
                {{ ev.content }}
                <span class="mono">（剩 {{ ev.remainingTicks }} Tick）</span>
              </li>
            </ul>
          </div>
        </div>

        <!-- CoT -->
        <div v-show="tab === 'cot'" ref="cotScrollRef" class="tab-body cot-scroll">
          <p v-if="!cotLogs.length" class="empty">尚无思维链。启动模拟或单步后将在此滚动展示。</p>
          <article v-for="log in cotLogs" :key="log.tick + '-' + log.createdAtEpochMs" class="cot-card">
            <header class="cot-head">
              <span class="mono">Tick #{{ log.tick }}</span>
              <span>{{ log.timeDisplay }}</span>
              <span v-if="log.fallback" class="badge bad">FALLBACK</span>
            </header>

            <div class="cot-step">
              <div class="step-label">① 环境感知</div>
              <div class="step-body">{{ log.perceptionSummary }}</div>
            </div>
            <div class="cot-step">
              <div class="step-label">② 检索记忆</div>
              <ul v-if="log.retrievedMemories?.length" class="step-list">
                <li v-for="(m, i) in log.retrievedMemories" :key="i">{{ m }}</li>
              </ul>
              <div v-else class="step-body muted">（无）</div>
            </div>
            <div class="cot-step">
              <div class="step-label">③ 内心独白</div>
              <pre class="thought">{{ log.thought }}</pre>
            </div>
            <div class="cot-step">
              <div class="step-label">④ 最终动作</div>
              <div class="step-body">
                <span class="mono action">{{ log.action }}</span>
                <span v-if="log.targetLocation"> → {{ log.targetLocation }}</span>
                <span v-if="log.targetAgent"> @{{ log.targetAgent }}</span>
                <div v-if="log.speech" class="speech">「{{ log.speech }}」</div>
              </div>
            </div>
            <div class="cot-step metrics">
              <div class="step-label">⑤ 耗时 / Token</div>
              <div class="metrics-row">
                <span>{{ log.latencyMs ?? '—' }} ms</span>
                <span>prompt {{ log.promptTokens ?? '—' }}</span>
                <span>completion {{ log.completionTokens ?? '—' }}</span>
                <span>total {{ log.totalTokens ?? '—' }}</span>
                <span class="muted">{{ log.model || '' }}</span>
              </div>
              <div v-if="log.error" class="err">{{ log.error }}</div>
            </div>
          </article>
        </div>

        <!-- 记忆 -->
        <div v-show="tab === 'memory'" class="tab-body">
          <h4>短期工作记忆</h4>
          <ul v-if="working.length" class="mem-list">
            <li v-for="(m, i) in working" :key="'w' + i">
              <span class="tag">{{ m.type }}</span>
              <span class="mono">{{ m.timeDisplay }}</span>
              {{ m.content }}
            </li>
          </ul>
          <p v-else class="empty">暂无</p>

          <h4 class="mt">长期记忆流</h4>
          <ul v-if="longTerm.length" class="mem-list">
            <li v-for="m in longTerm" :key="m.id">
              <div class="mem-top">
                <span class="tag" :class="m.memoryType.toLowerCase()">{{ m.memoryType }}</span>
                <span class="stars" :title="'重要性 ' + m.importance">{{ stars(m.importance) }}</span>
              </div>
              <div>{{ m.content }}</div>
              <div class="mono muted">
                第{{ m.gameDay }}天 {{ String(m.gameHour).padStart(2, '0') }}:{{
                  String(m.gameMinute).padStart(2, '0')
                }}
                · Tick #{{ m.gameTick }}
              </div>
            </li>
          </ul>
          <p v-else class="empty">暂无长期记忆（需 LLM 决策写入）</p>
        </div>

        <!-- 关系 -->
        <div v-show="tab === 'relation'" class="tab-body">
          <p class="hint-inline">{{ selected.name }} 对其余居民的印象与好感度</p>
          <div v-if="!relations.length" class="empty">关系尚未建立，对话后会更新。</div>
          <div v-for="r in relations" :key="r.toAgentId" class="rel-card">
            <div class="rel-head">
              <strong>{{ r.toAgentName }}</strong>
              <span class="favor" :style="{ color: favorColor(r.favorability) }">
                好感 {{ r.favorability }}
              </span>
            </div>
            <div class="bar-track">
              <div
                class="bar-fill"
                :style="{
                  width: r.favorability + '%',
                  background: favorColor(r.favorability),
                }"
              />
            </div>
            <div class="muted">{{ r.impression }}</div>
            <ul v-if="r.history?.length" class="hist">
              <li v-for="(h, i) in r.history.slice(0, 5)" :key="i">
                <span class="mono">{{ h.timeDisplay }}</span>
                <span :class="h.delta >= 0 ? 'up' : 'down'">
                  {{ h.delta >= 0 ? '+' : '' }}{{ h.delta }}
                </span>
                → {{ h.favorabilityAfter }}
                <span class="muted">{{ h.note }}</span>
              </li>
            </ul>
          </div>
        </div>
      </template>
      <p v-else class="empty">在地图上点击一位居民开始观察</p>
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
  padding: 14px 16px 8px;
  border-bottom: 1px solid var(--border);
}

.panel-head h2 {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
}

.hint,
.hint-inline {
  margin: 6px 0 0;
  font-size: 0.75rem;
  color: var(--text-muted);
  line-height: 1.4;
}

.section {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
}

.section.grow {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border-bottom: none;
  min-height: 0;
}

.section h3,
h4 {
  margin: 0 0 8px;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-muted);
}

h4.mt {
  margin-top: 16px;
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
  padding: 7px 10px;
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

.tabs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-bottom: 1px solid var(--border);
}

.tabs button {
  padding: 8px 4px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 600;
}

.tabs button.on {
  color: var(--text);
  border-bottom-color: var(--accent);
}

.tab-body {
  flex: 1;
  overflow: auto;
  padding: 4px 2px 12px;
  min-height: 0;
}

.cot-scroll {
  scroll-behavior: smooth;
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
}

.profile-name {
  font-weight: 700;
  font-size: 1.05rem;
}

.profile-role {
  color: var(--text-muted);
  font-size: 0.8rem;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.row {
  display: grid;
  grid-template-columns: 56px 1fr;
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

.err {
  color: var(--danger);
  font-size: 0.8rem;
}

.empty {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.cot-card {
  margin-bottom: 12px;
  padding: 10px;
  background: #1b2228;
  border: 1px solid var(--border);
}

.cot-head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.badge {
  font-size: 0.65rem;
  padding: 1px 6px;
  border: 1px solid var(--border);
}

.badge.bad {
  color: var(--danger);
  border-color: #8a4a4a;
}

.cot-step {
  margin-bottom: 8px;
}

.step-label {
  font-size: 0.7rem;
  color: var(--accent);
  margin-bottom: 4px;
  font-weight: 600;
}

.step-body {
  font-size: 0.8rem;
  line-height: 1.45;
}

.step-list {
  margin: 0;
  padding-left: 16px;
  font-size: 0.75rem;
  color: var(--text-muted);
  line-height: 1.4;
}

.action {
  color: var(--warn);
  font-weight: 600;
}

.speech {
  margin-top: 4px;
  color: #d8e6f0;
}

.metrics-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-family: var(--font-mono);
  font-size: 0.72rem;
  color: var(--text-muted);
}

.mem-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mem-list li {
  padding: 8px 10px;
  background: #1b2228;
  border: 1px solid var(--border);
  font-size: 0.8rem;
  line-height: 1.4;
}

.mem-top {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.tag {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  font-size: 0.65rem;
  font-family: var(--font-mono);
  border: 1px solid var(--border);
  color: var(--accent);
}

.tag.dialogue {
  color: var(--warn);
}

.tag.reflection {
  color: var(--success);
}

.stars {
  font-size: 0.65rem;
  letter-spacing: -1px;
  color: #d4a84b;
  white-space: nowrap;
}

.rel-card {
  padding: 10px;
  margin-bottom: 10px;
  background: #1b2228;
  border: 1px solid var(--border);
}

.rel-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}

.favor {
  font-family: var(--font-mono);
  font-weight: 600;
}

.bar-track {
  height: 6px;
  background: #2c353e;
  margin-bottom: 8px;
}

.bar-fill {
  height: 100%;
}

.hist {
  margin: 8px 0 0;
  padding-left: 0;
  list-style: none;
  font-size: 0.72rem;
  color: var(--text-muted);
}

.hist li {
  margin-bottom: 4px;
}

.up {
  color: var(--success);
}

.down {
  color: var(--danger);
}

.block-box {
  margin-top: 12px;
}

.block-box ul {
  margin: 0;
  padding-left: 16px;
  font-size: 0.8rem;
  color: var(--text-muted);
}
</style>
