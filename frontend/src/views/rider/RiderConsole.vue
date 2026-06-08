<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { riderApi } from '../../api'
import { toastError } from '../../api/http'
import { createWsClient } from '../../utils/ws'
import { interpolate, distanceMeters } from '../../utils/geo'
import { fenToYuan, formatTime, formatDistance } from '../../utils/format'
import { useResponsiveValue } from '../../composables/useViewport'
import AppIcon from '../../components/AppIcon.vue'
import MiniMap from '../../components/MiniMap.vue'
import StatusPill from '../../components/StatusPill.vue'
import RouteLine from '../../components/RouteLine.vue'
import EmptyState from '../../components/EmptyState.vue'

/**
 * 骑手工作台。
 *
 * 响应式：
 *   ≥1024px  左（身份卡 + 身份切换 + 位置与态势） + 右（待接单 + 进行中订单）
 *   <1024px  单列
 *
 * 上下线 / 位置上报 / 模拟移动 / 接单拒单 / 取货送达 与 WebSocket 逻辑全部沿用原实现。
 */
const route = useRoute()
const router = useRouter()

// 骑手身份来自登录态，路由守卫已保证这里只能是本人编号
const riderId = ref(Number(route.params.riderId) || 0)

const rider = ref(null)
const tasks = ref([])
const currentOrders = ref([])
const loading = ref(false)

const mapHeight = useResponsiveValue({ mobile: 200, tablet: 250, desktop: 300 })

/** 骑手本地位置（前端模拟移动用） */
const pos = reactive({ lng: 116.404269, lat: 39.913164 })

/** 模拟移动 */
const sim = reactive({ running: false, timer: null, tick: 0 })

/** 本地秒表：让派单倒计时真的在走，而不是等下次刷新才跳 */
const taskClock = ref(Date.now())
let clockTimer = null
let taskLoadedAt = 0

/** WebSocket 客户端 */
let wsClient = null

const isOnline = computed(() => !!rider.value?.online)

const activeOrder = computed(() => currentOrders.value[0] || null)

const pickupPoint = computed(() =>
  activeOrder.value
    ? { lng: Number(activeOrder.value.pickupLng), lat: Number(activeOrder.value.pickupLat) }
    : null,
)
const deliverPoint = computed(() =>
  activeOrder.value
    ? { lng: Number(activeOrder.value.deliverLng), lat: Number(activeOrder.value.deliverLat) }
    : null,
)

const loadPct = computed(() => {
  const cap = rider.value?.maxCapacity || 1
  return Math.min(100, Math.round(((rider.value?.deliveringCount ?? 0) / cap) * 100))
})

const mapStatus = computed(() => activeOrder.value?.status || '')

/** 派单剩余秒数（本地递减） */
function remaining(t) {
  const elapsed = Math.floor((taskClock.value - taskLoadedAt) / 1000)
  return Math.max(0, (t.remainingSeconds ?? 0) - elapsed)
}

/* ==================== 数据加载 ==================== */

async function loadRider() {
  try {
    rider.value = await riderApi.detail(riderId.value)
    if (rider.value?.lng) {
      pos.lng = Number(rider.value.lng)
      pos.lat = Number(rider.value.lat)
    }
  } catch (e) {
    toastError(e)
  }
}

async function loadTasks() {
  try {
    tasks.value = await riderApi.pendingTasks(riderId.value)
    taskLoadedAt = Date.now()
  } catch {
    tasks.value = []
  }
}

async function loadCurrentOrders() {
  try {
    currentOrders.value = await riderApi.currentOrders(riderId.value)
  } catch {
    currentOrders.value = []
  }
}

async function refresh() {
  loading.value = true
  await Promise.all([loadRider(), loadTasks(), loadCurrentOrders()])
  loading.value = false
}

/* ==================== 上下线 ==================== */

async function toggleOnline() {
  try {
    if (isOnline.value) {
      await riderApi.offline(riderId.value)
      ElMessage.info('已下线')
      stopSim()
    } else {
      await riderApi.online(riderId.value, pos.lng, pos.lat)
      ElMessage.success('已上线，可以接单了')
    }
    await loadRider()
  } catch (e) {
    toastError(e)
  }
}

/* ==================== 位置上报 ==================== */

async function reportOnce() {
  try {
    await riderApi.reportLocation(riderId.value, {
      lng: pos.lng,
      lat: pos.lat,
      orderNo: activeOrder.value?.orderNo,
    })
    await loadRider()
    ElMessage.success('位置已更新')
  } catch (e) {
    toastError(e)
  }
}

