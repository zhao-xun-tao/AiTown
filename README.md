# AI Town · 多智能体社会模拟沙盒

用于 AI 游戏研究的 Web 端 Generative Agents 沙盒。已实现：**世界时钟 + Canvas + LLM 决策 + 双层记忆 + 检索/反思 + 科研观察面板（CoT / 记忆 / 关系）+ God Mode 世界事件**。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.2、WebSocket、MyBatis-Plus、MySQL 8、Redis |
| 前端 | Vue 3、TypeScript、Vite、HTML5 Canvas 2D |

## 目录结构

```
game/
├── docker-compose.yml   # MySQL + Redis
├── backend/
├── frontend/
├── .cursorrules
└── README.md
```

## 环境要求

- JDK 17+、Maven 3.8+、Node.js 18+
- Docker（推荐，用于 MySQL / Redis）
- OpenAI 兼容 API Key（可选；无 Key 时降级 Mock）

## 启动步骤

### 0. 启动基础设施

```bash
docker compose up -d
```

等待 MySQL / Redis healthy 后继续。

### 1. 启动后端

```bash
cd backend
# PowerShell 示例
$env:LLM_API_KEY="sk-..."
mvn spring-boot:run
```

- REST：`http://localhost:8080/api/world/state`
- 记忆：`http://localhost:8080/api/agents/{agentId}/memories`
- WebSocket：`ws://localhost:8080/ws/world`

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器：`http://localhost:5173`

## 双层记忆（Generative Agents）

| 层 | 存储 | 作用 |
|---|---|---|
| 短期工作记忆 | Redis List，最近 10 条 | 每次决策自动带入 Prompt |
| 长期记忆流 | MySQL `agent_memory` | 观察 / 对话 / 反思，含重要性 1-10 |
| 检索 | 时间衰减 + 重要性 + 关键词相关 | Top-5 注入 Prompt |
| 反思 | 累计重要性 ≥ 30 | LLM 提炼高层次认知写回 REFLECTION |

决策 JSON 额外字段：

```json
{
  "thought": "...",
  "action": "MOVE | TALK | IDLE",
  "targetLocation": "...",
  "targetAgent": "...",
  "speech": "...",
  "memoryNote": "本回合写入长期记忆的一句话",
  "memoryImportance": 5,
  "memoryType": "OBSERVATION | DIALOGUE"
}
```

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/world/state` | 世界快照 |
| POST | `/api/sim/start` | 开始 |
| POST | `/api/sim/pause` | 暂停 |
| POST | `/api/sim/step` | 单步 |
| GET | `/api/sim/status` | 状态 |
| GET | `/api/agents/{id}/memories` | 短期+长期记忆 |
| GET | `/api/agents/{id}/cot` | 思维链回合日志 |
| GET | `/api/agents/{id}/relations` | 好感度/印象图谱 |
| POST | `/api/world/events` | 上帝视角投放世界事件 |
| GET | `/api/world/events` | 活跃/历史世界事件 |
| WS | `/ws/world` | Tick 广播 |

## 配置摘要

```yaml
memory:
  enabled: true
  working-memory-size: 10
  retrieve-top-k: 5
  reflection-importance-threshold: 30
  weight-recency: 1.0
  weight-importance: 1.0
  weight-relevance: 1.0
  recency-decay-lambda: 0.995

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/aitown?...
    username: aitown
    password: aitown
  data:
    redis:
      host: localhost
      port: 6379
```

## 初始居民

| 姓名 | 角色 | 出生点 |
|---|---|---|
| 林悦 | 咖啡师 | 咖啡馆附近 |
| 陈默 | 程序员 | 东北住宅 |
| 苏晴 | 作家 | 西北住宅 |

## 后续可做

- Agent 档案表与实验日志持久化  
- Redis 缓存实时坐标 / 世界时钟  
- 观察面板展示完整 Prompt / Token  
- 向量检索替代关键词 Jaccard  

## 故障排查

- 后端起不来：先 `docker compose ps`，确认 3306/6379  
- 前端连不上：确认后端 8080  
- Agent 无记忆：需启用 LLM 并成功决策（Mock 模式不写长期记忆）  
- 反思不触发：累计 `memoryImportance` 需达到阈值 30  
