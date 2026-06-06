<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../../api'
import { toastError } from '../../api/http'
import {
  statusLabel, fenToYuan, formatTime, formatDistance,
  eventLabel, eventDetail, eventIcon, operatorLabel,
} from '../../utils/format'
import { useResponsiveValue } from '../../composables/useViewport'
import AppIcon from '../../components/AppIcon.vue'
import MiniMap from '../../components/MiniMap.vue'
import StatusPill from '../../components/StatusPill.vue'
import StatusSteps from '../../components/StatusSteps.vue'
import EmptyState from '../../components/EmptyState.vue'

/**
 * 订单追踪。
 *
 * 响应式：
 *   ≥1024px  左（状态总览 + 大地图 + 骑手卡） + 右（配送信息 + 订单动态）
 *   <1024px  单列
 *
 * WebSocket 逻辑沿用原实现：状态推送拉全量、位置推送走增量、重连后拉全量兜底。
 */
const route = useRoute()
const router = useRouter()
const orderNo = route.params.orderNo

const loading = ref(true)
const order = ref(null)
const track = ref(null)
const events = ref([])
const trail = ref([])

/** 地图高度按断点切换（MiniMap 用 JS 量宽度，高度只能由 JS 给） */
const mapHeight = useResponsiveValue({ mobile: 210, tablet: 280, desktop: 340 })

/** 骑手当前位置（用于地图） */
const riderPoint = computed(() => {
  if (!track.value?.lng || !track.value?.lat) return null
  return { lng: Number(track.value.lng), lat: Number(track.value.lat) }
})

const pickupPoint = computed(() =>
  order.value ? { lng: Number(order.value.pickupLng), lat: Number(order.value.pickupLat) } : null,
)
const deliverPoint = computed(() =>
  order.value ? { lng: Number(order.value.deliverLng), lat: Number(order.value.deliverLat) } : null,
)

const canCancel = computed(() =>
  ['PENDING_DISPATCH', 'DISPATCHING', 'PENDING_PICKUP'].includes(order.value?.status),
)

/** 事件流水按时间倒序，最新的在最上面 */
const timeline = computed(() => [...events.value].reverse())

