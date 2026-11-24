# AI Town · 多智能体社会模拟沙盒

用于 AI 游戏研究的 Web 端 Generative Agents 沙盒。当前已跑通 **世界时钟 + Canvas + LLM 自主决策闭环**（OpenAI 兼容接口）；MySQL / Redis 仍待后续接入。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Java 17、Spring Boot 3.2、WebSocket |
| 前端 | Vue 3、TypeScript、Vite、HTML5 Canvas 2D |

## 目录结构

```
game/
├── backend/          # Spring Boot 服务（默认端口 8080）
├── frontend/         # Vue3 研究工作台（默认端口 5173）
├── .cursorrules
└── README.md
```

## 环境要求

- JDK 17+
- Maven 3.8+（或使用 IDE 内置 Maven）
- Node.js 18+ / npm

## 启动步骤

### 1. 启动后端

```bash
cd backend
mvn spring-boot:run
```

启动成功后可验证：

- REST：`http://localhost:8080/api/world/state`
- WebSocket：`ws://localhost:8080/ws/world`

### 2. 启动前端

```bash
cd frontend
npm install
npm run dev
```

浏览器打开：`http://localhost:5173`

### 3. 操作说明

1. 顶部查看**世界时间**与 WS 连接状态  
2. 点击 **开始**：每 **2 秒**自动推进 1 Tick（游戏内 +10 分钟）  
3. **暂停**停止自动推进  
4. **单步**手动推进 1 Tick  
5. 点击地图居民或右侧列表，查看人设 / 气泡 / Mock 思考  

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/world/state` | 完整世界快照 |
| POST | `/api/sim/start` | 开始自动 Tick |
| POST | `/api/sim/pause` | 暂停 |
| POST | `/api/sim/step` | 单步推进 |
| GET | `/api/sim/status` | 引擎状态 |
| WS | `/ws/world` | 每 Tick 广播 `WorldState` JSON |

所有 REST 响应统一为：

```json
{ "code": 0, "message": "success", "data": { ... } }
```

## 初始居民

| 姓名 | 角色 | 出生点 |
|---|---|---|
| 林悦 | 咖啡师 | 咖啡馆附近 |
| 陈默 | 程序员 | 东北住宅 |
| 苏晴 | 作家 | 西北住宅 |

## 配置

`backend/src/main/resources/application.yml`：

```yaml
aitown:
  tick-interval-ms: 5000   # LLM 模式下建议 ≥ 5s
  minutes-per-tick: 10
  start-hour: 8
  start-minute: 0

llm:
  enabled: true
  base-url: https://api.openai.com/v1   # 可换成兼容网关
  api-key: ${LLM_API_KEY:}
  model: gpt-4o-mini
  timeout-ms: 15000
  perception-radius: 3
```

### 启用真实 LLM

```bash
# Windows PowerShell
$env:LLM_API_KEY="sk-..."

# 或在 application.yml 直接填写 llm.api-key
```

- `llm.enabled=true` 且配置了 `api-key`：走感知 → Prompt → LLM → MOVE/TALK/IDLE  
- 未配置 key 或 `enabled=false`：自动降级为 Phase 1 Mock 决策  
- LLM 超时 / 非 JSON：该 Agent 本 Tick 强制 IDLE，模拟不中断  

### 决策 JSON 约定

```json
{
  "thought": "内心独白与规划",
  "action": "MOVE | TALK | IDLE",
  "targetLocation": "咖啡馆/公园等或null",
  "targetAgent": "对方名字或null",
  "speech": "说出的话或null"
}
```

TALK 会写入区域事件，**下一 Tick** 附近 Agent 可感知并回应。

## 后续阶段（未实现）

- MySQL + MyBatis-Plus：Agent 档案、记忆流、实验日志  
- Redis：世界时钟、实时坐标、短期工作记忆  
- 右侧观察面板：完整 Prompt / Token 消耗明细  

## 故障排查

- 前端显示「无法连接后端」：确认后端已在 8080 启动  
- WS 一直断开：检查防火墙 / 代理，开发期前端直连 `ws://localhost:8080/ws/world`  
- 端口占用：修改 `application.yml` 的 `server.port` 或 Vite `server.port`  
- Agent 一直 IDLE 且日志报 LLM 失败：检查 `LLM_API_KEY`、`base-url`、网络与模型名
