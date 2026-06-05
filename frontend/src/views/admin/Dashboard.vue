<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import { adminApi } from '../../api'
import { toastError } from '../../api/http'
import { statusLabel } from '../../utils/format'
import AppIcon from '../../components/AppIcon.vue'
import MetricCard from '../../components/MetricCard.vue'

const loading = ref(false)
const metrics = ref(null)

const pieRef = ref(null)
let pieChart = null
let timer = null

/**
 * 指标卡配置。
 * accent 用来给关键指标一条颜色，避免 12 张卡长得一模一样。
 */
const CARDS = [
  { key: 'orderTotal', label: '订单总数', unit: '单', accent: 'neutral', icon: 'box' },
  { key: 'dispatchedOrderCount', label: '已派单订单', unit: '单', accent: 'neutral', icon: 'send' },
  {
    key: 'dispatchSuccessRate',
    label: '派单成功率',
    accent: 'brand',
    icon: 'target',
    hint: '成功安排骑手的订单占全部订单的比例。',
  },
  {
    key: 'needManualOrders',
    label: '待人工调度',
    unit: '单',
    accent: 'warn',
    icon: 'alert',
    hint: '暂时没有骑手接单、需要人工介入安排的订单数。',
  },
  { key: 'dispatchTaskTotal', label: '派单推送总数', unit: '条', accent: 'neutral', icon: 'layers' },
  {
    key: 'taskResponseRate',
    label: '骑手响应率',
    accent: 'neutral',
    icon: 'activity',
    hint: '骑手确认接单的派单推送占比。',
  },
  {
    key: 'avgDispatchResponseSeconds',
    label: '平均接单响应',
    unit: '秒',
    accent: 'neutral',
    icon: 'clock',
  },
  { key: 'avgDispatchRound', label: '平均匹配轮次', unit: '轮', accent: 'neutral', icon: 'refresh' },
  { key: 'avgDeliverMinutes', label: '平均送达时长', unit: '分钟', accent: 'neutral', icon: 'speed' },
  { key: 'riderOnline', label: '在线骑手', unit: '人', accent: 'ok', icon: 'users' },
  {
    key: 'riderGeoOnline',
    label: '可派单骑手',
    unit: '人',
    accent: 'neutral',
    icon: 'database',
    hint: '当前有实时定位、可参与派单的骑手数量。',
  },
  { key: 'userWsSessions', label: '正在追踪用户', unit: '人', accent: 'neutral', icon: 'user' },
]

/** 图表配色，与状态徽标同源 */
const CHART_COLORS = {
  PENDING_DISPATCH: '#f79009',
  DISPATCHING: '#2e90fa',
  PENDING_PICKUP: '#7a5af8',
  DELIVERING: '#06aed4',
  DELIVERED: '#12b76a',
  CANCELLED: '#98a2b3',
  EXCEPTION: '#f04438',
}

function cardValue(c) {
  const v = metrics.value?.[c.key]
  if (v == null) return '—'
  return v
}

const distribution = computed(() => metrics.value?.orderStatusDistribution || {})

const distTotal = computed(() =>
  Object.values(distribution.value).reduce((s, v) => s + (Number(v) || 0), 0),
)

/** 派单漏斗：任务总数 → 已接单 */
const funnel = computed(() => {
  const m = metrics.value || {}
  const total = Number(m.dispatchTaskTotal) || 0
  const accepted = Number(m.dispatchTaskAccepted) || 0
  return {
    total,
    accepted,
    pct: total > 0 ? Math.round((accepted / total) * 100) : 0,
  }
})

const needManual = computed(() => Number(metrics.value?.needManualOrders) || 0)

async function load(silent = false) {
  // silent=true 为定时自动刷新：失败时保留上一次数据，不弹提示、不闪烁 loading
  if (!silent) loading.value = true
  try {
    metrics.value = await adminApi.metrics()
    renderPie()
  } catch (e) {
    if (!silent) toastError(e)
  } finally {
    if (!silent) loading.value = false
  }
}

