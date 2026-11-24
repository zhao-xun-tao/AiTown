# AI Town Game Engine — 模块 1–8 设计（全部完成）

**日期**: 2026-11-24  
**状态**: 已实现  
**寻路方案**: 混合（后端 A\* 粗路径 + 前端插值/局部重寻路）

## 1–4

- 多层 Tilemap（Ground/Path）+ 碰撞网格
- 建筑/树/角色 Y-Sorting
- 动画 FSM：Idle/Walk/Talk/Think
- 后端 `PathfindingService` + Agent.`path`/`targetX/Y`；快照完整下发

## 5 昼夜灯光

- `DayNight.ts` 按 `gameTime.hour` 分清晨/白天/黄昏/夜晚
- 夜幕遮罩 + 路灯/窗光光晕

## 6 交互

- 滚轮缩放、拖拽平移、双击/复位全景、选中跟随
- 点击角色 → 观察面板 CoT；点击建筑 → 在场角色/近期发言
- God Mode 事件投放 + 场景 Toast/闪屏

## 7 Game Feel

- 行走脚步 bob、到达短停、气泡弹出缩放、选中高亮环

## 8 后端容错

- Tick 单 Agent 异常隔离
- LLM fallback 已有；路径字段可回放
- 前端无 path 时本地 A\* 兜底
