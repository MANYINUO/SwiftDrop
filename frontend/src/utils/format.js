/**
 * 订单状态：中文名 + 视觉色调。
 *
 * tone 对应 styles/tokens.css 里的 --st-* 三件套（前景 / 底色 / 描边），
 * 页面里不要再写裸十六进制。
 */
export const ORDER_STATUS = {
  PENDING_DISPATCH: { label: '待派单', tone: 'pending-dispatch', icon: 'hourglass' },
  DISPATCHING: { label: '派单中', tone: 'dispatching', icon: 'send' },
  PENDING_PICKUP: { label: '待取货', tone: 'pending-pickup', icon: 'pin' },
  DELIVERING: { label: '配送中', tone: 'delivering', icon: 'scooter' },
  DELIVERED: { label: '已送达', tone: 'delivered', icon: 'check-circle' },
  CANCELLED: { label: '已取消', tone: 'cancelled', icon: 'ban' },
  EXCEPTION: { label: '异常', tone: 'exception', icon: 'alert' },
}

/** 状态推进顺序，用于步骤条 */
export const STATUS_FLOW = [
  'PENDING_DISPATCH',
  'DISPATCHING',
  'PENDING_PICKUP',
  'DELIVERING',
  'DELIVERED',
]

/** 每个环节一句人话，解释"现在在等什么" */
export const STATUS_HINT = {
  PENDING_DISPATCH: '订单已提交，正在为你安排骑手',
  DISPATCHING: '正在为你匹配合适的骑手',
  PENDING_PICKUP: '骑手已接单，正在赶往取件点',
  DELIVERING: '骑手已取货，正在送往目的地',
  DELIVERED: '订单已送达，感谢使用',
  CANCELLED: '订单已取消',
  EXCEPTION: '订单出现异常，客服正在处理',
}

export const DISPATCH_TASK_STATUS = {
  PENDING: { label: '待响应', tone: 'pending-dispatch' },
  ACCEPTED: { label: '已接单', tone: 'delivered' },
  REJECTED: { label: '已拒单', tone: 'exception' },
  TIMEOUT: { label: '已超时', tone: 'cancelled' },
  CANCELLED: { label: '已作废', tone: 'cancelled' },
}

export const OPERATOR_TYPE = {
  USER: '用户',
  RIDER: '骑手',
  SYSTEM: '系统',
  ADMIN: '客服',
}

export const EVENT_TYPE = {
  ORDER_CREATED: '创建订单',
  ORDER_CANCELLED: '取消订单',
  STATUS_CHANGED: '状态变更',
  DISPATCH_CREATED: '开始安排骑手',
  DISPATCH_ACCEPTED: '骑手接单',
  DISPATCH_REJECTED: '骑手暂不接单',
  DISPATCH_TIMEOUT: '骑手未及时响应',
  DISPATCH_EXHAUSTED: '暂无可接单骑手，转人工处理',
  DISPATCH_ROUND_ADVANCE: '继续匹配骑手',
  RIDER_PICKED_UP: '骑手取货',
  RIDER_DELIVERED: '骑手送达',
  RIDER_RELEASED: '重新安排骑手',
  ADMIN_RESOLVED: '人工处理',
  ADMIN_EXCEPTION: '人工标记异常',
  ADMIN_MANUAL_DISPATCH: '人工指派骑手',
}

/** 事件类型 → 图标，用于动态流水的节点样式 */
export const EVENT_ICON = {
  ORDER_CREATED: 'plus',
  ORDER_CANCELLED: 'ban',
  STATUS_CHANGED: 'refresh',
  DISPATCH_CREATED: 'send',
  DISPATCH_ACCEPTED: 'check',
  DISPATCH_REJECTED: 'close',
  DISPATCH_TIMEOUT: 'clock',
  DISPATCH_EXHAUSTED: 'alert',
  DISPATCH_ROUND_ADVANCE: 'layers',
  RIDER_PICKED_UP: 'box',
  RIDER_DELIVERED: 'check-circle',
  RIDER_RELEASED: 'close',
  ADMIN_RESOLVED: 'sliders',
  ADMIN_EXCEPTION: 'alert',
  ADMIN_MANUAL_DISPATCH: 'target',
}