function renderPie() {
  if (!pieRef.value) return
  const dist = distribution.value
  const data = Object.entries(dist).map(([k, v]) => ({
    name: statusLabel(k),
    value: v,
    itemStyle: { color: CHART_COLORS[k] || '#98a2b3' },
  }))

  pieChart = pieChart || echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}<br/>{c} 单 · {d}%',
      backgroundColor: 'rgba(255,255,255,0.97)',
      borderColor: '#e5e8ee',
      borderWidth: 1,
      padding: [8, 12],
      textStyle: { color: '#101828', fontSize: 12 },
      extraCssText: 'border-radius:8px;box-shadow:0 8px 24px -8px rgba(16,24,40,0.18);',
    },
    legend: {
      bottom: 0,
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 14,
      icon: 'roundRect',
      textStyle: { fontSize: 11, color: '#667085' },
    },
    series: [
      {
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '43%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: {
          show: true,
          position: 'center',
          formatter: () => `{v|${distTotal.value}}\n{t|订单总数}`,
          rich: {
            v: { fontSize: 26, fontWeight: 700, color: '#101828', lineHeight: 32 },
            t: { fontSize: 11, color: '#98a2b3' },
          },
        },
        emphasis: { scale: true, scaleSize: 4 },
        labelLine: { show: false },
        data: data.length ? data : [{ name: '暂无数据', value: 1, itemStyle: { color: '#e5e8ee' } }],
      },
    ],
  })
}

function resize() {
  pieChart?.resize()
}

onMounted(async () => {
  await nextTick()
  await load()
  // 每 5 秒静默刷新一次，数据自动更新且不打断操作
  timer = setInterval(() => load(true), 5000)
  window.addEventListener('resize', resize)
})

onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('resize', resize)
  pieChart?.dispose()
})
</script>

<template>
  <div class="dash">
    <header class="page-head">
      <div class="page-head-titles">
        <span class="eyebrow">运营总览</span>
        <h1>指标看板</h1>
        <p class="page-head-desc">
          实时掌握订单履约与骑手运力情况。
        </p>
      </div>
      <div class="page-actions">
        <span class="refresh-hint">
          <span class="live-dot"></span>
          实时更新中
        </span>
        <el-button size="small" :loading="loading" @click="load(false)">
          <AppIcon name="refresh" :size="14" :stroke-width="2.2" />
          刷新
        </el-button>
      </div>
    </header>

    <!-- ==================== 待人工调度告警 ==================== -->
    <Transition name="fade-slide">
      <div v-if="needManual > 0" class="attention">
        <span class="attention-icon">
          <AppIcon name="alert" :size="16" :stroke-width="2.1" />
        </span>
        <div class="attention-copy">
          <b>有 {{ needManual }} 笔订单等待人工调度</b>
          <span>暂时没有骑手接单，已转为人工处理，可前往订单管理安排骑手。</span>
        </div>
        <router-link to="/admin/orders" class="attention-link">
          去处理
          <AppIcon name="arrow-right" :size="14" :stroke-width="2.3" />
        </router-link>
      </div>
    </Transition>

    <!-- ==================== 指标卡 ==================== -->
    <div class="metric-grid">
      <MetricCard
        v-for="c in CARDS"
        :key="c.key"
        :label="c.label"
        :value="cardValue(c)"
        :unit="c.unit"
        :hint="c.hint"
        :accent="c.accent"
        :icon="c.icon"
        :compact="String(cardValue(c)).length > 6"
      />
    </div>

    <!-- ==================== 图表 ==================== -->
    <div class="charts">
      <!-- 订单状态分布 -->
      <section class="chart-card">
        <div class="chart-head">
          <div>
            <div class="chart-title">订单状态分布</div>
            <div class="chart-sub">各状态下的订单数量</div>
          </div>
          <span class="chart-total num">{{ distTotal }}<span class="chart-total-unit">单</span></span>
        </div>
        <div ref="pieRef" class="chart" style="height: 268px"></div>
      </section>

      <!-- 派单漏斗 -->
      <section class="chart-card">
        <div class="chart-head">
          <div>
            <div class="chart-title">派单推送结果</div>
            <div class="chart-sub">每次向骑手推送派单记为一条</div>
          </div>
        </div>

        <div class="funnel">
          <div class="funnel-row">
            <div class="funnel-top">
              <span class="funnel-label">推送总数</span>
              <span class="funnel-value num">{{ funnel.total }}</span>
            </div>
            <div class="funnel-bar">
              <span class="funnel-fill total" style="width: 100%"></span>
            </div>
          </div>

          <div class="funnel-row">
            <div class="funnel-top">
              <span class="funnel-label">已被骑手接单</span>
              <span class="funnel-value num">{{ funnel.accepted }}</span>
            </div>
            <div class="funnel-bar">
              <span class="funnel-fill accepted" :style="{ width: funnel.pct + '%' }"></span>
            </div>
          </div>

          <div class="funnel-conv">
            <span class="conv-pct num">{{ funnel.pct }}%</span>
            <span class="conv-label">骑手响应率</span>
          </div>

          <p class="funnel-note">
            同一订单可能依次推送给多位骑手，响应率反映骑手的接单意愿；
            订单最终是否成功派出，请以 <b>派单成功率</b> 为准。
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dash {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}

