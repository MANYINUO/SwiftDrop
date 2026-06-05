<script setup>
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'

const router = useRouter()

/** 服务流程：从下单到送达，用户经历的四个环节 */
const FLOW = [
  {
    icon: 'plus',
    title: '在线下单',
    desc: '填写取件和送达地址、物品信息，一键呼叫附近骑手。',
  },
  {
    icon: 'target',
    title: '骑手上门',
    desc: '系统为你匹配就近的骑手，尽快上门取件。',
  },
  {
    icon: 'scooter',
    title: '实时追踪',
    desc: '骑手取件后立即配送，地图上可实时查看位置与进度。',
  },
  {
    icon: 'check',
    title: '准时送达',
    desc: '送达后即时通知，全程状态一目了然、安心可查。',
  },
]

// F1 阶段：只有用户端。后面 F2/F6 加上骑手端和后台后，再把卡片加回来。
const ROLES = [
  {
    key: 'user',
    to: '/user/create',
    icon: 'user',
    title: '用户端',
    tagline: '下单 · 追踪',
    desc: '在线下单呼叫骑手，实时查看骑手位置与订单进度。',
    points: ['一键下单，快速安排骑手上门', '地图实时查看骑手位置', '订单状态与配送动态随时掌握'],
  },
]
</script>

<template>
  <div class="home">
    <!-- ==================== Hero ==================== -->
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">同城即时配送 · 专人即取即送</span>
        <h1>
          <!-- 断行用显式 block 行控制：text-wrap: balance 在不同宽度下
               会给出"…算 / 出…"这种把词切开的断法，不可控 -->
          <span class="h1-line">一键下单，<span class="hl">附近骑手上门取件</span></span>
          <span class="h1-line">同城急件即取即送</span>
        </h1>
        <p class="hero-lead">
          文件、鲜花、钥匙、生鲜等物品专人即取即送。在线下单后为你匹配就近骑手上门取件，
          全程位置实时可见，配送进度一目了然。
        </p>

        <div class="hero-cta">
          <router-link to="/user/create" class="btn btn-primary">
            <AppIcon name="plus" :size="16" :stroke-width="2.2" />
            立即下单
          </router-link>
          <router-link to="/user/orders" class="btn btn-ghost">
            <AppIcon name="list" :size="16" />
            我的订单
          </router-link>
        </div>
      </div>

      <!-- 全链路流水线：与 StatusSteps 同一套视觉语言 -->
      <ol class="flow">
        <li v-for="(f, i) in FLOW" :key="f.title" class="flow-item" :style="{ '--i': i }">
          <span class="flow-node">
            <AppIcon :name="f.icon" :size="15" :stroke-width="2" />
          </span>
          <div class="flow-body">
            <div class="flow-title">{{ f.title }}</div>
            <div class="flow-desc">{{ f.desc }}</div>
          </div>
        </li>
      </ol>
    </section>

    <!-- ==================== 角色入口 ==================== -->
    <section class="roles">
      <div class="roles-head">
        <h2>用户端</h2>
        <p class="muted">在线下单，实时追踪订单状态与配送进度。</p>
      </div>

      <div class="role-grid">
        <router-link
          v-for="(r, i) in ROLES"
          :key="r.key"
          :to="r.to"
          class="role-card"
          :style="{ '--i': i }"
        >
          <div class="role-top">
            <span class="role-icon">
              <AppIcon :name="r.icon" :size="20" :stroke-width="1.9" />
            </span>
            <span class="role-tagline">{{ r.tagline }}</span>
          </div>

          <h3 class="role-title">{{ r.title }}</h3>
          <p class="role-desc">{{ r.desc }}</p>

          <ul class="role-points">
            <li v-for="p in r.points" :key="p">
              <AppIcon name="check" :size="13" :stroke-width="2.4" />
              <span>{{ p }}</span>
            </li>
          </ul>

          <span class="role-enter">
            进入{{ r.title }}
            <AppIcon name="arrow-right" :size="15" :stroke-width="2.2" />
          </span>
        </router-link>
      </div>
    </section>

  </div>
</template>

<style scoped>
.home {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: var(--sp-8);
}

/* ============================================================
   Hero：非对称左右分栏，左侧主张、右侧链路
   ============================================================ */
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: var(--sp-8);
  align-items: start;
  padding-top: var(--sp-4);
}

.hero-copy h1 {
  font-size: clamp(26px, 3.4vw, 42px);
  line-height: 1.16;
  font-weight: 700;
  letter-spacing: -0.035em;
  color: var(--text-strong);
  margin: var(--sp-3) 0 var(--sp-4);
}

.h1-line {
  display: block;
}

/* 荧光笔划重点的效果：靠背景渐变画一条下半段的色带。
   不用伪元素 + z-index 是因为 inline 元素上加负 z-index 会跑到父级背景后面去。 */
.hl {
  color: var(--brand-600);
  white-space: nowrap;
  background-image: linear-gradient(
    180deg,
    transparent 56%,
    var(--brand-100) 56%,
    var(--brand-100) 93%,
    transparent 93%
  );
  box-decoration-break: clone;
  -webkit-box-decoration-break: clone;
  padding: 0 3px;
  margin: 0 -3px;
  border-radius: 4px;
}

.hero-lead {
  font-size: var(--fs-15);
  line-height: 1.75;
  color: var(--text-muted);
  max-width: 46ch;
}