/**
 * 模拟移动：朝当前目标点前进一小步并上报位置。
 * 目标 = 待取货时朝取件点，配送中时朝送达点，空闲时原地小幅游走。
 */
async function simTick() {
  const o = activeOrder.value
  let target = null

  if (o?.status === 'PENDING_PICKUP') {
    target = { lng: Number(o.pickupLng), lat: Number(o.pickupLat) }
  } else if (o?.status === 'DELIVERING') {
    target = { lng: Number(o.deliverLng), lat: Number(o.deliverLat) }
  }

  if (target) {
    const step = 0.18
    const next = interpolate(pos.lng, pos.lat, target.lng, target.lat, step)
    pos.lng = next.lng
    pos.lat = next.lat
  } else {
    // 空闲时小幅游走，看起来像在移动
    pos.lng += (Math.random() - 0.5) * 0.0008
    pos.lat += (Math.random() - 0.5) * 0.0008
  }

  sim.tick += 1
  try {
    await riderApi.reportLocation(riderId.value, {
      lng: pos.lng,
      lat: pos.lat,
      orderNo: activeOrder.value?.orderNo,
    })
  } catch {
    /* 防抖丢弃是正常的，不提示 */
  }
}

function startSim() {
  if (!isOnline.value) {
    ElMessage.warning('请先上线')
    return
  }
  if (sim.running) return
  sim.running = true
  sim.timer = setInterval(simTick, 1500)
  ElMessage.success('已开始配送，位置实时更新中')
}

function stopSim() {
  sim.running = false
  clearInterval(sim.timer)
  sim.timer = null
}

/* ==================== 接单 ==================== */

async function accept(order) {
  try {
    await riderApi.accept(riderId.value, order.orderNo)
    ElMessage.success('接单成功')
    await refresh()
  } catch (e) {
    // 40006 = 已被别人接走，这是并发场景的正常结果
    if (e.code === 40006) {
      ElMessage.warning('手慢了，这单已被其他骑手接走')
    } else {
      toastError(e)
    }
    await refresh()
  }
}

/* ==================== 取货 / 送达 ==================== */

async function pickup(order) {
  try {
    await riderApi.pickup(riderId.value, order.orderNo)
    ElMessage.success('已取货，开始配送')
    await refresh()
  } catch (e) {
    toastError(e)
  }
}

async function delivered(order) {
  try {
    await riderApi.delivered(riderId.value, order.orderNo)
    ElMessage.success('已送达，订单完成')
    stopSim()
    await refresh()
  } catch (e) {
    toastError(e)
  }
}

function distTo(target) {
  if (!target) return null
  return distanceMeters(pos.lng, pos.lat, Number(target.lng), Number(target.lat))
}

/* ==================== 生命周期 ==================== */

/** F2 简化版：没有登录页，切换账号改成回首页 */
function switchAccount() {
  stopSim()
  ElMessage.info('已退出当前骑手账号')
  router.push('/')
}

onMounted(() => {
  refresh()
  clockTimer = setInterval(() => (taskClock.value = Date.now()), 1000)
  // 连接 WebSocket，实时接收新派单通知
  wsClient = createWsClient(`/ws/rider/${riderId.value}`, {
    onMessage(msg) {
      if (msg.type === 'DISPATCH_TASK') {
        ElMessage.info('收到新派单，请尽快接单')
        loadTasks()
      }
    },
    onReconnect() {
      // 重连后拉一次待接单，补齐断线期间漏掉的派单
      loadTasks()
    },
  })
})