.refresh-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-12);
  color: var(--text-muted);
}

/* ==================== 告警条 ==================== */
.attention {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-4);
  border-radius: var(--r-lg);
  background: linear-gradient(120deg, var(--warn-bg), #fffdf5);
  border: 1px solid #fedf89;
}

.attention-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: var(--r-md);
  display: grid;
  place-items: center;
  background: rgb(255 255 255 / 0.75);
  color: var(--warn);
  border: 1px solid #fedf89;
}

.attention-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.attention-copy b {
  font-size: var(--fs-13);
  font-weight: 700;
  color: #93370d;
}

.attention-copy span {
  font-size: var(--fs-12);
  color: #b54708;
}

.attention-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-13);
  font-weight: 700;
  color: var(--warn);
  flex: 0 0 auto;
  padding: 6px 12px;
  border-radius: var(--r-md);
  background: rgb(255 255 255 / 0.8);
  border: 1px solid #fedf89;
  transition: all var(--dur-fast) var(--ease-out-quart);
}

.attention-link:hover {
  background: #fff;
}

/* ==================== 图表 ==================== */
.charts {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--sp-4);
}

.chart-card {
  display: flex;
  flex-direction: column;
  padding: var(--sp-5);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
}

.chart-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--sp-4);
  margin-bottom: var(--sp-3);
}

.chart-title {
  font-size: var(--fs-14);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.01em;
}

.chart-sub {
  font-size: var(--fs-12);
  color: var(--text-faint);
  margin-top: 2px;
}

.chart-total {
  font-size: var(--fs-22);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.03em;
  line-height: 1;
}

.chart-total-unit {
  font-family: var(--font-sans);
  font-size: var(--fs-12);
  font-weight: 500;
  color: var(--text-faint);
  margin-left: 3px;
}

.chart {
  width: 100%;
}

/* ==================== 漏斗 ==================== */
.funnel {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  padding-top: var(--sp-2);
}

.funnel-row {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.funnel-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.funnel-label {
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--text-muted);
}

.funnel-value {
  font-size: var(--fs-15);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.02em;
}

.funnel-bar {
  height: 10px;
  border-radius: var(--r-full);
  background: var(--n-100);
  overflow: hidden;
}

.funnel-fill {
  display: block;
  height: 100%;
  border-radius: var(--r-full);
  transition: width var(--dur-slow) var(--ease-out-quart);
}

.funnel-fill.total {
  background: linear-gradient(90deg, var(--n-300), var(--n-400));
}

.funnel-fill.accepted {
  background: linear-gradient(90deg, var(--brand-400), var(--brand-600));
}

.funnel-conv {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: var(--sp-4);
  border-radius: var(--r-md);
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
}

.conv-pct {
  font-size: var(--fs-28);
  font-weight: 700;
  color: var(--brand-600);
  letter-spacing: -0.035em;
  line-height: 1;
}

.conv-label {
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--brand-700);
}

.funnel-note {
  font-size: var(--fs-12);
  line-height: 1.65;
  color: var(--text-faint);
}

.funnel-note b {
  color: var(--text-muted);
  font-weight: 700;
}

@media (max-width: 900px) {
  .charts {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .chart-card {
    padding: var(--sp-4);
  }

  /* 告警条在手机上竖排，否则文字被挤成很窄的一列 */
  .attention {
    flex-wrap: wrap;
    gap: var(--sp-3);
  }

  .attention-copy {
    flex: 1 1 100%;
    order: 2;
  }

  .attention-icon {
    order: 1;
  }

  .attention-link {
    order: 3;
    width: 100%;
    justify-content: center;
  }

  .conv-pct {
    font-size: var(--fs-22);
  }
}
</style>
