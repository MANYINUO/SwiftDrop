<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../../api'
import { toastError } from '../../api/http'
import {
  statusTone, fenToYuan, formatTime, formatDistance, fromNow,
} from '../../utils/format'
import AppIcon from '../../components/AppIcon.vue'
import StatusPill from '../../components/StatusPill.vue'
import RouteLine from '../../components/RouteLine.vue'
import EmptyState from '../../components/EmptyState.vue'

/**
 * 我的订单。
 *
 * 响应式：
 *   ≥768px   订单卡走自适应栅格（每行 2~3 张），筛选条换行铺开
 *   <768px   单列；筛选条横向滚动 + 右缘淡出提示
 *
 * 数据与接口调用沿用原实现（userApi.listOrders / cancelOrder / createOrder）。
 */
const router = useRouter()
const loading = ref(false)
const orders = ref([])
const total = ref(0)
const query = ref({ pageNum: 1, pageSize: 12, status: '' })

const STATUS_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'PENDING_DISPATCH', label: '待派单' },
  { value: 'DISPATCHING', label: '派单中' },
  { value: 'PENDING_PICKUP', label: '待取货' },
  { value: 'DELIVERING', label: '配送中' },
  { value: 'DELIVERED', label: '已送达' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'EXCEPTION', label: '异常' },
]

/** 可以取消的状态 */
const CANCELABLE = ['PENDING_DISPATCH', 'DISPATCHING', 'PENDING_PICKUP']

const canCancel = (o) => CANCELABLE.includes(o.status)

const hasMore = computed(() => total.value > query.value.pageSize)

async function load() {
  loading.value = true
  try {
    const page = await userApi.listOrders({
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
      status: query.value.status || undefined,
    })
    orders.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    toastError(e)
  } finally {
    loading.value = false
  }
}

function pickStatus(v) {
  query.value.status = v
  query.value.pageNum = 1
  load()
}