/**
 * 用户端「订单动态」的一句话说明：
 * 后端事件 remark 是调度视角（含轮次、骑手编号等内部信息），不对 C 端用户展示，
 * 这里按事件类型给出用户能看懂的话术；未知类型再回退显示后端原文。
 */
export const EVENT_DETAIL = {
  ORDER_CREATED: '订单已提交，正在为你安排骑手。',
  ORDER_CANCELLED: '订单已取消。',
  STATUS_CHANGED: '订单状态已更新。',
  DISPATCH_CREATED: '已开始为你安排附近骑手。',
  DISPATCH_ACCEPTED: '骑手已接单，正尽快赶来取件。',
  DISPATCH_REJECTED: '这位骑手暂时无法接单，正在为你联系其他骑手。',
  DISPATCH_TIMEOUT: '附近骑手暂时没有响应，正在为你联系其他骑手。',
  DISPATCH_EXHAUSTED: '当前附近暂无可接单骑手，已转人工为你安排。',
  DISPATCH_ROUND_ADVANCE: '仍在为你寻找可用骑手，请稍候。',
  RIDER_PICKED_UP: '骑手已取到物品，正在送往目的地。',
  RIDER_DELIVERED: '物品已送达，感谢使用。',
  RIDER_RELEASED: '正在为你重新安排骑手。',
  ADMIN_RESOLVED: '客服已介入处理你的订单。',
  ADMIN_EXCEPTION: '订单已标记为异常，客服正在跟进。',
  ADMIN_MANUAL_DISPATCH: '客服已为你指派骑手。',
}

/* ==================== 取用函数 ==================== */

export function statusLabel(s) {
  return ORDER_STATUS[s]?.label || s || '—'
}

export function statusTone(s) {
  return ORDER_STATUS[s]?.tone || 'cancelled'
}

export function statusHint(s) {
  return STATUS_HINT[s] || ''
}

export function taskStatusLabel(s) {
  return DISPATCH_TASK_STATUS[s]?.label || s || '—'
}

export function taskStatusTone(s) {
  return DISPATCH_TASK_STATUS[s]?.tone || 'cancelled'
}

export function eventLabel(t) {
  return EVENT_TYPE[t] || t || '—'
}

/** 用户端事件说明：优先用用户化话术，没有则回退后端原文 */
export function eventDetail(t, fallback = '') {
  return EVENT_DETAIL[t] || fallback || ''
}

export function eventIcon(t) {
  return EVENT_ICON[t] || 'dot'
}

export function operatorLabel(t) {
  return OPERATOR_TYPE[t] || t || '—'
}

/* ==================== 格式化 ==================== */

/** 分 → 元，保留两位 */
export function fenToYuan(fen) {
  if (fen == null) return '—'
  return (fen / 100).toFixed(2)
}

/** 时间格式化：MM-DD HH:mm:ss */
export function formatTime(v) {
  if (!v) return '—'
  const d = typeof v === 'string' ? new Date(v.replace(' ', 'T')) : new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 只要 HH:mm，用于时间线这类密集场景 */
export function formatClock(v) {
  if (!v) return '—'
  const d = typeof v === 'string' ? new Date(v.replace(' ', 'T')) : new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}`
}

/** MM-DD HH:mm，表格里用：比 formatTime 少 3 个字符，列宽能收紧一档 */
export function formatTimeShort(v) {
  if (!v) return '—'
  const d = typeof v === 'string' ? new Date(v.replace(' ', 'T')) : new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

/** 相对时间：刚刚 / x 分钟前 / x 小时前 */
export function fromNow(v) {
  if (!v) return '—'
  const d = typeof v === 'string' ? new Date(v.replace(' ', 'T')) : new Date(v)
  if (Number.isNaN(d.getTime())) return '—'
  const diff = Math.floor((Date.now() - d.getTime()) / 1000)
  if (diff < 15) return '刚刚'
  if (diff < 60) return `${diff} 秒前`
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  return `${Math.floor(diff / 86400)} 天前`
}

/** 秒 → "x 分 y 秒" */
export function formatDuration(sec) {
  if (sec == null) return '—'
  const s = Math.round(Number(sec))
  if (s < 60) return `${s} 秒`
  return `${Math.floor(s / 60)} 分 ${s % 60} 秒`
}

/** 距离格式化 */
export function formatDistance(m) {
  if (m == null) return '—'
  return m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(2)} km`
}
