import auth from './auth'

/**
 * WebSocket 客户端封装。
 *
 * 关键点：
 *  1. **自动带登录令牌**：浏览器原生 WebSocket 不能自定义请求头，令牌按通道拼在
 *     {@code ?token=xxx}，后端握手拦截器据此校验身份（骑手通道还会核对编号）。
 *  2. **自动重连**（指数退避，最长 15 秒一次）。
 *  3. **重连成功后回调 onReconnect**，调用方应在此时调 /track 拉一次全量状态
 *     —— 断线期间漏掉的推送补不回来，只能靠拉全量兜底。
 *  4. URL 用相对路径（同源），由 Vite 的 proxy 转发到后端，避免跨域。
 */
function tokenForWsPath(path) {
  if (path.includes('/ws/rider/')) return auth.getToken(auth.RIDER)
  if (path.includes('/ws/user/')) return auth.getToken(auth.USER)
  return null
}

export function createWsClient(path, { onMessage, onOpen, onClose, onReconnect } = {}) {
  const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = tokenForWsPath(path)
  const qs = token ? `?token=${encodeURIComponent(token)}` : ''
  const url = `${proto}//${location.host}${path}${qs}`

  let ws = null
  let retry = 0
  let timer = null
  let closedByUser = false
  let everConnected = false

  function connect() {
    try {
      ws = new WebSocket(url)
    } catch (e) {
      scheduleReconnect()
      return
    }

    ws.onopen = () => {
      retry = 0
      if (everConnected) {
        // 断线重连成功：通知调用方拉全量
        onReconnect?.()
      }
      everConnected = true
      onOpen?.()
    }

    ws.onmessage = (evt) => {
      try {
        onMessage?.(JSON.parse(evt.data))
      } catch {
        /* 忽略非 JSON 消息 */
      }
    }

    ws.onclose = () => {
      onClose?.()
      if (!closedByUser) scheduleReconnect()
    }

    ws.onerror = () => {
      try { ws.close() } catch { /* ignore */ }
    }
  }

  function scheduleReconnect() {
    const delay = Math.min(1000 * 2 ** retry, 15000)
    retry += 1
    clearTimeout(timer)
    timer = setTimeout(connect, delay)
  }

  connect()

  return {
    close() {
      closedByUser = true
      clearTimeout(timer)
      try { ws?.close() } catch { /* ignore */ }
    },
    /** 0=连接中 1=已连接 2=关闭中 3=已关闭 */
    get readyState() {
      return ws?.readyState ?? 3
    },
  }
}
