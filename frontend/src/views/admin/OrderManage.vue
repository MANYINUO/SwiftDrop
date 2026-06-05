<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'
import { toastError } from '../../api/http'
import {
  statusLabel, fenToYuan, formatTime, formatTimeShort, formatDistance,
  taskStatusLabel, taskStatusTone, eventLabel, eventIcon, operatorLabel,
} from '../../utils/format'
import { useResponsiveValue, useViewport } from '../../composables/useViewport'
import AppIcon from '../../components/AppIcon.vue'
import MiniMap from '../../components/MiniMap.vue'
import StatusPill from '../../components/StatusPill.vue'
import StatusSteps from '../../components/StatusSteps.vue'
import RouteLine from '../../components/RouteLine.vue'
import EmptyState from '../../components/EmptyState.vue'

/* 这张表 8 列，列宽总和约 900px。后台侧栏还要占 236px，所以只有
   视口 ≥1280px（xl）时内容区才装得下；再窄就会出现横向滚动 ——
   而横向滚动时 fixed 的操作列会压住中间列，看起来像坏了。
   所以 <1280px 统一降级成卡片列表。抽屉宽度与地图高度同步降级。 */
const { width: vw } = useViewport()
const compact = computed(() => vw.value < 1280)
const drawerSize = useResponsiveValue({ mobile: '100%', tablet: '88%', desktop: '62%' })
const detailMapHeight = useResponsiveValue({ mobile: 180, tablet: 200, desktop: 196 })

const loading = ref(false)
const orders = ref([])
const total = ref(0)

const query = ref({
  status: '',
  needManual: false,
  pageNum: 1,
  pageSize: 10,
})

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 'PENDING_DISPATCH', label: '待派单' },
  { value: 'DISPATCHING', label: '派单中' },
  { value: 'PENDING_PICKUP', label: '待取货' },
  { value: 'DELIVERING', label: '配送中' },
  { value: 'DELIVERED', label: '已送达' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'EXCEPTION', label: '异常' },
]

/* ==================== 详情抽屉 ==================== */

const drawer = ref(false)
const detail = ref(null)
const dispatchTasks = ref([])
const events = ref([])
const riders = ref([])
const detailLoading = ref(false)
const selectedRiderId = ref(null)
const acting = ref(false)

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    const page = await adminApi.orders({
      status: query.value.status || undefined,
      needManual: query.value.needManual ? true : undefined,
      pageNum: query.value.pageNum,
      pageSize: query.value.pageSize,
    })
    orders.value = page.records || []
    total.value = page.total || 0
  } catch (e) {
    if (!silent) toastError(e)
  } finally {
    if (!silent) loading.value = false
  }
}

function applyFilter() {
  query.value.pageNum = 1
  load()
}

async function openDetail(row) {
  drawer.value = true
  detailLoading.value = true
  selectedRiderId.value = null
  try {
    const [d, t, ev, rs] = await Promise.all([
      adminApi.orderDetail(row.orderNo),
      adminApi.dispatchTasks(row.orderNo),
      adminApi.orderEvents(row.orderNo),
      adminApi.riders(),
    ])
    detail.value = d
    dispatchTasks.value = t || []
    events.value = ev || []
    riders.value = rs || []
  } catch (e) {
    toastError(e)
  } finally {
    detailLoading.value = false
  }
}

async function reloadDetail() {
  if (detail.value) {
    await openDetail({ orderNo: detail.value.orderNo })
  }
  await load()
}

/* ==================== 调度操作 ==================== */

async function manualDispatch() {
  acting.value = true
  try {
    const r = await adminApi.dispatch(detail.value.orderNo, selectedRiderId.value || undefined)
    ElMessage.success(r.message || '已派单')
    await reloadDetail()
  } catch (e) {
    toastError(e)
  } finally {
    acting.value = false
  }
}

