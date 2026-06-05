<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './components/AppIcon.vue'

/**
 * 应用外壳。
 *
 * F1 阶段：只有用户端，导航只保留"概览"和"用户端"。
 * 后面 F2/F6 加上骑手端和后台后，再把对应导航加回来。
 */
const route = useRoute()
const router = useRouter()

const navs = [
  { key: 'home', label: '概览', short: '概览', icon: 'grid', path: '/' },
  { key: 'user', label: '用户端', short: '用户', icon: 'user', path: '/user/create' },
]

const activeKey = computed(() => {
  const p = route.path
  if (p.startsWith('/user')) return 'user'
  return 'home'
})

const pageTitle = computed(() => route.meta?.title || '概览')

/* ---------- 顶栏时钟 ---------- */
const now = ref(new Date())
let timer = null

const clock = computed(() => {
  const d = now.value
  const p = (n) => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
})

const today = computed(() => {
  const d = now.value
  const wd = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 周${wd}`
})

onMounted(() => {
  timer = setInterval(() => (now.value = new Date()), 1000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="shell">
    <header class="topbar">
      <div class="brand" @click="router.push('/')">
        <span class="brand-mark"><AppIcon name="zap" :size="17" :stroke-width="2" /></span>
        <span class="brand-text">
          <span class="brand-name">SwiftDrop</span>
          <span class="brand-sub">同城即时配送调度平台</span>
        </span>
      </div>

      <!-- 移动端：导航在底部标签栏，这里只显示当前页标题 -->
      <div class="topbar-title">{{ pageTitle }}</div>

      <nav class="segmented" role="tablist" aria-label="主导航">
        <router-link
          v-for="n in navs"
          :key="n.key"
          :to="n.path"
          class="seg-item"
          :class="{ active: activeKey === n.key }"
          role="tab"
          :aria-selected="activeKey === n.key"
        >
          <AppIcon :name="n.icon" :size="15" />
          <span>{{ n.label }}</span>
        </router-link>
      </nav>

      <div class="topbar-meta">
        <span class="meta-date">{{ today }}</span>
        <span class="meta-clock num">{{ clock }}</span>
      </div>
    </header>

    <main class="main">
      <div class="container">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </main>

    <!-- 移动端底部标签栏 -->
    <nav class="tabbar" aria-label="主导航">
      <router-link
        v-for="n in navs"
        :key="n.key"
        :to="n.path"
        class="tab"
        :class="{ active: activeKey === n.key }"
        :aria-current="activeKey === n.key ? 'page' : undefined"
      >
        <AppIcon :name="n.icon" :size="20" :stroke-width="1.9" />
        <span class="tab-label">{{ n.short }}</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.shell {
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  background: var(--bg-app);
}

/* ============================================================
   顶栏
   ============================================================ */
.topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
  height: var(--header-h);
  flex: 0 0 var(--header-h);
  display: flex;
  align-items: center;
  gap: var(--sp-5);
  padding: 0 var(--gutter);
  background: rgb(255 255 255 / 0.86);
  backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--border);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
  flex: 0 0 auto;
}

.brand-mark {
  width: 30px;
  height: 30px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(150deg, var(--brand-400), var(--brand-600));
  box-shadow: 0 2px 6px rgb(242 96 12 / 0.3), inset 0 1px 0 rgb(255 255 255 / 0.28);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-name {
  font-size: var(--fs-15);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.015em;
}

.brand-sub {
  font-size: var(--fs-11);
  color: var(--text-faint);
  letter-spacing: 0.02em;
}

/* 移动端才显示的当前页标题 */
.topbar-title {
  display: none;
  font-size: var(--fs-15);
  font-weight: 600;
  color: var(--text-strong);
  letter-spacing: -0.01em;
  margin-left: var(--sp-1);
}

/* ---------- 分段导航（≥640px） ---------- */
.segmented {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border-radius: var(--r-lg);
  background: var(--n-100);
  border: 1px solid var(--border-subtle);
}

.seg-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--r-md);
  font-size: var(--fs-13);
  font-weight: 500;
  color: var(--text-muted);
  white-space: nowrap;
  transition: color var(--dur-fast) var(--ease-in-out),
    background-color var(--dur-fast) var(--ease-in-out);
}

.seg-item:hover {
  color: var(--text-strong);
}

.seg-item.active {
  background: var(--n-0);
  color: var(--text-strong);
  font-weight: 600;
  box-shadow: var(--sh-sm);
}

/* ---------- 右侧元信息（≥1024px） ---------- */
.topbar-meta {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 0 0 auto;
  padding-left: var(--sp-4);
  border-left: 1px solid var(--border);
}

.meta-date {
  font-size: var(--fs-12);
  color: var(--text-faint);
}

.meta-clock {
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--text);
  letter-spacing: 0.02em;
}

/* ============================================================
   内容容器：所有页面共用
   ============================================================ */
.main {
  flex: 1;
  min-height: 0;
}

.container {
  width: 100%;
  max-width: var(--shell-max);
  margin: 0 auto;
  padding: var(--sp-5) var(--gutter) var(--sp-7);
}

/* ============================================================
   移动端底部标签栏
   ============================================================ */
.tabbar {
  display: none;
}

/* ============================================================
   响应式
   ============================================================ */

/* 平板：收起时钟 */
@media (max-width: 1023px) {
  .topbar-meta {
    display: none;
  }
}

/* 移动端：换成底部标签栏 */
@media (max-width: 639px) {
  .topbar {
    gap: var(--sp-3);
    padding: 0 var(--gutter);
  }

  .brand-sub,
  .segmented {
    display: none;
  }

  .topbar-title {
    display: block;
    margin-left: auto;
  }

  .container {
    padding: var(--sp-4) var(--gutter)
      calc(var(--tabbar-h) + var(--sp-6) + env(safe-area-inset-bottom));
  }

  .tabbar {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: var(--z-nav);
    height: calc(var(--tabbar-h) + env(safe-area-inset-bottom));
    padding-bottom: env(safe-area-inset-bottom);
    background: rgb(255 255 255 / 0.94);
    backdrop-filter: saturate(180%) blur(14px);
    border-top: 1px solid var(--border);
  }

  .tab {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 3px;
    color: var(--text-faint);
    transition: color var(--dur-fast) var(--ease-in-out);
    -webkit-tap-highlight-color: transparent;
  }

  .tab.active {
    color: var(--brand);
  }

  .tab-label {
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.02em;
  }
}
</style>
