<script setup>
import AppIcon from './AppIcon.vue'

/**
 * 指标卡。
 *
 * 刻意不用大号色块 + 渐变数字那套模板 —— 这里的数字是要被"读"的，
 * 所以用等宽字形、tabular 数字，右对齐的量纲，标签在上方当眉标。
 */
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], default: '—' },
  unit: { type: String, default: '' },
  hint: { type: String, default: '' },
  /** 强调色：brand / ok / warn / danger / neutral */
  accent: { type: String, default: 'neutral' },
  icon: { type: String, default: '' },
  /** 小号数值（位数多时用） */
  compact: { type: Boolean, default: false },
})
</script>

<template>
  <div class="metric" :data-accent="accent">
    <div class="metric-top">
      <span class="metric-label">{{ label }}</span>
      <span v-if="hint" class="metric-hint" :title="hint">
        <AppIcon name="question" :size="13" />
      </span>
      <AppIcon v-else-if="icon" class="metric-icon" :name="icon" :size="14" />
    </div>

    <div class="metric-value num" :class="{ compact }">
      {{ value }}
      <span v-if="unit" class="metric-unit">{{ unit }}</span>
    </div>
  </div>
</template>

<style scoped>
.metric {
  --accent: var(--text-faint);
  position: relative;
  padding: var(--sp-4);
  padding-left: calc(var(--sp-4) - 2px);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg);
  transition: border-color var(--dur) var(--ease-out-quart),
    box-shadow var(--dur) var(--ease-out-quart),
    transform var(--dur) var(--ease-out-quart);
}

/* 左侧一条强调色小竖条。上下各留 12px 对齐圆角，看起来是有意为之，
   而不是被 overflow 裁掉的一截。 */
.metric::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 2px;
  border-radius: 0 2px 2px 0;
  background: var(--accent);
}

.metric:hover {
  border-color: var(--border-strong);
  box-shadow: var(--sh-sm);
  transform: translateY(-1px);
}

.metric-top {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 10px;
}

.metric-label {
  font-size: var(--fs-11);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-hint {
  color: var(--n-300);
  cursor: help;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  transition: color var(--dur-fast);
}

.metric-hint:hover {
  color: var(--text-muted);
}

.metric-icon {
  margin-left: auto;
  color: var(--n-300);
  flex: 0 0 auto;
}

.metric-value {
  font-size: var(--fs-28);
  font-weight: 700;
  line-height: 1.05;
  color: var(--text-strong);
  letter-spacing: -0.03em;
  display: flex;
  align-items: baseline;
  gap: 3px;
}

.metric-value.compact {
  font-size: var(--fs-18);
  letter-spacing: -0.015em;
}

.metric-unit {
  font-family: var(--font-sans);
  font-size: var(--fs-12);
  font-weight: 500;
  color: var(--text-faint);
  letter-spacing: 0;
}

/* ---------- 强调色 ---------- */
.metric[data-accent='brand'] {
  --accent: var(--brand);
}

.metric[data-accent='brand'] .metric-value {
  color: var(--brand-600);
}

.metric[data-accent='ok'] {
  --accent: var(--ok);
}

.metric[data-accent='ok'] .metric-value {
  color: var(--ok);
}

.metric[data-accent='warn'] {
  --accent: var(--warn);
}

.metric[data-accent='warn'] .metric-value {
  color: var(--warn);
}

.metric[data-accent='danger'] {
  --accent: var(--danger);
}

.metric[data-accent='danger'] .metric-value {
  color: var(--danger);
}

.metric[data-accent='neutral'] {
  --accent: var(--n-300);
}
</style>