async function cancel(order) {
  try {
    await ElMessageBox.confirm(
      `取消后不可恢复，确认取消订单 ${order.orderNo}？`,
      '取消订单',
      { type: 'warning', confirmButtonText: '确认取消', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  try {
    await userApi.cancelOrder(order.orderNo, '用户在前端取消')
    ElMessage.success('订单已取消')
    load()
  } catch (e) {
    toastError(e)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page-head">
      <div class="page-head-main">
        <span class="eyebrow">订单中心</span>
        <h1 class="page-title">我的订单</h1>
        <p class="page-desc">共 {{ total }} 笔，点击卡片可查看实时进度与骑手位置。</p>
      </div>
      <div class="page-head-actions">
        <router-link to="/user/create" class="primary-btn">
          <AppIcon name="plus" :size="14" :stroke-width="2.4" />
          新建订单
        </router-link>
      </div>
    </header>

    <!-- ==================== 状态筛选 ==================== -->
    <div class="filters">
      <button
        v-for="o in STATUS_OPTIONS"
        :key="o.value"
        type="button"
        class="filter"
        :class="{ on: query.status === o.value }"
        :data-tone="o.value ? statusTone(o.value) : 'all'"
        @click="pickStatus(o.value)"
      >
        {{ o.label }}
      </button>
    </div>

    <!-- ==================== 订单列表 ==================== -->
    <template v-if="loading">
      <div class="card-grid">
        <div v-for="n in 4" :key="n" class="order-card">
          <div class="skeleton" style="height: 14px; width: 46%"></div>
          <div class="skeleton" style="height: 12px; width: 78%; margin-top: 14px"></div>
          <div class="skeleton" style="height: 12px; width: 62%; margin-top: 9px"></div>
          <div class="skeleton" style="height: 12px; width: 40%; margin-top: 18px"></div>
        </div>
      </div>
    </template>

    <EmptyState
      v-else-if="orders.length === 0"
      icon="box"
      title="还没有订单"
      :description="
        query.status
          ? '当前筛选条件下没有订单，换个状态看看。'
          : '下单后这里会显示订单状态、路线和金额。'
      "
    >
      <template #action>
        <router-link to="/user/create" class="ghost-btn">
          <AppIcon name="plus" :size="14" :stroke-width="2.4" />
          去下单
        </router-link>
      </template>
    </EmptyState>

    <div v-else class="card-grid">
      <article
        v-for="(o, i) in orders"
        :key="o.orderNo"
        class="order-card"
        :style="{ '--i': i }"
        @click="router.push(`/user/orders/${o.orderNo}`)"
      >
        <div class="order-top">
          <span class="order-no num">{{ o.orderNo }}</span>
          <StatusPill :status="o.status" size="sm" />
        </div>

        <RouteLine :pickup="o.pickupAddress" :deliver="o.deliverAddress" compact />

        <div class="order-foot">
          <span class="foot-item">
            <AppIcon name="route" :size="12" />
            {{ formatDistance(o.distanceM) }}
          </span>
          <span class="foot-item">
            <AppIcon name="clock" :size="12" />
            {{ fromNow(o.createdAt) }}
          </span>
          <span class="amount">¥{{ fenToYuan(o.amountFen) }}</span>
        </div>

        <div v-if="canCancel(o)" class="order-actions" @click.stop>
          <button class="cancel-btn" @click="cancel(o)">取消订单</button>
          <span class="foot-time num">{{ formatTime(o.createdAt) }}</span>
        </div>
      </article>
    </div>

    <div v-if="hasMore" class="pager-row">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="query.pageSize"
        :current-page="query.pageNum"
        @current-change="(p) => { query.pageNum = p; load() }"
      />
    </div>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
}

/* ==================== 筛选 ==================== */
.filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: var(--sp-4);
}

.filter {
  --c: var(--brand-700);
  --bg: var(--brand-50);
  --bd: var(--brand-200);
  padding: 5px 12px;
  border-radius: var(--r-full);
  font-size: var(--fs-12);
  font-weight: 500;
  color: var(--text-muted);
  background: var(--n-50);
  border: 1px solid var(--border);
  cursor: pointer;
  white-space: nowrap;
  flex: 0 0 auto;
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.filter:hover {
  color: var(--text-strong);
  border-color: var(--border-strong);
}

.filter.on {
  background: var(--bg);
  border-color: var(--bd);
  color: var(--c);
  font-weight: 700;
}

.filter:active {
  transform: scale(0.96);
}

.filter[data-tone='pending-dispatch'].on { --c: var(--st-pending-dispatch); --bg: var(--st-pending-dispatch-bg); --bd: var(--st-pending-dispatch-bd); }
.filter[data-tone='dispatching'].on { --c: var(--st-dispatching); --bg: var(--st-dispatching-bg); --bd: var(--st-dispatching-bd); }
.filter[data-tone='pending-pickup'].on { --c: var(--st-pending-pickup); --bg: var(--st-pending-pickup-bg); --bd: var(--st-pending-pickup-bd); }
.filter[data-tone='delivering'].on { --c: var(--st-delivering); --bg: var(--st-delivering-bg); --bd: var(--st-delivering-bd); }
.filter[data-tone='delivered'].on { --c: var(--st-delivered); --bg: var(--st-delivered-bg); --bd: var(--st-delivered-bd); }
.filter[data-tone='cancelled'].on { --c: var(--st-cancelled); --bg: var(--st-cancelled-bg); --bd: var(--st-cancelled-bd); }
.filter[data-tone='exception'].on { --c: var(--st-exception); --bg: var(--st-exception-bg); --bd: var(--st-exception-bd); }

/* 移动端：一行放不下，改成横向滚动 + 右缘淡出 */
@media (max-width: 767px) {
  .filters {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 2px;
    scrollbar-width: none;
    mask-image: linear-gradient(to right, #000 0, #000 calc(100% - 26px), transparent 100%);
    -webkit-mask-image: linear-gradient(to right, #000 0, #000 calc(100% - 26px), transparent 100%);
  }

  .filters::-webkit-scrollbar {
    display: none;
  }
}

/* ==================== 订单卡 ==================== */
.order-card {
  padding: var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease-out-quart),
    box-shadow var(--dur) var(--ease-out-quart),
    transform var(--dur) var(--ease-out-quart);
}

/* 入场淡入为渐进增强：默认可见，仅在动画时间轴正常推进时错位淡入 */
@media (prefers-reduced-motion: no-preference) {
  html.motion .order-card {
    animation: rise-in var(--dur-slow) var(--ease-out-quart) both;
    animation-delay: calc(var(--i) * 45ms);
  }
}

.order-card:hover {
  border-color: var(--brand-200);
  box-shadow: 0 6px 20px -10px rgb(16 24 40 / 0.18);
  transform: translateY(-2px);
}

.order-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.order-no {
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--text);
  letter-spacing: -0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-foot {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  margin-top: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border-subtle);
}

.foot-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-11);
  color: var(--text-faint);
  white-space: nowrap;
}

.order-foot .amount {
  margin-left: auto;
  font-size: var(--fs-15);
}

.order-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--border);
}

.cancel-btn {
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--danger);
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  transition: opacity var(--dur-fast);
}

.cancel-btn:hover {
  opacity: 0.72;
  text-decoration: underline;
}

.foot-time {
  font-size: var(--fs-11);
  color: var(--text-faint);
}

/* ==================== 按钮 / 分页 ==================== */
.primary-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 34px;
  padding: 0 15px;
  border-radius: var(--r-md);
  font-size: var(--fs-13);
  font-weight: 600;
  color: #fff;
  background: var(--brand);
  box-shadow: 0 1px 2px rgb(242 96 12 / 0.28);
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.primary-btn:hover {
  background: var(--brand-hover);
  box-shadow: var(--sh-brand);
}

.primary-btn:active {
  transform: scale(0.975);
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
}

.ghost-btn:hover {
  color: var(--text-strong);
  background: var(--n-50);
}

.pager-row {
  display: flex;
  justify-content: center;
  margin-top: var(--sp-5);
}
</style>
