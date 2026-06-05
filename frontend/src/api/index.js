import http from './http'
import auth from '../utils/auth'

/* ==================== 登录 / 登出 ==================== */

export const authApi = {
  /** 后台账号密码登录 */
  adminLogin(username, password) {
    return http.post('/auth/admin/login', { username, password })
  },
  /** 骑手登录：骑手编号 + 手机号后 4 位 */
  riderLogin(riderId, pin) {
    return http.post('/auth/rider/login', { riderId, pin })
  },
  /** 顾客端匿名登录（前端首次打开时静默调用） */
  guest() {
    return http.post('/auth/guest')
  },
  /** 登出并作废当前端的令牌 */
  logout(role) {
    const t = auth.getToken(role)
    return http.post('/auth/logout', null, {
      headers: t ? { Authorization: `Bearer ${t}` } : {},
    })
  },
}

/* ==================== 用户端 ==================== */

export const userApi = {
  /** 创建订单。idempotencyKey 相同则只会产生一笔订单 */
  createOrder(payload, idempotencyKey) {
    return http.post('/user/orders', payload, {
      headers: { 'Idempotency-Key': idempotencyKey },
    })
  },
  getOrder(orderNo) {
    return http.get(`/user/orders/${orderNo}`)
  },
  listOrders(params) {
    return http.get('/user/orders', { params })
  },
  cancelOrder(orderNo, reason) {
    return http.post(`/user/orders/${orderNo}/cancel`, null, { params: { reason } })
  },
  track(orderNo) {
    return http.get(`/user/orders/${orderNo}/track`)
  },
  events(orderNo) {
    return http.get(`/user/orders/${orderNo}/events`)
  },
}

/* ==================== 骑手端 ==================== */

export const riderApi = {
  /** 登录页骑手名单（公开接口，精简字段：不含手机号 / 坐标） */
  options() {
    return http.get('/rider/options')
  },
  online(riderId, lng, lat) {
    return http.post(`/rider/${riderId}/online`, null, { params: { lng, lat } })
  },
  offline(riderId) {
    return http.post(`/rider/${riderId}/offline`)
  },
  reportLocation(riderId, payload) {
    return http.post(`/rider/${riderId}/location`, payload)
  },
  detail(riderId) {
    return http.get(`/rider/${riderId}`)
  },
  /** 全部骑手（需骑手登录） */
  list() {
    return http.get('/rider/list')
  },
  /** 在线骑手（需骑手登录） */
  onlineList() {
    return http.get('/rider/online')
  },
  pendingTasks(riderId) {
    return http.get(`/rider/${riderId}/dispatch-tasks`)
  },
  /** 骑手手上进行中的订单（已接单未送达）—— 刷新页面后靠它恢复状态 */
  currentOrders(riderId) {
    return http.get(`/rider/${riderId}/orders`)
  },
  accept(riderId, taskId) {
    return http.post(`/rider/${riderId}/dispatch-tasks/${taskId}/accept`)
  },
  reject(riderId, taskId, reason) {
    return http.post(`/rider/${riderId}/dispatch-tasks/${taskId}/reject`, null, {
      params: { reason },
    })
  },
  pickup(riderId, orderNo) {
    return http.post(`/rider/${riderId}/orders/${orderNo}/pickup`)
  },
  delivered(riderId, orderNo) {
    return http.post(`/rider/${riderId}/orders/${orderNo}/delivered`)
  },
}

/* ==================== 调度后台 ==================== */

export const adminApi = {
  orders(params) {
    return http.get('/admin/orders', { params })
  },
  orderDetail(orderNo) {
    return http.get(`/admin/orders/${orderNo}`)
  },
  orderEvents(orderNo) {
    return http.get(`/admin/orders/${orderNo}/events`)
  },
  dispatchTasks(orderNo) {
    return http.get(`/admin/orders/${orderNo}/dispatch-tasks`)
  },
  /** 不传 riderId 则按算法重派一轮 */
  dispatch(orderNo, riderId) {
    return http.post(`/admin/orders/${orderNo}/dispatch`, null, { params: { riderId } })
  },
  markException(orderNo, remark) {
    return http.post(`/admin/orders/${orderNo}/exception`, null, { params: { remark } })
  },
  resolve(orderNo, action, reason, riderId) {
    return http.post(`/admin/orders/${orderNo}/resolve`, null, {
      params: { action, reason, riderId },
    })
  },
  riders() {
    return http.get('/admin/riders')
  },
  onlineRiders() {
    return http.get('/admin/riders/online')
  },
  metrics() {
    return http.get('/admin/metrics')
  },
  orderStatus() {
    return http.get('/admin/metrics/order-status')
  },
}
