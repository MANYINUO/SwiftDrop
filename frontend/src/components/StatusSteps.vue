<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import { ORDER_STATUS, STATUS_FLOW, statusHint } from '../utils/format'

/**
 * 订单状态步骤条（自研，替代 el-steps）。
 *
 * 相比 Element 的步骤条：
 *  - 节点更小、连线更细，在 400px 的手机壳里 5 个环节也能放下
 *  - 当前节点带脉冲环，一眼看出"停在哪一步"
 *  - 取消 / 异常不在主流程里，整条压暗并单独给一条说明
 */
const props = defineProps({
  status: { type: String, default: '' },
  /** 是否显示当前状态的一句话说明 */
  showHint: { type: Boolean, default: true },
})

const steps = computed(() =>
  STATUS_FLOW.map((key) => ({ key, label: ORDER_STATUS[key].label })),
)

const abnormal = computed(() => ['CANCELLED', 'EXCEPTION'].includes(props.status))

const active = computed(() => {
  if (abnormal.value) return -1
  const idx = STATUS_FLOW.indexOf(props.status)
  return idx < 0 ? 0 : idx
})

function stateOf(i) {
  if (abnormal.value) return 'frozen'
  if (i < active.value) return 'done'
  if (i === active.value) return 'current'
  return 'todo'
}

const hint = computed(() => statusHint(props.status))
</script>

<template>
  <div class="steps" :class="{ abnormal }">
    <ol class="steps-track">
      <li
        v-for="(s, i) in steps"
        :key="s.key"
        class="step"
        :class="stateOf(i)"
      >
        <span class="node">
          <AppIcon v-if="stateOf(i) === 'done'" name="check" :size="12" :stroke-width="2.6" />
          <span v-else-if="stateOf(i) === 'current'" class="dot" />
          <span v-else class="idx num">{{ i + 1 }}</span>
        </span>
        <span class="label">{{ s.label }}</span>
      </li>
    </ol>

    <p v-if="abnormal && showHint" class="hint hint-abnormal">
      <AppIcon :name="status === 'CANCELLED' ? 'ban' : 'alert'" :size="13" :stroke-width="2" />
      {{ hint }}
    </p>
    <p v-else-if="showHint && hint" class="hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
.steps {
  --node: 22px;
  --rail: 2px;
}

.steps-track {
  display: flex;
  align-items: flex-start;
  margin: 0;
}

.step {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 7px;
}

/* ---------- 连线 ---------- */
.step::before,
.step::after {
  content: '';
  position: absolute;
  top: calc(var(--node) / 2 - var(--rail) / 2);
  height: var(--rail);
  background: var(--n-200);
  border-radius: var(--rail);
  transition: background-color var(--dur-slow) var(--ease-out-quart);
}

.step::before {
  left: 0;
  right: 50%;
  margin-right: calc(var(--node) / 2 + 2px);
}

.step::after {
  left: 50%;
  right: 0;
  margin-left: calc(var(--node) / 2 + 2px);
}

.step:first-child::before,
.step:last-child::after {
  display: none;
}

.step.done::after,
.step.current::before {
  background: var(--brand);
}

/* ---------- 节点 ---------- */
.node {
  position: relative;
  z-index: 1;
  width: var(--node);
  height: var(--node);
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--bg-surface);
  border: 1px solid var(--n-300);
  color: var(--text-faint);
  transition: background-color var(--dur) var(--ease-out-quart),
    border-color var(--dur) var(--ease-out-quart),
    color var(--dur) var(--ease-out-quart);
}

.idx {
  font-size: 10px;
  font-weight: 700;
}

.step.done .node {
  background: var(--brand);
  border-color: var(--brand);
  color: #fff;
}

.step.current .node {
  background: var(--bg-surface);
  border: 2px solid var(--brand);
}

.step.current .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand);
}

/* 当前节点外的脉冲环 */
.step.current .node::after {
  content: '';
  position: absolute;
  inset: -5px;
  border-radius: 50%;
  border: 1.5px solid var(--brand);
  opacity: 0;
  animation: ring-pulse 2.2s var(--ease-out-quart) infinite;
}

@keyframes ring-pulse {
  0% {
    opacity: 0.5;
    transform: scale(0.82);
  }
  70%,
  100% {
    opacity: 0;
    transform: scale(1.18);
  }
}

/* ---------- 标签 ---------- */
.label {
  font-size: var(--fs-11);
  font-weight: 500;
  color: var(--text-faint);
  text-align: center;
  line-height: 1.3;
  transition: color var(--dur) var(--ease-out-quart);
}

.step.done .label {
  color: var(--text-muted);
}

.step.current .label {
  color: var(--brand-600);
  font-weight: 700;
}

/* ---------- 异常态 ---------- */
.steps.abnormal .step::before,
.steps.abnormal .step::after {
  background: var(--n-200);
}

.steps.abnormal .node {
  background: var(--n-100);
  border-color: var(--n-300);
  color: var(--n-400);
}

.steps.abnormal .step.current .node::after {
  display: none;
}

/* ---------- 说明 ---------- */
.hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 14px 0 0;
  font-size: var(--fs-12);
  line-height: 1.5;
  color: var(--text-muted);
}

.hint-abnormal {
  padding: 9px 12px;
  border-radius: var(--r-md);
  background: var(--danger-bg);
  border: 1px solid #fecdca;
  color: var(--danger);
  font-weight: 500;
}
</style>
