import { onUnmounted, ref, type Ref } from 'vue'
import type { WorldState } from '../types/world'

/**
 * 连接后端 /ws/world，接收每 Tick 广播的世界快照。
 * 断线后自动重连（带退避）。
 */
export function useWorldSocket(onState: (state: WorldState) => void) {
  const connected: Ref<boolean> = ref(false)
  let socket: WebSocket | null = null
  let disposed = false
  let retryMs = 1000
  let retryTimer: ReturnType<typeof setTimeout> | null = null

  function wsUrl(): string {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    // 开发期直连后端 8080；生产可改为同源反代
    const host = import.meta.env.DEV ? 'localhost:8080' : location.host
    return `${proto}://${host}/ws/world`
  }

  function connect() {
    if (disposed) return
    socket = new WebSocket(wsUrl())

    socket.onopen = () => {
      connected.value = true
      retryMs = 1000
    }

    socket.onmessage = (ev) => {
      try {
        const state = JSON.parse(ev.data as string) as WorldState
        onState(state)
      } catch (e) {
        console.warn('解析世界状态失败', e)
      }
    }

    socket.onclose = () => {
      connected.value = false
      scheduleReconnect()
    }

    socket.onerror = () => {
      socket?.close()
    }
  }

  function scheduleReconnect() {
    if (disposed) return
    if (retryTimer) clearTimeout(retryTimer)
    retryTimer = setTimeout(() => {
      retryMs = Math.min(retryMs * 1.5, 10000)
      connect()
    }, retryMs)
  }

  function disconnect() {
    disposed = true
    if (retryTimer) clearTimeout(retryTimer)
    socket?.close()
    socket = null
    connected.value = false
  }

  connect()
  onUnmounted(disconnect)

  return { connected, disconnect }
}
