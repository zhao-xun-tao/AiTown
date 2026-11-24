import type { WorldState } from '../types/world'

/**
 * 本地骨架世界：首屏立刻可画，等后端就绪后再用真实快照覆盖。
 * 建筑布局需与后端 TownMapFactory 保持一致。
 */
export function createSkeletonWorld(): WorldState {
  return {
    mapSize: 20,
    buildings: [
      { id: 'plaza', name: '中央广场', type: 'PLAZA', x: 7, y: 7, width: 6, height: 6 },
      { id: 'cafe', name: '林悦咖啡馆', type: 'CAFE', x: 14, y: 7, width: 5, height: 5 },
      { id: 'library', name: '静心图书馆', type: 'LIBRARY', x: 7, y: 0, width: 6, height: 5 },
      { id: 'park-west', name: '西侧公园', type: 'PARK', x: 0, y: 7, width: 5, height: 5 },
      { id: 'park-south', name: '南侧公园', type: 'PARK', x: 7, y: 14, width: 6, height: 5 },
      { id: 'house-nw', name: '西北住宅', type: 'HOUSE', x: 0, y: 0, width: 5, height: 5 },
      { id: 'house-ne', name: '东北住宅', type: 'HOUSE', x: 14, y: 0, width: 5, height: 5 },
      { id: 'house-sw', name: '西南住宅', type: 'HOUSE', x: 0, y: 14, width: 5, height: 5 },
      { id: 'house-se', name: '东南住宅', type: 'HOUSE', x: 14, y: 14, width: 5, height: 5 },
    ],
    agents: [
      {
        id: 'agent-linyue',
        name: '林悦',
        role: '咖啡师',
        persona: '',
        x: 15,
        y: 9,
        state: 'IDLE',
        bubble: '连接中…',
        lastThought: '',
        color: '#E07A5F',
      },
      {
        id: 'agent-chenmo',
        name: '陈默',
        role: '程序员',
        persona: '',
        x: 16,
        y: 2,
        state: 'IDLE',
        bubble: '连接中…',
        lastThought: '',
        color: '#3D5A80',
      },
      {
        id: 'agent-suqing',
        name: '苏晴',
        role: '作家',
        persona: '',
        x: 2,
        y: 2,
        state: 'IDLE',
        bubble: '连接中…',
        lastThought: '',
        color: '#81B29A',
      },
    ],
    recentSpeeches: [],
    activeWorldEvents: [],
    gameTime: { day: 1, hour: 8, minute: 0, tick: 0 },
    simStatus: 'PAUSED',
    timeDisplay: '连接中…',
  }
}
