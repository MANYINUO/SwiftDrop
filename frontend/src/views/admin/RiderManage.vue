<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { adminApi } from '@/api'
import { toastError } from '@/api/http'
import { formatTime, fromNow } from '@/utils/format'
import AppIcon from '@/components/AppIcon.vue'
import MetricCard from '@/components/MetricCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const loading = ref(false)
const riders = ref([])
const onlyOnline = ref(false)

const list = computed(() =>
  onlyOnline.value ? riders.value.filter((r) => r.online) : riders.value,
)

const onlineCount = computed(() => riders.value.filter((r) => r.online).length)
const busyCount = computed(() =>
  riders.value.filter((r) => (r.deliveringCount ?? 0) > 0).length,
)
const totalLoad = computed(() =>
  riders.value.reduce((s, r) => s + (r.deliveringCount ?? 0), 0),
)

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    riders.value = await adminApi.riders()
  } catch (e) {
    if (!silent) toastError(e)
  } finally {
    if (!silent) loading.value = false
  }
}

function loadPercent(r) {
  const cap = r.maxCapacity || 1
  return Math.min(100, Math.round(((r.deliveringCount ?? 0) / cap) * 100))
}

let timer = null
onMounted(() => {
  load()
  timer = setInterval(() => load(true), 5000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="rm">
    <header class="page-head">
      <div class="page-head-titles">
        <span class="eyebrow">运力管理</span>
        <h1>骑手管理</h1>
        <p class="page-head-desc">查看骑手在线状态、当前负载与实时位置。</p>
      </div>
      <div class="page-actions">
        <label class="check" :class="{ on: onlyOnline }">
          <input v-model="onlyOnline" type="checkbox" />
          <span class="check-box">
            <AppIcon v-if="onlyOnline" name="check" :size="11" :stroke-width="3" />
          </span>
          只看在线
        </label>
        <el-button size="small" :loading="loading" @click="load(false)">
          <AppIcon name="refresh" :size="14" :stroke-width="2.2" />
          刷新
        </el-button>
      </div>
    </header>

    <!-- ==================== 概览 ==================== -->
    <div class="metric-grid">
      <MetricCard label="骑手总数" :value="riders.length" unit="人" icon="users" />
      <MetricCard label="在线" :value="onlineCount" unit="人" accent="ok" icon="wifi" />
      <MetricCard label="忙碌中" :value="busyCount" unit="人" accent="warn" icon="scooter" />
      <MetricCard label="在手订单合计" :value="totalLoad" unit="单" accent="brand" icon="box" />
    </div>

    <!-- ==================== 骑手卡 ==================== -->
    <div v-if="loading && riders.length === 0" class="rider-grid">
      <div v-for="n in 4" :key="n" class="rider-card">
        <div class="skeleton" style="height: 40px; width: 40px; border-radius: 50%"></div>
        <div class="skeleton" style="height: 12px; width: 60%; margin-top: 14px"></div>
        <div class="skeleton" style="height: 10px; width: 100%; margin-top: 16px"></div>
      </div>
    </div>

    <EmptyState
      v-else-if="list.length === 0"
      icon="users"
      title="没有骑手"
      :description="onlyOnline ? '当前没有骑手在线。' : '暂无骑手信息。'"
    />

    <div v-else class="rider-grid">
      <article
        v-for="(r, i) in list"
        :key="r.riderId"
        class="rider-card"
        :data-online="r.online"
        :style="{ '--i': i }"
      >
        <!-- 头部 -->
        <div class="rc-head">
          <span class="rc-avatar">{{ (r.name || '?').slice(0, 1) }}</span>
          <div class="rc-id">
            <div class="rc-name">{{ r.name }}</div>
            <div class="rc-no num">{{ r.riderNo }}</div>
          </div>
          <div class="rc-tags">
            <span class="tag" :data-kind="r.online ? 'online' : 'offline'">
              <span v-if="r.online" class="live-dot"></span>
              {{ r.online ? '在线' : '离线' }}
            </span>
            <span class="tag" :data-kind="r.busy ? 'busy' : 'idle'">
              {{ r.busy ? '忙碌' : '空闲' }}
            </span>
          </div>
        </div>

        <!-- 负载 -->
        <div class="rc-load">
          <div class="rc-load-top">
            <span class="rc-load-label">在手负载</span>
            <span class="rc-load-count num">
              {{ r.deliveringCount ?? 0 }}<span class="rc-load-cap">/{{ r.maxCapacity ?? '—' }}</span>
            </span>
          </div>
          <div class="rc-bar">
            <span
              class="rc-fill"
              :style="{ width: loadPercent(r) + '%' }"
              :data-full="loadPercent(r) >= 100"
            ></span>
          </div>
        </div>

        <!-- 明细 -->
        <dl class="rc-meta">
          <div class="rc-row">
            <dt><AppIcon name="trending" :size="12" /> 评分</dt>
            <dd class="num score">{{ r.score ?? '—' }}</dd>
          </div>
          <div class="rc-row">
            <dt><AppIcon name="pin" :size="12" /> 实时位置</dt>
            <dd>
              <span v-if="r.lng && r.lat" class="num">
                {{ Number(r.lng).toFixed(5) }}, {{ Number(r.lat).toFixed(5) }}
              </span>
              <span v-else class="faint">暂无位置</span>
            </dd>
          </div>
          <div class="rc-row">
            <dt><AppIcon name="clock" :size="12" /> 位置更新</dt>
            <dd>
              <span v-if="r.lastReportAt" class="num">{{ fromNow(r.lastReportAt) }}</span>
              <span v-else class="faint">—</span>
            </dd>
          </div>
          <div class="rc-row">
            <dt><AppIcon name="phone" :size="12" /> 手机号</dt>
            <dd class="num">{{ r.phone || '—' }}</dd>
          </div>
        </dl>

        <div v-if="r.lastReportAt" class="rc-foot num">
          精确时间 {{ formatTime(r.lastReportAt) }}
        </div>
      </article>
    </div>

    <!-- ==================== 说明 ==================== -->
    <div class="note">
      <span class="note-icon">
        <AppIcon name="database" :size="16" :stroke-width="1.9" />
      </span>
      <div class="note-copy">
        <div class="note-title">负载说明</div>
        <p class="note-text">
          「负载」为骑手在手订单数与最大接单量的比值，<b>满载骑手暂不参与派单</b>；
          位置随骑手端实时更新。
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rm {
  display: flex;
  flex-direction: column;
  gap: var(--sp-5);
}

/* ---------- 只看在线 ---------- */
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

/* ==================== 骑手卡网格 ==================== */
.rider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(296px, 1fr));
  gap: var(--sp-4);
}

.rider-card {
  display: flex;
  flex-direction: column;
  padding: var(--sp-4);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  position: relative;
  overflow: hidden;
  transition: border-color var(--dur) var(--ease-out-quart),
    box-shadow var(--dur) var(--ease-out-quart),
    transform var(--dur) var(--ease-out-quart);
}

/* 入场淡入为渐进增强：默认可见，仅在动画时间轴正常推进时错位淡入 */
@media (prefers-reduced-motion: no-preference) {
  html.motion .rider-card {
    animation: rise-in var(--dur-slow) var(--ease-out-quart) both;
    animation-delay: calc(var(--i) * 55ms);
  }
}

/* 在线骑手左侧一条绿色指示 */
.rider-card[data-online='true']::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--ok);
}

