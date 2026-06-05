<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../../api'
import auth from '../../utils/auth'
import AppIcon from '../../components/AppIcon.vue'

const route = useRoute()
const router = useRouter()

const MENUS = [
  { path: '/admin/dashboard', label: '指标看板', icon: 'gauge', desc: '运行指标与分布' },
  { path: '/admin/orders', label: '订单管理', icon: 'list', desc: '派单与异常处置' },
  { path: '/admin/riders', label: '骑手管理', icon: 'users', desc: '在线状态与负载' },
]

const active = computed(() => route.path)

const adminName = computed(() => auth.get(auth.ADMIN)?.name || '管理员')

async function logout() {
  try {
    await authApi.logout(auth.ADMIN)
  } catch {
    /* 后端注销失败也照常清前端登录态 */
  }
  auth.logout(auth.ADMIN)
  ElMessage.success('已退出登录')
  router.push('/login/admin')
}
</script>

<template>
  <div class="admin">
    <!-- ==================== 侧栏 ==================== -->
    <aside class="side">
      <div class="side-head">
        <span class="side-mark">
          <AppIcon name="sliders" :size="16" :stroke-width="2" />
        </span>
        <div class="side-titles">
          <span class="side-title">调度后台</span>
          <span class="side-sub">运营调度中心</span>
        </div>
      </div>

      <nav class="side-nav">
        <router-link
          v-for="m in MENUS"
          :key="m.path"
          :to="m.path"
          class="nav-item"
          :class="{ on: active === m.path }"
        >
          <span class="nav-icon">
            <AppIcon :name="m.icon" :size="16" :stroke-width="1.9" />
          </span>
          <span class="nav-copy">
            <span class="nav-label">{{ m.label }}</span>
            <span class="nav-desc">{{ m.desc }}</span>
          </span>
        </router-link>

        <button type="button" class="nav-logout" @click="logout">退出登录</button>
      </nav>

      <div class="side-foot">
        <span class="foot-row">
          <span class="live-dot"></span>
          {{ adminName }} · 在线
        </span>
      </div>
    </aside>

    <!-- ==================== 内容 ==================== -->
    <section class="content">
      <router-view />
    </section>
  </div>
</template>

<style scoped>
.admin {
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  gap: var(--sp-5);
  align-items: start;
}

/* ============================================================
   侧栏：深色浮层面板
   ============================================================ */
.side {
  position: sticky;
  top: calc(var(--header-h) + var(--sp-5));
  display: flex;
  flex-direction: column;
  border-radius: var(--r-xl);
  background: linear-gradient(178deg, var(--ink-800), var(--ink-900) 55%);
  border: 1px solid var(--ink-700);
  box-shadow: 0 18px 40px -20px rgb(11 18 32 / 0.55);
  overflow: hidden;
}

/* 顶部一道极细的品牌光带，避免整块死黑 */
.side::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, var(--brand), rgb(242 96 12 / 0) 78%);
}

.side-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: var(--sp-5) var(--sp-4) var(--sp-4);
  border-bottom: 1px solid rgb(255 255 255 / 0.07);
}

.side-mark {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: var(--r-md);
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(150deg, var(--brand-400), var(--brand-600));
  box-shadow: 0 2px 8px rgb(242 96 12 / 0.4), inset 0 1px 0 rgb(255 255 255 / 0.25);
}

.side-titles {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.side-title {
  font-size: var(--fs-14);
  font-weight: 700;
  color: #f1f5f9;
  letter-spacing: -0.01em;
}

.side-sub {
  font-size: 10px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--ink-400);
}

/* ---------- 导航 ---------- */
.side-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-3);
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: var(--r-md);
  color: var(--ink-300);
  transition: background-color var(--dur-fast) var(--ease-in-out),
    color var(--dur-fast) var(--ease-in-out);
}

.nav-item:hover {
  background: rgb(255 255 255 / 0.05);
  color: #e2e8f0;
}

.nav-item.on {
  background: rgb(242 96 12 / 0.14);
  color: #fff;
}

/* 选中项左侧的品牌色指示条 */
.nav-item.on::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 2px;
  height: 18px;
  border-radius: 0 2px 2px 0;
  background: var(--brand);
}

.nav-icon {
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  border-radius: var(--r-sm);
  display: grid;
  place-items: center;
  background: rgb(255 255 255 / 0.05);
  color: inherit;
}

.nav-item.on .nav-icon {
  background: var(--brand);
  color: #fff;
}

.nav-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  line-height: 1.25;
}

.nav-label {
  font-size: var(--fs-13);
  font-weight: 600;
}

.nav-desc {
  font-size: 10px;
  opacity: 0.6;
}

/* ---------- 退出登录 ---------- */
.nav-logout {
  margin: var(--sp-2) var(--sp-3) 0;
  height: 36px;
  border-radius: var(--r-md);
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--ink-300);
  background: rgb(255 255 255 / 0.04);
  border: 1px solid rgb(255 255 255 / 0.1);
  cursor: pointer;
  transition: background-color var(--dur-fast) var(--ease-in-out),
    color var(--dur-fast) var(--ease-in-out), border-color var(--dur-fast) var(--ease-in-out);
}

.nav-logout:hover {
  color: #fda29b;
  background: rgb(245 72 72 / 0.12);
  border-color: rgb(245 72 72 / 0.4);
}

/* ---------- 侧栏页脚 ---------- */
.side-foot {
  margin-top: auto;
  padding: var(--sp-4);
  border-top: 1px solid rgb(255 255 255 / 0.07);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.foot-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-11);
  font-weight: 600;
  color: var(--ink-300);
  font-family: var(--num-font);
}

.foot-hint {
  font-size: 10px;
  color: var(--ink-400);
}

/* ============================================================
   内容
   ============================================================ */
.content {
  min-width: 0;
}

/* 平板及以下：侧栏变成横向可滚动的导航条，内容占满整行 */
@media (max-width: 1023px) {
  .admin {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--sp-4);
  }

  .side {
    position: static;
    border-radius: var(--r-lg);
  }

  .side-head {
    padding: var(--sp-3) var(--sp-4);
  }

  .side-nav {
    flex-direction: row;
    gap: var(--sp-2);
    padding: var(--sp-2) var(--sp-3) var(--sp-3);
    overflow-x: auto;
    scrollbar-width: none;
    mask-image: linear-gradient(to right, #000 0, #000 calc(100% - 22px), transparent 100%);
    -webkit-mask-image: linear-gradient(to right, #000 0, #000 calc(100% - 22px), transparent 100%);
  }

  .side-nav::-webkit-scrollbar {
    display: none;
  }

  .nav-item {
    flex: 0 0 auto;
    padding: 7px 12px;
    border: 1px solid rgb(255 255 255 / 0.08);
  }

  .nav-item.on {
    border-color: rgb(242 96 12 / 0.4);
  }

  /* 横向排布时左侧竖条改成整圈描边，不再需要 */
  .nav-item.on::before {
    display: none;
  }

  .nav-desc {
    display: none;
  }

  .nav-logout {
    flex: 0 0 auto;
    margin: 0;
    padding: 0 14px;
    height: auto;
    align-self: center;
    white-space: nowrap;
  }

  .side-foot {
    display: none;
  }
}
</style>