async function markException() {
  let remark = '后台标记异常'
  try {
    const { value } = await ElMessageBox.prompt('请输入异常原因', '标记异常', {
      inputValue: '骑手失联，需人工介入',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
    remark = value || remark
  } catch {
    return
  }
  try {
    await adminApi.markException(detail.value.orderNo, remark)
    ElMessage.success('已标记异常')
    await reloadDetail()
  } catch (e) {
    toastError(e)
  }
}

async function resolveOrder(action) {
  const label = action === 'REASSIGN' ? '改派' : '取消'
  let reason = action === 'REASSIGN' ? '后台改派' : '后台取消异常订单'
  try {
    const { value } = await ElMessageBox.prompt(`确认${label}？可填写原因`, `${label}订单`, {
      inputValue: reason,
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
    reason = value || reason
  } catch {
    return
  }
  try {
    await adminApi.resolve(detail.value.orderNo, action, reason, selectedRiderId.value || undefined)
    ElMessage.success(`${label}成功`)
    await reloadDetail()
  } catch (e) {
    toastError(e)
  }
}

/* ==================== 计算 ==================== */

const pickupPoint = computed(() =>
  detail.value ? { lng: Number(detail.value.pickupLng), lat: Number(detail.value.pickupLat) } : null,
)
const deliverPoint = computed(() =>
  detail.value ? { lng: Number(detail.value.deliverLng), lat: Number(detail.value.deliverLat) } : null,
)

const timeline = computed(() => [...events.value].reverse())

/** 可选骑手：在线且未满载 */
const availableRiders = computed(() =>
  riders.value.filter((r) => r.online && (r.deliveringCount ?? 0) < (r.maxCapacity ?? 1)),
)

const canMarkException = computed(
  () =>
    detail.value &&
    detail.value.status !== 'EXCEPTION' &&
    !['DELIVERED', 'CANCELLED'].includes(detail.value.status),
)

let timer = null
onMounted(() => {
  load()
  timer = setInterval(() => load(true), 8000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="om">
    <header class="page-head">
      <div class="page-head-titles">
        <span class="eyebrow">订单调度</span>
        <h1>订单管理</h1>
        <p class="page-head-desc">
          查询与筛选订单，跟踪派单进度，必要时人工指派或处理异常。
        </p>
      </div>
      <div class="page-actions">
        <el-button size="small" :loading="loading" @click="load(false)">
          <AppIcon name="refresh" :size="14" :stroke-width="2.2" />
          刷新
        </el-button>
      </div>
    </header>

    <!-- ==================== 筛选 ==================== -->
    <div class="filter-bar">
      <el-select
        v-model="query.status"
        placeholder="全部状态"
        class="status-select"
        @change="applyFilter"
      >
        <el-option v-for="o in STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>

      <label class="check" :class="{ on: query.needManual }">
        <input v-model="query.needManual" type="checkbox" @change="applyFilter" />
        <span class="check-box">
          <AppIcon v-if="query.needManual" name="check" :size="11" :stroke-width="3" />
        </span>
        只看待人工调度
      </label>

      <span class="filter-count">
        共 <b class="num">{{ total }}</b> 条
      </span>
    </div>

    <!-- ==================== 表格（≥1280px） ==================== -->
    <div v-if="!compact" class="table-wrap">
      <el-table :data="orders" v-loading="loading" size="default" stripe>
        <el-table-column prop="orderNo" label="订单号" width="190">
          <template #default="{ row }">
            <span class="order-no num">{{ row.orderNo }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="94">
          <template #default="{ row }">
            <StatusPill :status="row.status" size="sm" :with-icon="false" />
          </template>
        </el-table-column>

        <el-table-column label="路线" min-width="200">
          <template #default="{ row }">
            <RouteLine :pickup="row.pickupAddress" :deliver="row.deliverAddress" compact />
          </template>
        </el-table-column>

        <el-table-column label="骑手" width="72">
          <template #default="{ row }">
            <span v-if="row.riderId" class="num rider-id">#{{ row.riderId }}</span>
            <span v-else class="faint">未派</span>
          </template>
        </el-table-column>

        <el-table-column label="匹配轮次" width="88">
          <template #default="{ row }">
            <span :class="row.needManual ? 'warn-text' : 'num'">
              第 {{ row.dispatchRound || 0 }} 轮
            </span>
          </template>
        </el-table-column>

        <el-table-column label="金额" width="84" align="right">
          <template #default="{ row }">
            <span class="amount">¥{{ fenToYuan(row.amountFen) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="下单时间" width="106">
          <template #default="{ row }">
            <span class="time-cell num">{{ formatTimeShort(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="96" fixed="right" align="right">
          <template #default="{ row }">
            <button class="row-btn" @click="openDetail(row)">详情 / 调度</button>
          </template>
        </el-table-column>

        <template #empty>
          <EmptyState
            compact
            icon="list"
            title="没有匹配的订单"
            description="换个状态，或取消「只看待人工调度」。"
          />
        </template>
      </el-table>

      <div v-if="total > query.pageSize" class="pager-row">
        <el-pagination
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          @current-change="(p) => { query.pageNum = p; load() }"
        />
      </div>
    </div>

    <!-- ==================== 卡片列表（<1280px） ==================== -->
    <div v-else class="order-cards">
      <EmptyState
        v-if="!loading && orders.length === 0"
        icon="list"
        title="没有匹配的订单"
        description="换个状态，或取消「只看待人工调度」。"
      />

      <article
        v-for="row in orders"
        :key="row.orderNo"
        class="order-card"
        @click="openDetail(row)"
      >
        <div class="oc-top">
          <span class="order-no num">{{ row.orderNo }}</span>
          <StatusPill :status="row.status" size="sm" :with-icon="false" />
        </div>

        <RouteLine :pickup="row.pickupAddress" :deliver="row.deliverAddress" compact />

        <div class="oc-meta">
          <span class="oc-item">
            <AppIcon name="scooter" :size="12" />
            <template v-if="row.riderId">#{{ row.riderId }}</template>
            <template v-else>未派</template>
          </span>
          <span class="oc-item" :class="{ 'warn-text': row.needManual }">
            <AppIcon name="layers" :size="12" />
            第 {{ row.dispatchRound || 0 }} 轮
          </span>
          <span class="amount">¥{{ fenToYuan(row.amountFen) }}</span>
        </div>

        <div class="oc-foot">
          <span class="num">{{ formatTimeShort(row.createdAt) }}</span>
          <span class="row-btn">详情 / 调度 →</span>
        </div>
      </article>

      <div v-if="total > query.pageSize" class="pager-row">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          @current-change="(p) => { query.pageNum = p; load() }"
        />
      </div>
    </div>

    <!-- ==================== 详情抽屉 ==================== -->
    <el-drawer
      v-model="drawer"
      :size="drawerSize"
      :with-header="false"
      class="order-drawer"
    >
      <div v-loading="detailLoading" class="drawer">
        <template v-if="detail">
          <!-- 抽屉头 -->
          <div class="drawer-head">
            <div class="drawer-head-main">
              <span class="eyebrow">订单详情</span>
              <span class="drawer-no num">{{ detail.orderNo }}</span>
            </div>
            <div class="drawer-head-actions">
              <StatusPill :status="detail.status" :with-icon="false" />
              <button class="icon-btn" aria-label="关闭" @click="drawer = false">
                <AppIcon name="close" :size="15" :stroke-width="2.2" />
              </button>
            </div>
          </div>

          <!-- 状态流程 -->
          <section class="d-section">
            <div v-if="detail.needManual" class="manual-strip">
              <AppIcon name="alert" :size="15" :stroke-width="2.1" />
              <span>暂时没有骑手接单，这笔订单等待人工调度。</span>
            </div>
            <StatusSteps :status="detail.status" />
          </section>

          <!-- 地图 -->
          <section class="d-section">
            <div class="section-title">配送路线</div>
            <MiniMap
              :pickup="pickupPoint"
              :deliver="deliverPoint"
              :rider="null"
              :height="detailMapHeight"
              show-coords
            />
          </section>

          <!-- 订单信息 -->
          <section class="d-section">
            <div class="section-title">订单信息</div>
            <dl class="kv-grid">
              <div class="kv">
                <dt>取件地址</dt>
                <dd>{{ detail.pickupAddress }}</dd>
              </div>
              <div class="kv">
                <dt>送达地址</dt>
                <dd>{{ detail.deliverAddress }}</dd>
              </div>
              <div class="kv">
                <dt>联系人</dt>
                <dd>{{ detail.pickupContact }} → {{ detail.deliverContact }}</dd>
              </div>
              <div class="kv">
                <dt>距离 / 金额</dt>
                <dd>
                  <span class="num">{{ formatDistance(detail.distanceM) }}</span>
                  <span class="kv-sep">·</span>
                  <span class="amount">¥{{ fenToYuan(detail.amountFen) }}</span>
                </dd>
              </div>
              <div class="kv">
                <dt>骑手</dt>
                <dd>
                  <span v-if="detail.riderId" class="num">#{{ detail.riderId }}</span>
                  <span v-else class="faint">未派单</span>
                </dd>
              </div>
              <div class="kv">
                <dt>匹配轮次</dt>
                <dd>第 {{ detail.dispatchRound || 0 }} 轮</dd>
              </div>
              <div class="kv">
                <dt>承诺送达</dt>
                <dd class="num">{{ formatTime(detail.expectDeliverAt) }}</dd>
              </div>
              <div class="kv">
                <dt>下单时间</dt>
                <dd class="num">{{ formatTime(detail.createdAt) }}</dd>
              </div>
            </dl>
          </section>

          <!-- 调度操作 -->
          <section class="d-section">
            <div class="section-title">
              调度操作
              <span class="section-tail">{{ availableRiders.length }} 位骑手可派</span>
            </div>

            <div class="ops">
              <el-select
                v-model="selectedRiderId"
                placeholder="不指定骑手则自动重新匹配"
                clearable
                style="flex: 1; min-width: 240px"
              >
                <el-option
                  v-for="r in availableRiders"
                  :key="r.riderId"
                  :label="`${r.name}（${r.riderNo}）在手 ${r.deliveringCount}/${r.maxCapacity}`"
                  :value="r.riderId"
                />
              </el-select>

              <el-button type="primary" :loading="acting" @click="manualDispatch">
                <AppIcon name="target" :size="14" :stroke-width="2.1" />
                {{ selectedRiderId ? '指派给该骑手' : '自动重新匹配' }}
              </el-button>

              <el-button v-if="canMarkException" type="warning" plain @click="markException">
                <AppIcon name="alert" :size="14" :stroke-width="2.1" />
                标记异常
              </el-button>

              <template v-if="detail.status === 'EXCEPTION'">
                <el-button type="primary" plain @click="resolveOrder('REASSIGN')">
                  <AppIcon name="refresh" :size="14" :stroke-width="2.1" />
                  改派
                </el-button>
                <el-button type="danger" plain @click="resolveOrder('CANCEL')">
                  <AppIcon name="ban" :size="14" :stroke-width="2.1" />
                  取消订单
                </el-button>
              </template>
            </div>

            <p class="ops-note">
              列表仅显示在线且未满载、当前可接单的骑手。
            </p>
          </section>

          <!-- 派单记录 -->
          <section class="d-section">
            <div class="section-title">
              派单记录
              <span class="section-tail">{{ dispatchTasks.length }} 条记录</span>
            </div>

            <EmptyState
              v-if="dispatchTasks.length === 0"
              compact
              icon="send"
              title="暂无派单记录"
              description="订单还没进入派单流程。"
            />

            <div v-else class="tasks">
              <article v-for="t in dispatchTasks" :key="t.taskId" class="task">
                <div class="task-head">
                  <div class="task-id">
                    <span class="task-round num">R{{ t.round }}</span>
                    <span class="task-rank">排名 <b class="num">{{ t.candidateRank }}</b></span>
                    <span class="task-rider num">骑手 #{{ t.riderId }}</span>
                  </div>
                  <div class="task-right">
                    <span class="task-status" :data-tone="taskStatusTone(t.status)">
                      {{ taskStatusLabel(t.status) }}
                    </span>
                  </div>
                </div>
              </article>
            </div>
          </section>

          <!-- 事件流水 -->
          <section class="d-section">
            <div class="section-title">
              事件流水
              <span class="section-tail">{{ timeline.length }} 条</span>
            </div>

            <ol class="tl">
              <li v-for="(e, i) in timeline" :key="e.id" class="tl-item" :class="{ head: i === 0 }">
                <span class="tl-node">
                  <AppIcon :name="eventIcon(e.eventType)" :size="12" :stroke-width="2.1" />
                </span>
                <div class="tl-body">
                  <div class="tl-top">
                    <span class="tl-label">{{ eventLabel(e.eventType) }}</span>
                    <span class="tl-op">{{ operatorLabel(e.operatorType) }}</span>
                  </div>
                  <p v-if="e.remark" class="tl-remark">{{ e.remark }}</p>
                </div>
                <time class="tl-time num">{{ formatTime(e.createdAt) }}</time>
              </li>
            </ol>
          </section>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.om {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}

/* ==================== 筛选条 ==================== */
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  flex-wrap: wrap;
  padding: var(--sp-3) var(--sp-4);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
}

.check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-13);
  font-weight: 500;
  color: var(--text-muted);
  cursor: pointer;
  user-select: none;
}

.check.on {
  color: var(--text-strong);
}

.check input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.check-box {
  width: 17px;
  height: 17px;
  border-radius: var(--r-xs);
  border: 1.5px solid var(--border-strong);
  background: var(--bg-surface);
  display: grid;
  place-items: center;
  color: #fff;
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.check.on .check-box {
  background: var(--brand);
  border-color: var(--brand);
}

.filter-count {
  margin-left: auto;
  font-size: var(--fs-12);
  color: var(--text-faint);
}

.filter-count b {
  color: var(--text-strong);
  font-weight: 700;
}

.status-select {
  width: 150px;
}

@media (max-width: 767px) {
  .filter-bar {
    gap: var(--sp-3);
    padding: var(--sp-3);
  }

  /* 手机上下拉占满整行，勾选框与计数并排第二行 */
  .status-select {
    width: 100%;
  }

  .filter-count {
    margin-left: auto;
  }
}

/* ==================== 表格 ==================== */
.table-wrap {
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  padding: var(--sp-2) var(--sp-2) 0;
  overflow: hidden;
}

.table-wrap :deep(.el-table) {
  --el-table-border-color: transparent;
}

.table-wrap :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.rider-id {
  font-weight: 600;
  color: var(--text);
}

/* 订单号 / 时间都是定长文本，折行会让行高忽高忽低 */
.order-no {
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
}

.time-cell {
  font-size: var(--fs-12);
  color: var(--text-faint);
  white-space: nowrap;
}

.warn-text {
  color: var(--warn);
  font-weight: 600;
}

.row-btn {
  font-size: var(--fs-13);
  font-weight: 600;
  color: var(--brand-600);
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  transition: opacity var(--dur-fast);
}

.row-btn:hover {
  opacity: 0.75;
  text-decoration: underline;
}

.pager-row {
  display: flex;
  justify-content: flex-end;
  padding: var(--sp-3) var(--sp-3) var(--sp-3);
}

/* ==================== 卡片列表（<1024px） ==================== */
.order-cards {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--sp-3);
}

@media (min-width: 768px) {
  .order-cards {
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  }
}

.order-card {
  padding: var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  cursor: pointer;
  transition: border-color var(--dur) var(--ease-out-quart),
    box-shadow var(--dur) var(--ease-out-quart);
}

.order-card:active {
  border-color: var(--brand-200);
  box-shadow: var(--sh-sm);
}

.oc-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.oc-meta {
  display: flex;
  align-items: center;
  gap: var(--sp-4);
  flex-wrap: wrap;
  margin-top: var(--sp-4);
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border-subtle);
}

.oc-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-11);
  color: var(--text-faint);
  white-space: nowrap;
}

.oc-meta .amount {
  margin-left: auto;
  font-size: var(--fs-15);
}

.oc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
  font-size: var(--fs-11);
  color: var(--text-faint);
}

/* ==================== 抽屉 ==================== */
.drawer {
  display: flex;
  flex-direction: column;
  gap: var(--sp-6);
  padding-bottom: var(--sp-8);
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  padding-bottom: var(--sp-4);
  border-bottom: 1px solid var(--border);
  position: sticky;
  top: -24px;
  background: var(--bg-app);
  padding-top: 4px;
  z-index: 3;
}

.drawer-head-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.drawer-no {
  font-size: var(--fs-18);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.02em;
}

.drawer-head-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex: 0 0 auto;
}

.d-section {
  display: flex;
  flex-direction: column;
}

.manual-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--warn-bg);
  border: 1px solid #fedf89;
  color: #93370d;
  font-size: var(--fs-13);
  font-weight: 500;
}

/* ---------- KV 网格 ---------- */
.kv-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1px;
  background: var(--border-subtle);
  border: 1px solid var(--border-subtle);
  border-radius: var(--r-md);
  overflow: hidden;
}

.kv {
  background: var(--bg-surface);
  padding: 11px 14px;
  min-width: 0;
}

.kv dt {
  font-size: var(--fs-11);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-faint);
  margin-bottom: 4px;
}

.kv dd {
  margin: 0;
  font-size: var(--fs-13);
  font-weight: 500;
  color: var(--text-strong);
  word-break: break-word;
}

.kv-sep {
  color: var(--n-300);
  margin: 0 5px;
}

/* ---------- 调度操作 ---------- */
.ops {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.ops-note {
  margin-top: var(--sp-3);
  font-size: var(--fs-12);
  color: var(--text-faint);
}

/* ---------- 派单记录 ---------- */
.tasks {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.task {
  padding: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--bg-surface);
  border: 1px solid var(--border);
}

.task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  flex-wrap: wrap;
  margin-bottom: var(--sp-3);
}

.task-id {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  flex-wrap: wrap;
}

.task-round {
  font-size: var(--fs-11);
  font-weight: 700;
  color: var(--brand-700);
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
  border-radius: var(--r-xs);
  padding: 2px 7px;
}

.task-rank,
.task-rider {
  font-size: var(--fs-12);
  color: var(--text-muted);
}

.task-rank b {
  color: var(--text-strong);
  font-weight: 700;
}

.task-right {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
}

.task-score {
  font-size: var(--fs-12);
  color: var(--text-muted);
}

.task-score b {
  color: var(--text-strong);
  font-weight: 700;
  font-size: var(--fs-13);
}

.task-status {
  --c: var(--text-muted);
  --bg: var(--n-100);
  --bd: var(--border);
  font-size: var(--fs-11);
  font-weight: 700;
  color: var(--c);
  background: var(--bg);
  border: 1px solid var(--bd);
  border-radius: var(--r-full);
  padding: 2px 9px;
}

.task-status[data-tone='pending-dispatch'] { --c: var(--st-pending-dispatch); --bg: var(--st-pending-dispatch-bg); --bd: var(--st-pending-dispatch-bd); }
.task-status[data-tone='delivered'] { --c: var(--st-delivered); --bg: var(--st-delivered-bg); --bd: var(--st-delivered-bd); }
.task-status[data-tone='exception'] { --c: var(--st-exception); --bg: var(--st-exception-bg); --bd: var(--st-exception-bd); }
.task-status[data-tone='cancelled'] { --c: var(--st-cancelled); --bg: var(--st-cancelled-bg); --bd: var(--st-cancelled-bd); }

/* ---------- 因子条 ---------- */
/* 5 个因子要在一行里放下。minmax 给到 140px 时，871px 宽的抽屉正好排 5 列；
   给 168px 只能排 4 列，第 5 个会变成孤儿行。 */
.factors {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 9px var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--border-subtle);
}

