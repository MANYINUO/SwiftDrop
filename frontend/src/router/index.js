import { createRouter, createWebHashHistory } from 'vue-router'

// 用 hash 模式：部署到任意静态服务器都不用配 rewrite 规则
const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/Home.vue'),
    meta: { title: '概览' },
  },

  // ---------------- 用户端 ----------------
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

  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

const APP_NAME = '同城即时配送调度平台'

router.afterEach((to) => {
  document.title = to.meta?.title ? `${to.meta.title} · ${APP_NAME}` : APP_NAME
})

export default router
