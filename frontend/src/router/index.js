import { createRouter, createWebHashHistory } from 'vue-router'
import auth from '../utils/auth'

// 用 hash 模式：部署到任意静态服务器都不用配 rewrite 规则
const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/Home.vue'),
    meta: { title: '概览' },
  },

  // ---------------- 登录页 ----------------
  {
    path: '/login/admin',
    name: 'login-admin',
    component: () => import('../views/auth/AdminLogin.vue'),
    meta: { title: '调度后台登录', public: true },
  },
  {
    path: '/login/rider',
    name: 'login-rider',
    component: () => import('../views/auth/RiderLogin.vue'),
    meta: { title: '骑手登录', public: true },
  },

  // ---------------- 用户端（游客免登录，身份由 main.js 启动时静默领取）----------------
  {
    path: '/user',
    children: [
      { path: '', redirect: '/user/create' },
      {
        path: 'create',
        name: 'user-create',
        component: () => import('../views/user/CreateOrder.vue'),
        meta: { title: '创建订单' },
      },
      {
        path: 'orders',
        name: 'user-orders',
        component: () => import('../views/user/OrderList.vue'),
        meta: { title: '我的订单' },
      },
      {
        path: 'orders/:orderNo',
        name: 'user-track',
        component: () => import('../views/user/OrderTrack.vue'),
        meta: { title: '订单追踪' },
      },
    ],
  },

  // ---------------- 骑手端 ----------------
  {
    path: '/rider/:riderId',
    name: 'rider-console',
    component: () => import('../views/rider/RiderConsole.vue'),
    props: true,
    meta: { title: '骑手工作台' },
  },

  // ---------------- 调度后台 ----------------
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/admin/dashboard' },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('../views/admin/Dashboard.vue'),
        meta: { title: '指标看板' },
      },
      {
        path: 'orders',
        name: 'admin-orders',
        component: () => import('../views/admin/OrderManage.vue'),
        meta: { title: '订单管理' },
      },
      {
        path: 'riders',
        name: 'admin-riders',
        component: () => import('../views/admin/RiderManage.vue'),
        meta: { title: '骑手管理' },
      },
    ],
  },

  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

/*
 * 路由守卫（体验层）：把未登录 / 走错端的人引导到正确页面。
 * 这一层可以被手动改地址绕过，但真正的权限校验在后端 AuthFilter / WS 握手拦截器，
 * 绕过前端直接调接口同样会被 401 / 403 挡下。
 */
router.beforeEach((to, from, next) => {
  // 已登录再打开登录页：直接进对应主页
  if (to.path === '/login/admin') {
    return auth.isLoggedIn(auth.ADMIN) ? next('/admin/dashboard') : next()
  }
  if (to.path === '/login/rider') {
    return auth.isLoggedIn(auth.RIDER)
      ? next(`/rider/${auth.getId(auth.RIDER)}`)
      : next()
  }

  // 调度后台：需要管理员
  if (to.path.startsWith('/admin')) {
    if (!auth.isLoggedIn(auth.ADMIN)) {
      return next({ path: '/login/admin', query: { redirect: to.fullPath } })
    }
    return next()
  }

  // 骑手工作台：需要骑手，且只能进自己的编号
  if (to.path.startsWith('/rider/')) {
    const rider = auth.get(auth.RIDER)
    if (!rider || !rider.token) {
      return next({ path: '/login/rider', query: { redirect: to.fullPath } })
    }
    const m = /^\/rider\/(\d+)/.exec(to.path)
    if (m && String(rider.id) !== m[1]) {
      // 已登录却访问他人工作台：直接弹回自己的页面
      return next(`/rider/${rider.id}`)
    }
    return next()
  }

  // 其余页面（首页、顾客端、登录页本身）放行
  next()
})

const APP_NAME = '同城即时配送调度平台'

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · ${APP_NAME}` : APP_NAME
})

export default router
