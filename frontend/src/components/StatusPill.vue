<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { statusLabel, statusTone, taskStatusLabel, taskStatusTone } from '../utils/format'

/**
 * 状态徽标。
 *
 * 颜色不是写死的 —— 每个状态对应 tokens.css 里的一组 --st-* 三件套
 * （前景 / 底色 / 描边），换主题只改令牌。
 */
const props = defineProps({
  status: { type: String, default: '' },
  /** order = 订单状态；task = 派单任务状态 */
  kind: { type: String, default: 'order' },
  size: { type: String, default: 'md' },
  /** 显示前置图标 */
  withIcon: { type: Boolean, default: true },
  /** 实心样式（用于强调当前状态） */
  solid: { type: Boolean, default: false },
})

const label = computed(() =>
  props.kind === 'task' ? taskStatusLabel(props.status) : statusLabel(props.status),
)

const tone = computed(() =>
  props.kind === 'task' ? taskStatusTone(props.status) : statusTone(props.status),
)

const icon = computed(() => {
  if (!props.withIcon) return null
  const map = {
    'pending-dispatch': 'hourglass',
    dispatching: 'send',
    'pending-pickup': 'pin',
    delivering: 'scooter',
    delivered: 'check-circle',
    cancelled: 'ban',
    exception: 'alert',
  }
  return map[tone.value] || 'dot'
})

/** 进行中的状态给个呼吸点，一眼看出"还在动" */
const isLive = computed(() => ['dispatching', 'delivering'].includes(tone.value))
</script>

<template>
  <span
    class="pill"
    :class="[`size-${size}`, { solid }]"
    :data-tone="tone"
  >
    <span v-if="isLive" class="pill-live" aria-hidden="true"></span>
    <AppIcon v-else-if="icon" :name="icon" :size="size === 'sm' ? 11 : 13" :stroke-width="2" />
    <span class="pill-label">{{ label }}</span>
  </span>
</template>

<style scoped>
.pill {
  --c: var(--text-muted);
  --bg: var(--n-100);
  --bd: var(--border);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border-radius: var(--r-full);
  border: 1px solid var(--bd);
  background: var(--bg);
  color: var(--c);
  font-weight: 600;
  white-space: nowrap;
  line-height: 1;
  letter-spacing: 0.01em;
}

.size-sm {
  height: 20px;
  padding: 0 8px;
  font-size: var(--fs-11);
}

.size-md {
  height: 24px;
  padding: 0 10px;
  font-size: var(--fs-12);
}

.pill.solid {
  background: var(--c);
  border-color: var(--c);
  color: #fff;
}

/* 呼吸点 */
.pill-live {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c);
  flex: 0 0 6px;
  animation: pulse-dot 1.6s var(--ease-in-out) infinite;
}

.pill.solid .pill-live {
  background: #fff;
}

/* ---------- 色调映射 ---------- */
.pill[data-tone='pending-dispatch'] {
  --c: var(--st-pending-dispatch);
  --bg: var(--st-pending-dispatch-bg);
  --bd: var(--st-pending-dispatch-bd);
}

.pill[data-tone='dispatching'] {
  --c: var(--st-dispatching);
  --bg: var(--st-dispatching-bg);
  --bd: var(--st-dispatching-bd);
}

.pill[data-tone='pending-pickup'] {
  --c: var(--st-pending-pickup);
  --bg: var(--st-pending-pickup-bg);
  --bd: var(--st-pending-pickup-bd);
}

.pill[data-tone='delivering'] {
  --c: var(--st-delivering);
  --bg: var(--st-delivering-bg);
  --bd: var(--st-delivering-bd);
}

.pill[data-tone='delivered'] {
  --c: var(--st-delivered);
  --bg: var(--st-delivered-bg);
  --bd: var(--st-delivered-bd);
}

.pill[data-tone='cancelled'] {
  --c: var(--st-cancelled);
  --bg: var(--st-cancelled-bg);
  --bd: var(--st-cancelled-bd);
}

.pill[data-tone='exception'] {
  --c: var(--st-exception);
  --bg: var(--st-exception-bg);
  --bd: var(--st-exception-bd);
}
</style>