onUnmounted(() => {
  stopSim()
  clearInterval(clockTimer)
  wsClient?.close()
})
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head-main">
        <span class="eyebrow">骑手端</span>
        <h1 class="page-title">骑手工作台</h1>
        <p class="page-desc">
          {{ rider?.name || '加载中' }}
          <span v-if="rider?.riderNo" class="num"> · {{ rider.riderNo }}</span>
        </p>
      </div>
      <div class="page-head-actions">
        <span class="online-chip" :data-online="isOnline">
          <span class="online-dot"></span>
          {{ isOnline ? '在线' : '离线' }}
        </span>
      </div>
    </header>

    <div class="split">
      <!-- ==================== 主列 ==================== -->
      <div class="stack-5">
        <!-- 身份卡 -->
        <section class="id-card" :data-online="isOnline">
          <div class="id-row">
            <span class="id-avatar">{{ (rider?.name || '骑').slice(0, 1) }}</span>
            <div class="id-info">
              <div class="id-name">{{ rider?.name || '—' }}</div>
              <div class="id-meta">
                <span>评分 <b class="num">{{ rider?.score ?? '—' }}</b></span>
                <span class="id-sep"></span>
                <span>在手 <b class="num">{{ rider?.deliveringCount ?? 0 }}/{{ rider?.maxCapacity ?? '—' }}</b></span>
              </div>
            </div>
            <button class="power-btn" :data-online="isOnline" @click="toggleOnline">
              <AppIcon name="power" :size="14" :stroke-width="2.2" />
              {{ isOnline ? '下线' : '上线' }}
            </button>
          </div>

          <div class="load">
            <div class="load-bar">
              <span class="load-fill" :style="{ width: loadPct + '%' }" :data-full="loadPct >= 100"></span>
            </div>
            <span class="load-text">负载 {{ loadPct }}%</span>
          </div>
        </section>

        <!-- 当前账号 / 更换账号 -->
        <div class="rider-switch">
          <span class="switch-label">当前账号</span>
          <div class="switch-group account">
            <span class="account-name">
              {{ rider?.name || '—' }}
              <i class="account-no num">{{ rider?.riderNo || '' }}</i>
            </span>
            <button type="button" class="switch-account-btn" @click="switchAccount">
              更换账号
            </button>
          </div>
        </div>

        <!-- 位置与态势 -->
        <section class="card">
          <div class="section-title">
            实时位置
          </div>

          <MiniMap
            :pickup="pickupPoint"
            :deliver="deliverPoint"
            :rider="{ lng: pos.lng, lat: pos.lat }"
            :status="mapStatus"
            :height="mapHeight"
          />

          <div class="sim-row">
            <button class="act-btn" :disabled="!isOnline" @click="reportOnce">
              <AppIcon name="send" :size="14" :stroke-width="2" />
              更新位置
            </button>

            <button v-if="!sim.running" class="act-btn primary" :disabled="!isOnline" @click="startSim">
              <AppIcon name="play" :size="13" />
              开始配送
            </button>
            <button v-else class="act-btn danger" @click="stopSim">
              <AppIcon name="pause" :size="14" :stroke-width="2.4" />
              结束行程
            </button>
          </div>

          <p v-if="sim.running" class="sim-note">
            <span class="live-dot"></span>
            配送进行中 · 位置实时更新
          </p>
          <p v-else-if="!isOnline" class="sim-note muted">上线后才能开始配送和更新位置</p>
        </section>
      </div>

      <!-- ==================== 侧列 ==================== -->
      <div class="stack-5">
        <!-- 待接单 -->
        <section>
          <div class="section-title">
            待接单
            <span v-if="tasks.length" class="count-badge">{{ tasks.length }}</span>
          </div>

          <EmptyState
            v-if="tasks.length === 0"
            compact
            icon="send"
            title="暂无派单"
            :description="isOnline ? '有订单派给你时会实时推送到这里。' : '先上线，才会收到派单。'"
          />

          <div v-else class="stack">
            <article v-for="t in tasks" :key="t.orderNo" class="task-card">
              <div class="task-top">
                <span class="task-no num">{{ t.orderNo }}</span>
                <StatusPill :status="t.status" size="sm" :with-icon="false" />
              </div>

              <RouteLine :pickup="t.pickupAddress" :deliver="t.deliverAddress" compact />

              <div class="task-meta">
                <span class="meta-item">
                  <AppIcon name="route" :size="12" />
                  {{ formatDistance(t.distanceM) }}
                </span>
                <span class="amount">¥{{ fenToYuan(t.amountFen) }}</span>
              </div>

              <div class="task-actions">
                <button class="act-btn primary wide" @click="accept(t)">
                  <AppIcon name="check" :size="14" :stroke-width="2.5" />
                  接单
                </button>
              </div>
            </article>
          </div>
        </section>

        <!-- 进行中订单 -->
        <section>
          <div class="section-title">
            进行中订单
            <span v-if="currentOrders.length" class="count-badge">{{ currentOrders.length }}</span>
          </div>

          <EmptyState
            v-if="currentOrders.length === 0"
            compact
            icon="scooter"
            title="暂无进行中订单"
            description="接单后，取货与送达的操作会出现在这里。"
          />

          <div v-else class="stack">
            <article v-for="o in currentOrders" :key="o.orderNo" class="task-card">
              <div class="task-top">
                <span class="task-no num">{{ o.orderNo }}</span>
                <StatusPill :status="o.status" size="sm" :with-icon="false" />
              </div>

              <RouteLine
                :pickup="o.pickupAddress"
                :deliver="o.deliverAddress"
                :pickup-meta="o.status === 'PENDING_PICKUP' ? formatDistance(distTo({ lng: o.pickupLng, lat: o.pickupLat })) : ''"
                :deliver-meta="o.status === 'DELIVERING' ? formatDistance(distTo({ lng: o.deliverLng, lat: o.deliverLat })) : ''"
                compact
              />

              <div class="task-meta">
                <span class="meta-item">
                  <AppIcon name="clock" :size="12" />
                  {{ formatTime(o.expectDeliverAt) }}
                </span>
                <span class="amount">¥{{ fenToYuan(o.amountFen) }}</span>
              </div>

              <div class="task-actions">
                <button
                  v-if="o.status === 'PENDING_PICKUP'"
                  class="act-btn primary wide"
                  @click="pickup(o)"
                >
                  <AppIcon name="box" :size="14" :stroke-width="2" />
                  确认取货
                </button>
                <button
                  v-if="o.status === 'DELIVERING'"
                  class="act-btn success wide"
                  @click="delivered(o)"
                >
                  <AppIcon name="check-circle" :size="14" :stroke-width="2" />
                  确认送达
                </button>
              </div>
            </article>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
}

