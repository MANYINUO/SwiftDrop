import axios from 'axios'
import { ElMessage } from 'element-plus'
import auth, { roleOfApiUrl, LOGIN_ROUTE } from '../utils/auth'

/**
 * axios 封装。
 * 后端统一返回 { code, message, data }：成功时在拦截器里剥掉外层直接返回 data；
 * code !== 0 或 HTTP 4xx/5xx 都抛出带中文 message 的错误，业务侧用 toastError 统一提示。
 *
 * 鉴权：请求拦截按接口所属端（admin / rider / user）自动带上对应登录令牌；
 * 不再由前端硬写 X-User-Id / X-Admin-Id —— 身份由后端从令牌解析，前端无法伪造。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  // 已显式带 Authorization（如登出接口）则保留；否则按接口所属端注入对应 token
  if (!config.headers.Authorization) {
    const role = roleOfApiUrl(config.url || '')
    const token = role ? auth.getToken(role) : null
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

/* 防止一批并发请求同时 401 时重复跳转 / 重复弹提示 */
let authRedirecting = false

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      const err = new Error(body.message || '请求失败')
      err.code = body.code
      err.biz = true
      return Promise.reject(err)
    }
    return body
  },
  (error) => {
    const body = error.response?.data
    const status = error.response?.status
    const role = roleOfApiUrl(error.config?.url || '')

    // 401：未登录或登录过期，集中处理跳转 / 自愈
    if (status === 401) {
      handleUnauthorized(role)
      const err = new Error(body?.message || '未登录或登录已过期，请重新登录')
      err.code = body?.code ?? status
      err.status = status
      err.biz = !!body
      return Promise.reject(err)
    }

    // 其余错误：后端给了中文消息就优先用（含 403、503）
    let msg = body?.message
    if (!msg) {
      if (error.code === 'ECONNABORTED' || /timeout/i.test(error.message || '')) {
        msg = '请求超时，请检查网络后重试'
      } else if (!error.response) {
        msg = '网络连接异常，请稍后重试'
      } else if (status === 503) {
        msg = '登录服务暂不可用，请稍后重试'
      } else if (status >= 500) {
        msg = '服务繁忙，请稍后重试'
      } else if (status === 404) {
        msg = '请求的内容不存在'
      } else if (status === 403) {
        // 403 只提示、不跳转（如骑手环号越权），避免把人来回弹走
        msg = '没有操作权限'
      } else {
        msg = '请求失败，请稍后重试'
      }
    }
    const err = new Error(msg)
    err.code = body?.code ?? status
    err.status = status
    err.biz = !!body
    return Promise.reject(err)
  }
)

/** 集中处理 401：后台 / 骑手清登录态并跳登录页；顾客端刷新页面自动补游客身份 */
function handleUnauthorized(role) {
  // 顾客端是匿名游客：令牌失效后刷新一次，main.js 会静默重新领取游客身份
  if (role === auth.USER) {
    if (!sessionStorage.getItem('delivery_guest_reloaded')) {
      sessionStorage.setItem('delivery_guest_reloaded', '1')
      auth.logout(auth.USER)
      location.reload()
    }
    return
  }

  if ((role === auth.ADMIN || role === auth.RIDER) && !authRedirecting) {
    authRedirecting = true
    auth.logout(role)
    ElMessage.warning('登录已过期，请重新登录')
    const loginPath = LOGIN_ROUTE[role].replace(/^#/, '')
    const current = location.hash.replace(/^#/, '')
    const redirect = current && current !== loginPath
      ? `?redirect=${encodeURIComponent(current)}`
      : ''
    location.hash = LOGIN_ROUTE[role] + redirect
    setTimeout(() => {
      authRedirecting = false
    }, 1500)
  }
}

/** 统一错误提示（用户主动操作失败时调用；定时轮询请静默，不要打断用户） */
export function toastError(e, fallback = '操作失败，请稍后重试') {
  ElMessage.error(e?.message || fallback)
}

export default http