async function loadAll() {
  try {
    const [o, t, ev] = await Promise.all([
      userApi.getOrder(orderNo),
      userApi.track(orderNo),
      userApi.events(orderNo),
    ])
    order.value = o
    track.value = t
    events.value = ev || []

    // 用后端返回的历史轨迹初始化路线（进页面就能看到骑手走过的路径）
    if (t.trail && t.trail.length > 0) {
      trail.value = t.trail.map((p) => ({ lng: p.lng, lat: p.lat }))
    } else if (t.lng && t.lat && trail.value.length === 0) {
      trail.value = [{ lng: Number(t.lng), lat: Number(t.lat) }]
    }
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

async function cancelOrder() {
  try {
    await ElMessageBox.confirm(
      `取消后不可恢复，确认取消订单 ${orderNo}？`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  try {
    await userApi.cancelOrder(orderNo, '用户取消')
    ElMessage.success('订单已取消')
    loadAll()
  } catch (e) {
    toastError(e)
  }
}

onMounted(() => {
  loadAll()
})

onUnmounted(() => {
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head-main">
        <span class="eyebrow">实时追踪</span>
        <h1 class="page-title">订单追踪</h1>
        <p class="page-desc num">{{ orderNo }}</p>
      </div>
      <div class="page-head-actions">
        <router-link to="/user/orders" class="ghost-btn">
          <AppIcon name="list" :size="14" />
          我的订单
        </router-link>
      </div>
    </header>

    <div v-if="loading" class="stack-5">
      <div class="skeleton" style="height: 110px; border-radius: 12px"></div>
      <div class="skeleton" style="height: 320px; border-radius: 12px"></div>
      <div class="skeleton" style="height: 140px; border-radius: 12px"></div>
    </div>

    <div v-else-if="order" class="split">
      <!-- ==================== 主列 ==================== -->
      <div class="stack-5">
        <!-- 状态总览 -->
        <section class="card overview">
          <div class="overview-top">
            <div class="overview-status">
              <StatusPill :status="order.status" size="md" />
              <span v-if="order.needManual" class="manual-chip">
                <AppIcon name="alert" :size="11" :stroke-width="2.2" />
                已转人工处理
              </span>
            </div>
            <span class="overview-amount num">¥{{ fenToYuan(order.amountFen) }}</span>
          </div>
          <StatusSteps :status="order.status" />
        </section>

        <!-- 态势地图 -->
        <section>
          <MiniMap
            :pickup="pickupPoint"
            :deliver="deliverPoint"
            :rider="riderPoint"
            :track="trail"
            :status="order.status"
            :height="mapHeight"
          />
        </section>

        <!-- 骑手 -->
        <section>
          <div v-if="track?.riderAssigned" class="rider-card">
            <span class="rider-avatar">{{ (track.riderName || '骑').slice(0, 1) }}</span>
            <div class="rider-info">
              <div class="rider-name">
                {{ track.riderName || '骑手' }}
                <span class="rider-no num">{{ track.riderNo }}</span>
              </div>
              <div class="rider-phone">
                <AppIcon name="phone" :size="12" />
                {{ track.riderPhone || '—' }}
              </div>
            </div>
            <span class="rider-badge">
              <span class="live-dot"></span>
              配送中
            </span>
          </div>

          <div v-else class="waiting-card">
            <span class="waiting-icon">
              <AppIcon name="target" :size="18" :stroke-width="1.8" />
            </span>
            <div>
              <div class="waiting-title">等待骑手接单</div>
              <p class="waiting-desc">
                正在为你匹配附近的骑手，请稍候。
              </p>
            </div>
          </div>
        </section>
      </div>

      <!-- ==================== 侧列 ==================== -->
      <div class="stack-5">
        <!-- 配送信息 -->
        <section class="card">
          <div class="section-title">配送信息</div>
          <dl class="info-list">
            <div class="info-row">
              <dt>取件</dt>
              <dd>{{ order.pickupAddress }}</dd>
            </div>
            <div class="info-row">
              <dt>送达</dt>
              <dd>{{ order.deliverAddress }}</dd>
            </div>
            <div class="info-row">
              <dt>联系人</dt>
              <dd>{{ order.pickupContact }} → {{ order.deliverContact }}</dd>
            </div>
            <div class="info-row">
              <dt>距离</dt>
              <dd class="num">{{ formatDistance(order.distanceM) }}</dd>
            </div>
            <div class="info-row">
              <dt>物品</dt>
              <dd>{{ order.goodsDesc || '—' }}</dd>
            </div>
            <div class="info-row">
              <dt>承诺送达</dt>
              <dd class="num">{{ formatTime(order.expectDeliverAt) }}</dd>
            </div>
            <div class="info-row">
              <dt>下单时间</dt>
              <dd class="num">{{ formatTime(order.createdAt) }}</dd>
            </div>
          </dl>
        </section>

        <!-- 订单动态 -->
        <section class="card">
          <div class="section-title">
            订单动态
            <span class="section-tail">{{ timeline.length }} 条</span>
          </div>

          <EmptyState
            v-if="timeline.length === 0"
            compact
            icon="history"
            title="暂无动态"
            description="订单状态变化后会在这里留下记录。"
          />

          <ol v-else class="tl">
            <li v-for="(e, i) in timeline" :key="e.id" class="tl-item" :class="{ head: i === 0 }">
              <span class="tl-node">
                <AppIcon :name="eventIcon(e.eventType)" :size="12" :stroke-width="2.1" />
              </span>
              <div class="tl-body">
                <div class="tl-top">
                  <span class="tl-label">{{ eventLabel(e.eventType) }}</span>
                  <span class="tl-op">{{ operatorLabel(e.operatorType) }}</span>
                </div>
                <p v-if="eventDetail(e.eventType, e.remark)" class="tl-remark">
                  {{ eventDetail(e.eventType, e.remark) }}
                </p>
              </div>
              <time class="tl-time num">{{ formatTime(e.createdAt) }}</time>
            </li>
          </ol>
        </section>

        <button v-if="canCancel" class="danger-btn" @click="cancelOrder">
          <AppIcon name="close" :size="14" :stroke-width="2.3" />
          取消订单
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
}

/* ==================== WS 状态 ==================== */
.ws-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 12px;
  border-radius: var(--r-md);
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--text-muted);
  background: var(--n-100);
  border: 1px solid var(--border-subtle);
  white-space: nowrap;
}

.ws-chip[data-state='connected'],
.ws-chip[data-state='reconnected'] {
  color: var(--ok);
  background: var(--ok-bg);
  border-color: #abefc6;
}

.ws-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.ws-chip[data-state='connected'] .ws-dot {
  animation: pulse-dot 1.8s var(--ease-in-out) infinite;
}

/* ==================== 状态总览 ==================== */
.overview-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-5);
  flex-wrap: wrap;
}

.overview-status {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.manual-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  padding: 0 9px;
  border-radius: var(--r-full);
  font-size: var(--fs-11);
  font-weight: 600;
  color: var(--warn);
  background: var(--warn-bg);
  border: 1px solid #fedf89;
}

.overview-amount {
  font-size: var(--fs-22);
  font-weight: 700;
  color: var(--brand-600);
  letter-spacing: -0.025em;
}

/* ==================== 骑手卡 ==================== */
.rider-card {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-4);
  border-radius: var(--r-lg);
  background: linear-gradient(150deg, var(--brand-50), var(--n-0) 70%);
  border: 1px solid var(--brand-100);
}