/* ==================== 在线状态 ==================== */
.online-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 13px;
  border-radius: var(--r-md);
  font-size: var(--fs-12);
  font-weight: 700;
  color: var(--text-muted);
  background: var(--n-100);
  border: 1px solid var(--border-subtle);
}

.online-chip[data-online='true'] {
  color: var(--ok);
  background: var(--ok-bg);
  border-color: #abefc6;
}

.online-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.online-chip[data-online='true'] .online-dot {
  animation: pulse-dot 1.8s var(--ease-in-out) infinite;
}

/* ==================== 身份卡 ==================== */
.id-card {
  padding: var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  border: 1px solid var(--border);
}

.id-card[data-online='true'] {
  background: linear-gradient(150deg, var(--ok-bg), var(--n-0) 62%);
  border-color: #abefc6;
}

.id-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.id-avatar {
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  border-radius: var(--r-full);
  display: grid;
  place-items: center;
  font-size: var(--fs-16);
  font-weight: 700;
  color: #fff;
  background: linear-gradient(150deg, var(--brand-400), var(--brand-600));
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.3);
}

.id-card[data-online='true'] .id-avatar {
  background: linear-gradient(150deg, #34d399, #059669);
}

.id-info {
  flex: 1;
  min-width: 0;
}

.id-name {
  font-size: var(--fs-15);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.015em;
  margin-bottom: 3px;
}

.id-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-11);
  color: var(--text-muted);
}

.id-meta b {
  color: var(--text-strong);
  font-weight: 700;
}

.id-sep {
  width: 1px;
  height: 9px;
  background: var(--border-strong);
}

.power-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 15px;
  border-radius: var(--r-md);
  font-size: var(--fs-13);
  font-weight: 700;
  color: #fff;
  background: var(--brand);
  border: none;
  cursor: pointer;
  flex: 0 0 auto;
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.power-btn[data-online='true'] {
  color: var(--text);
  background: var(--bg-surface);
  border: 1px solid var(--border-strong);
}

.power-btn:hover {
  filter: brightness(0.97);
}

.power-btn:active {
  transform: scale(0.95);
}

/* ---------- 负载条 ---------- */
.load {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border-subtle);
}

.load-bar {
  flex: 1;
  height: 5px;
  border-radius: var(--r-full);
  background: var(--n-200);
  overflow: hidden;
}

.load-fill {
  display: block;
  height: 100%;
  border-radius: var(--r-full);
  background: var(--brand);
  transition: width var(--dur-slow) var(--ease-out-quart);
}

.load-fill[data-full='true'] {
  background: var(--danger);
}

.load-text {
  font-size: var(--fs-11);
  font-weight: 600;
  color: var(--text-muted);
  flex: 0 0 auto;
}

