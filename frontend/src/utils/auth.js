/**
 * 登录态管理（前端侧）。
 *
 * 三端身份分别存储，便于在同一个浏览器里并行演示顾客端 / 骑手端 / 调度后台：
 *   delivery_auth_admin、delivery_auth_rider、delivery_auth_user
 *
 * 注意：这里只是"体验层"的登录态（决定请求带哪个 token、路由守卫把人引导到哪），
 * 真正的身份与权限校验在后端 AuthFilter / 握手拦截器，改本地存储无法越权。
 */
const KEYS = {
  admin: 'delivery_auth_admin',
  rider: 'delivery_auth_rider',
  user: 'delivery_auth_user',
}

function read(role) {
  try {
    return JSON.parse(localStorage.getItem(KEYS[role]) || 'null')
  } catch (e) {
    return null
  }
}

export const auth = {
  ADMIN: 'admin',
  RIDER: 'rider',
  USER: 'user',

  get(role) {
    return read(role)
  },
  getToken(role) {
    return read(role)?.token || null
  },
  getId(role) {
    return read(role)?.id ?? null
  },
  isLoggedIn(role) {
    return !!read(role)?.token
  },
  /** 保存登录返回的 { token, id, name, riderNo? } */
  set(role, data) {
    localStorage.setItem(KEYS[role], JSON.stringify(data))
  },
  logout(role) {
    localStorage.removeItem(KEYS[role])
  },
}

/** 根据接口路径前缀判断它属于哪个端 */
export function roleOfApiUrl(url = '') {
  if (url.includes('/admin/')) return auth.ADMIN
  if (url.includes('/rider/')) return auth.RIDER
  if (url.includes('/user/')) return auth.USER
  return null
}

/** 各端登录页的 hash 路径（顾客端为游客免登录，没有登录页） */
export const LOGIN_ROUTE = {
  admin: '#/login/admin',
  rider: '#/login/rider',
}

export default auth