.rider-avatar {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  border-radius: var(--r-full);
  display: grid;
  place-items: center;
  font-size: var(--fs-16);
  font-weight: 700;
  color: #fff;
  background: linear-gradient(150deg, var(--brand-400), var(--brand-600));
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.3);
}

.rider-info {
  flex: 1;
  min-width: 0;
}

.rider-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-14);
  font-weight: 700;
  color: var(--text-strong);
  margin-bottom: 3px;
}

.rider-no {
  font-size: var(--fs-11);
  font-weight: 500;
  color: var(--text-faint);
}

.rider-phone {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-12);
  color: var(--text-muted);
}

.rider-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: var(--r-full);
  font-size: var(--fs-11);
  font-weight: 600;
  color: var(--ok);
  background: var(--ok-bg);
  border: 1px solid #abefc6;
  flex: 0 0 auto;
}

/* ==================== 等待骑手 ==================== */
.waiting-card {
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--warn-bg);
  border: 1px solid #fedf89;
}

.waiting-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: var(--r-md);
  display: grid;
  place-items: center;
  background: rgb(255 255 255 / 0.7);
  color: var(--warn);
  border: 1px solid #fedf89;
}

.waiting-title {
  font-size: var(--fs-13);
  font-weight: 700;
  color: var(--warn);
  margin-bottom: 3px;
}

.waiting-desc {
  font-size: var(--fs-12);
  line-height: 1.6;
  color: #93370d;
}

/* ==================== 事件流水 ==================== */
.tl {
  position: relative;
  display: flex;
  flex-direction: column;
}

.tl-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: var(--sp-3);
  padding-bottom: var(--sp-4);
}

.tl-item:last-child {
  padding-bottom: 0;
}

.tl-item:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 11px;
  top: 26px;
  bottom: 2px;
  width: 1.5px;
  background: var(--border);
}

.tl-node {
  width: 23px;
  height: 23px;
  flex: 0 0 23px;
  border-radius: var(--r-full);
  display: grid;
  place-items: center;
  background: var(--n-100);
  color: var(--text-faint);
  border: 1px solid var(--border);
  position: relative;
  z-index: 1;
}

.tl-item.head .tl-node {
  background: var(--brand-50);
  color: var(--brand-600);
  border-color: var(--brand-200);
}

.tl-body {
  flex: 1;
  min-width: 0;
  padding-top: 1px;
}

.tl-top {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tl-label {
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--text-strong);
}

.tl-op {
  font-size: var(--fs-11);
  font-weight: 500;
  color: var(--text-muted);
  padding: 1px 7px;
  border-radius: var(--r-full);
  background: var(--n-100);
  border: 1px solid var(--border-subtle);
}

.tl-remark {
  font-size: var(--fs-12);
  line-height: 1.55;
  color: var(--text-muted);
  margin-top: 3px;
}

.tl-time {
  flex: 0 0 auto;
  font-size: var(--fs-10);
  color: var(--text-faint);
  padding-top: 4px;
  text-align: right;
}

/* ==================== 按钮 ==================== */
.danger-btn {
  width: 100%;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: var(--r-md);
  font-size: var(--fs-14);
  font-weight: 600;
  color: var(--danger);
  background: var(--bg-surface);
  border: 1px solid #fecdca;
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.danger-btn:hover {
  background: var(--danger-bg);
  border-color: #fda29b;
}

.danger-btn:active {
  transform: scale(0.985);
}

.ghost-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 14px;
  border-radius: var(--r-md);
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--text);
  background: var(--bg-surface);
  border: 1px solid var(--border-strong);
  transition: all var(--dur-fast) var(--ease-out-quart);
  white-space: nowrap;
}

.ghost-btn:hover {
  color: var(--text-strong);
  background: var(--n-50);
}
</style>