.hero-cta {
  display: flex;
  gap: var(--sp-3);
  margin-top: var(--sp-6);
  flex-wrap: wrap;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 40px;
  padding: 0 18px;
  border-radius: var(--r-md);
  font-size: var(--fs-14);
  font-weight: 600;
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.btn:active {
  transform: scale(0.975);
}

.btn-primary {
  background: var(--brand);
  color: #fff;
  box-shadow: 0 1px 2px rgb(242 96 12 / 0.24);
}

.btn-primary:hover {
  background: var(--brand-hover);
  box-shadow: var(--sh-brand);
}

.btn-ghost {
  background: var(--bg-surface);
  color: var(--text);
  border: 1px solid var(--border-strong);
}

.btn-ghost:hover {
  background: var(--n-50);
  color: var(--text-strong);
  border-color: var(--n-400);
}

.stack-strip {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: var(--sp-7);
  padding-top: var(--sp-4);
  border-top: 1px solid var(--border-subtle);
}

.stack-label {
  font-size: var(--fs-11);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-faint);
  margin-right: 4px;
}

.stack-chip {
  font-size: var(--fs-11);
  font-weight: 500;
  color: var(--text-muted);
  padding: 3px 9px;
  border-radius: var(--r-full);
  background: var(--n-100);
  border: 1px solid var(--border-subtle);
}

/* ---------- 链路 ---------- */
.flow {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-5);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--r-xl);
  box-shadow: var(--sh-sm);
  position: relative;
  overflow: hidden;
}

/* 顶部一条品牌色渐变细线，暗示"管道" */
.flow::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 2px;
  background: linear-gradient(90deg, var(--brand), var(--brand-200) 60%, transparent);
}

.flow-item {
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-3) 0;
  position: relative;
}

/* 入场淡入为渐进增强：默认可见，仅在动画时间轴正常推进时错位淡入 */
@media (prefers-reduced-motion: no-preference) {
  html.motion .flow-item {
    animation: rise-in var(--dur-slow) var(--ease-out-quart) both;
    animation-delay: calc(var(--i) * 70ms);
  }
}

/* 节点之间的竖向连线 */
.flow-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 15px;
  top: 40px;
  bottom: -2px;
  width: 1.5px;
  background: linear-gradient(180deg, var(--n-200), var(--n-100));
}

.flow-node {
  width: 31px;
  height: 31px;
  flex: 0 0 31px;
  border-radius: var(--r-md);
  display: grid;
  place-items: center;
  background: var(--brand-50);
  color: var(--brand-600);
  border: 1px solid var(--brand-100);
  position: relative;
  z-index: 1;
}

.flow-body {
  min-width: 0;
  padding-top: 1px;
}

.flow-title {
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--text-strong);
  margin-bottom: 2px;
}

.flow-desc {
  font-size: var(--fs-12);
  line-height: 1.6;
  color: var(--text-muted);
}

/* ============================================================
   角色入口
   ============================================================ */
.roles-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-4);
  margin-bottom: var(--sp-4);
  flex-wrap: wrap;
}

.roles-head h2 {
  font-size: var(--fs-18);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.02em;
}

.role-grid {
  display: grid;
  /* 刻意不等宽：第一张卡更大，打破"三列均分"的模板感 */
  grid-template-columns: 1.25fr 1fr 1fr;
  gap: var(--sp-4);
}

.role-card {
  display: flex;
  flex-direction: column;
  padding: var(--sp-5);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--r-xl);
  transition: border-color var(--dur) var(--ease-out-quart),
    box-shadow var(--dur) var(--ease-out-quart),
    transform var(--dur) var(--ease-out-quart);
}

/* 入场淡入为渐进增强：默认可见，仅在动画时间轴正常推进时错位淡入 */
@media (prefers-reduced-motion: no-preference) {
  html.motion .role-card {
    animation: rise-in var(--dur-slow) var(--ease-out-quart) both;
    animation-delay: calc(var(--i) * 80ms);
  }
}

.role-card:hover {
  border-color: var(--brand-200);
  box-shadow: 0 10px 30px -12px rgb(242 96 12 / 0.22);
  transform: translateY(-3px);
}

.role-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.role-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--r-lg);
  display: grid;
  place-items: center;
  background: linear-gradient(150deg, var(--brand-50), var(--brand-100));
  color: var(--brand-600);
  border: 1px solid var(--brand-100);
}

.role-tagline {
  font-size: var(--fs-11);
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--text-faint);
  padding: 3px 9px;
  border-radius: var(--r-full);
  background: var(--n-50);
  border: 1px solid var(--border-subtle);
}

.role-title {
  font-size: var(--fs-16);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.015em;
  margin-bottom: 6px;
}

.role-desc {
  font-size: var(--fs-13);
  line-height: 1.65;
  color: var(--text-muted);
  margin-bottom: var(--sp-4);
}

.role-points {
  display: flex;
  flex-direction: column;
  gap: 7px;
  margin-bottom: var(--sp-5);
}

.role-points li {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  font-size: var(--fs-12);
  line-height: 1.5;
  color: var(--text);
}

.role-points li :deep(svg) {
  color: var(--brand);
  margin-top: 2px;
  flex: 0 0 auto;
}

.role-enter {
  margin-top: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--brand-600);
}

.role-card:hover .role-enter :deep(svg) {
  transform: translateX(3px);
}

.role-enter :deep(svg) {
  transition: transform var(--dur) var(--ease-out-quart);
}

/* ============================================================
   响应式（断点与 styles/layout.css 一致）
   ============================================================ */
@media (max-width: 1023px) {
  .hero {
    grid-template-columns: 1fr;
    gap: var(--sp-6);
  }

  .role-grid {
    grid-template-columns: 1fr 1fr;
  }

  .role-card:first-child {
    grid-column: 1 / -1;
  }
}

@media (max-width: 767px) {
  .home {
    gap: var(--sp-7);
  }

  .role-grid {
    grid-template-columns: 1fr;
  }

  .role-card:first-child {
    grid-column: auto;
  }

  .hero-cta .btn {
    flex: 1;
    justify-content: center;
  }
}
</style>