/* ==================== 身份切换 ==================== */
.rider-switch {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.switch-label {
  font-size: var(--fs-11);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-faint);
  flex: 0 0 auto;
}

.switch-group {
  display: flex;
  gap: 2px;
  padding: 3px;
  border-radius: var(--r-md);
  background: var(--n-100);
  border: 1px solid var(--border-subtle);
  flex: 1;
  max-width: 340px;
}

.account {
  justify-content: space-between;
  align-items: center;
  gap: var(--sp-3);
  padding: 4px 6px 4px 12px;
}

.account-name {
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--text-strong);
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.account-no {
  font-style: normal;
  font-size: var(--fs-11);
  font-weight: 500;
  color: var(--text-faint);
}

.switch-account-btn {
  flex: 0 0 auto;
  height: 28px;
  padding: 0 14px;
  border-radius: var(--r-sm);
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--brand-600);
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.switch-account-btn:hover {
  background: var(--brand-100);
  color: var(--brand-700);
}

.switch-account-btn:active {
  transform: scale(0.97);
}

/* ==================== 态势 ==================== */
.ws {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.ws-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--n-400);
}

.ws[data-state='connected'] .ws-dot,
.ws[data-state='reconnected'] .ws-dot {
  background: var(--ok);
  animation: pulse-dot 1.8s var(--ease-in-out) infinite;
}

.coord-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
  padding: 8px 12px;
  border-radius: var(--r-md);
  background: var(--n-50);
  border: 1px solid var(--border-subtle);
}

.coord-label {
  font-size: var(--fs-11);
  color: var(--text-faint);
  flex: 0 0 auto;
}

.coord-value {
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sim-row {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-3);
}

.sim-note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: var(--sp-3);
  font-size: var(--fs-12);
  color: var(--ok);
  font-weight: 500;
}

.sim-note.muted {
  color: var(--text-faint);
}

/* ==================== 按钮 ==================== */
.act-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  flex: 1;
  height: 36px;
  padding: 0 14px;
  border-radius: var(--r-md);
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--text);
  background: var(--bg-surface);
  border: 1px solid var(--border-strong);
  cursor: pointer;
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.act-btn:hover:not(:disabled) {
  background: var(--n-50);
  color: var(--text-strong);
  border-color: var(--n-400);
}

.act-btn:active:not(:disabled) {
  transform: scale(0.975);
}

.act-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.act-btn.primary {
  color: #fff;
  background: var(--brand);
  border-color: var(--brand);
  box-shadow: 0 1px 2px rgb(242 96 12 / 0.24);
}

.act-btn.primary:hover:not(:disabled) {
  background: var(--brand-hover);
  border-color: var(--brand-hover);
  color: #fff;
}

.act-btn.success {
  color: #fff;
  background: var(--ok);
  border-color: var(--ok);
}

.act-btn.success:hover:not(:disabled) {
  filter: brightness(1.08);
  color: #fff;
}

.act-btn.danger {
  color: var(--danger);
  background: var(--danger-bg);
  border-color: #fecdca;
}

.act-btn.wide {
  flex: 1;
}

/* ==================== 派单卡 ==================== */
.task-card {
  padding: var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  border: 1px solid var(--border);
}

.task-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.task-no {
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.countdown {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border-radius: var(--r-full);
  font-size: var(--fs-11);
  font-weight: 600;
  color: var(--warn);
  background: var(--warn-bg);
  border: 1px solid #fedf89;
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}

.countdown[data-urgent='true'] {
  color: var(--danger);
  background: var(--danger-bg);
  border-color: #fecdca;
}

.task-meta {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
  margin-top: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border-subtle);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-11);
  color: var(--text-faint);
  white-space: nowrap;
}

.task-meta .amount {
  margin-left: auto;
  font-size: var(--fs-15);
}

.score-detail {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: var(--sp-3);
  padding: 8px 10px;
  border-radius: var(--r-md);
  background: var(--n-50);
  border: 1px solid var(--border-subtle);
}

.score-label {
  font-size: var(--fs-11);
  color: var(--text-faint);
  flex: 0 0 auto;
  padding-top: 1px;
}

.score-value {
  font-size: var(--fs-11);
  color: var(--text-muted);
  line-height: 1.6;
  word-break: break-all;
}

.task-actions {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

/* ==================== 计数徽标 ==================== */
.count-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: var(--r-full);
  display: inline-grid;
  place-items: center;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  background: var(--brand);
  font-family: var(--num-font);
}
</style>