.rider-card:hover {
  border-color: var(--border-strong);
  box-shadow: var(--sh-md);
  transform: translateY(-2px);
}

/* ---------- 头部 ---------- */
.rc-head {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.rc-avatar {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  border-radius: var(--r-full);
  display: grid;
  place-items: center;
  font-size: var(--fs-15);
  font-weight: 700;
  color: var(--text-muted);
  background: var(--n-100);
  border: 1px solid var(--border);
  transition: all var(--dur) var(--ease-out-quart);
}

.rider-card[data-online='true'] .rc-avatar {
  color: #fff;
  background: linear-gradient(150deg, #34d399, #059669);
  border-color: transparent;
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 0.28);
}

.rc-id {
  flex: 1;
  min-width: 0;
}

.rc-name {
  font-size: var(--fs-14);
  font-weight: 700;
  color: var(--text-strong);
  letter-spacing: -0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rc-no {
  font-size: var(--fs-11);
  color: var(--text-faint);
}

.rc-tags {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex: 0 0 auto;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 7px;
  border-radius: var(--r-full);
  border: 1px solid var(--border);
  background: var(--n-100);
  color: var(--text-muted);
  white-space: nowrap;
}

.tag[data-kind='online'] {
  color: var(--ok);
  background: var(--ok-bg);
  border-color: #abefc6;
}

.tag[data-kind='busy'] {
  color: var(--warn);
  background: var(--warn-bg);
  border-color: #fedf89;
}

/* ---------- 负载 ---------- */
.rc-load {
  margin-bottom: var(--sp-4);
}

.rc-load-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6px;
}

.rc-load-label {
  font-size: var(--fs-11);
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.rc-load-count {
  font-size: var(--fs-15);
  font-weight: 700;
  color: var(--text-strong);
}

.rc-load-cap {
  font-size: var(--fs-11);
  font-weight: 500;
  color: var(--text-faint);
}

.rc-bar {
  height: 6px;
  border-radius: var(--r-full);
  background: var(--n-100);
  overflow: hidden;
}

.rc-fill {
  display: block;
  height: 100%;
  border-radius: var(--r-full);
  background: linear-gradient(90deg, var(--brand-300), var(--brand-500));
  transition: width var(--dur-slow) var(--ease-out-quart);
}

.rc-fill[data-full='true'] {
  background: linear-gradient(90deg, #f97066, var(--danger));
}

/* ---------- 明细 ---------- */
.rc-meta {
  display: flex;
  flex-direction: column;
  padding-top: var(--sp-3);
  border-top: 1px solid var(--border-subtle);
}

.rc-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: 5px 0;
  min-width: 0;
}

.rc-row dt {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--fs-11);
  color: var(--text-faint);
  flex: 0 0 auto;
}

.rc-row dd {
  margin: 0;
  font-size: var(--fs-12);
  font-weight: 600;
  color: var(--text);
  text-align: right;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rc-row dd.score {
  color: var(--warn);
}

.rc-foot {
  margin-top: var(--sp-3);
  padding-top: var(--sp-3);
  border-top: 1px dashed var(--border-subtle);
  font-size: 10px;
  color: var(--text-faint);
}

/* ==================== 说明 ==================== */
.note {
  display: flex;
  gap: var(--sp-3);
  padding: var(--sp-4);
  border-radius: var(--r-lg);
  background: var(--info-bg);
  border: 1px solid #b2ddff;
}

.note-icon {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  border-radius: var(--r-md);
  display: grid;
  place-items: center;
  background: rgb(255 255 255 / 0.7);
  color: var(--info);
  border: 1px solid #b2ddff;
}

.note-title {
  font-size: var(--fs-13);
  font-weight: 700;
  color: #194185;
  margin-bottom: 3px;
}

.note-text {
  font-size: var(--fs-12);
  line-height: 1.7;
  color: #175cd3;
}

/* 只强调行内的关键词，不能加 display: block —— 否则整段会被拆成多行 */
.note-text b {
  font-weight: 700;
  color: #194185;
}

.note-text code {
  font-family: var(--num-font);
  font-size: var(--fs-11);
  padding: 1px 5px;
  border-radius: var(--r-xs);
  background: rgb(255 255 255 / 0.75);
  border: 1px solid #b2ddff;
}
</style>