.factor {
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) 32px;
  align-items: center;
  gap: 7px;
}

.factor-label {
  font-size: var(--fs-11);
  color: var(--text-muted);
}

.factor-bar {
  height: 5px;
  border-radius: var(--r-full);
  background: var(--n-100);
  overflow: hidden;
}

.factor-bar i {
  display: block;
  height: 100%;
  border-radius: var(--r-full);
  background: linear-gradient(90deg, var(--brand-300), var(--brand-500));
  transition: width var(--dur-slow) var(--ease-out-quart);
}

.factor-value {
  font-size: var(--fs-11);
  font-weight: 700;
  color: var(--text);
  text-align: right;
}

.factor-raw {
  font-size: var(--fs-11);
  color: var(--text-faint);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--border-subtle);
  word-break: break-all;
}

.formula {
  margin-top: var(--sp-3);
  padding: 9px 12px;
  border-radius: var(--r-md);
  background: var(--n-50);
  border: 1px solid var(--border-subtle);
  font-family: var(--num-font);
  font-size: var(--fs-11);
  color: var(--text-muted);
  text-align: center;
}

/* ---------- 事件流水 ---------- */
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
  font-size: 10px;
  color: var(--text-faint);
  padding-top: 4px;
}

@media (max-width: 760px) {
  .kv-grid {
    grid-template-columns: 1fr;
  }

  /* 手机上调度操作竖排，按钮占满整行才好点 */
  .ops {
    flex-direction: column;
    align-items: stretch;
  }

  .ops :deep(.el-select) {
    width: 100% !important;
    min-width: 0 !important;
  }

  .ops :deep(.el-button) {
    margin-left: 0 !important;
    width: 100%;
  }

  .drawer-head {
    top: -16px;
    padding-top: 0;
  }

  .drawer-no {
    font-size: var(--fs-15);
  }

  .task-head {
    align-items: flex-start;
  }
}
</style>
