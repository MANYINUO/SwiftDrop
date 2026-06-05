<script setup>
/**
 * 自研图标集。
 *
 * 为什么不用 Element Plus 的图标：
 *  - 它们尺寸/描边粗细不统一，混在一起会有"拼贴感"
 *  - 图标数量太多（全量注册会拖累首屏）
 *
 * 这里全部基于 24×24 网格、1.75px 描边、round cap/join 手绘，
 * 只用 currentColor，颜色由父级决定。
 */
const props = defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 18 },
  strokeWidth: { type: [Number, String], default: 1.75 },
})

const PATHS = {
  /* ---------- 导航 ---------- */
  grid: '<rect x="3" y="3" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="2"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="2"/>',
  user: '<circle cx="12" cy="8" r="3.6"/><path d="M4.8 20.2c0-3.7 3.2-6.2 7.2-6.2s7.2 2.5 7.2 6.2"/>',
  scooter:
    '<circle cx="5.6" cy="17.2" r="2.6"/><circle cx="18.4" cy="17.2" r="2.6"/><path d="M8.2 17.2h7.6"/><path d="M16.4 5.4h2.7l-2.3 11.8"/><path d="M6.1 17.2 9.2 8.4h5.1"/>',
  gauge:
    '<path d="M3.6 18.4a8.4 8.4 0 1 1 16.8 0"/><path d="M12 18.4 16.2 12"/><circle cx="12" cy="18.4" r="1.5"/>',
  list: '<path d="M8.5 6.5h11.5M8.5 12h11.5M8.5 17.5h11.5"/><circle cx="4.4" cy="6.5" r="1.3"/><circle cx="4.4" cy="12" r="1.3"/><circle cx="4.4" cy="17.5" r="1.3"/>',
  users:
    '<circle cx="9.2" cy="8" r="3.4"/><path d="M2.8 19.8c0-3.4 2.9-5.6 6.4-5.6s6.4 2.2 6.4 5.6"/><path d="M16.2 5.3a3.4 3.4 0 0 1 0 6.4"/><path d="M17.6 14.6c2.2.7 3.6 2.5 3.6 5.2"/>',

  /* ---------- 方向 ---------- */
  'arrow-right': '<path d="M4.5 12h15M13.5 6l6 6-6 6"/>',
  'arrow-left': '<path d="M19.5 12h-15M10.5 18l-6-6 6-6"/>',
  'arrow-up-right': '<path d="M7 17 17 7M8.5 7H17v8.5"/>',
  'chevron-right': '<path d="M9.5 5.5 16 12l-6.5 6.5"/>',
  'chevron-down': '<path d="M5.5 9.5 12 16l6.5-6.5"/>',
  'chevron-left': '<path d="M14.5 5.5 8 12l6.5 6.5"/>',

  /* ---------- 动作 ---------- */
  plus: '<path d="M12 5v14M5 12h14"/>',
  refresh: '<path d="M20.2 11.2a8.2 8.2 0 1 0-2.4 5.9"/><path d="M20.4 4.8v6.6h-6.6"/>',
  check: '<path d="M4.5 12.6l5 5L20 7.2"/>',
  'check-circle': '<circle cx="12" cy="12" r="9"/><path d="M8 12.3l2.9 2.8L16.2 9.4"/>',
  close: '<path d="M6.2 6.2 17.8 17.8M17.8 6.2 6.2 17.8"/>',
  search: '<circle cx="11" cy="11" r="6.6"/><path d="M15.9 15.9 20.5 20.5"/>',
  filter: '<path d="M3.5 5.8h17l-6.8 8v5.9l-3.4-2v-3.9z"/>',
  play: '<path d="M7.4 4.6 19 12 7.4 19.4z" fill="currentColor"/>',
  pause: '<path d="M8.6 4.8v14.4M15.4 4.8v14.4"/>',
  power: '<path d="M12 3.6v8.2"/><path d="M7.4 6.6a7.6 7.6 0 1 0 9.2 0"/>',
  target:
    '<circle cx="12" cy="12" r="8.2"/><circle cx="12" cy="12" r="3.3"/><path d="M12 1.6v3.1M12 19.3v3.1M1.6 12h3.1M19.3 12h3.1"/>',
  sliders:
    '<path d="M3.6 8.2h9.6M18.4 8.2h2M3.6 15.8h4M12.8 15.8h7.6"/><circle cx="15.8" cy="8.2" r="2.4"/><circle cx="10.2" cy="15.8" r="2.4"/>',
  send: '<path d="M20.6 3.4 3.4 10.6l7.2 2.8 2.8 7.2z"/><path d="M20.6 3.4 10.6 13.4"/>',

  /* ---------- 业务对象 ---------- */
  pin: '<path d="M12 21.2s6.6-5.8 6.6-10.7A6.6 6.6 0 0 0 5.4 10.5c0 4.9 6.6 10.7 6.6 10.7z"/><circle cx="12" cy="10.4" r="2.5"/>',
  flag: '<path d="M6.2 21V3.8M6.2 5.2h11.4l-2.1 4.2 2.1 4.2H6.2"/>',
  route:
    '<circle cx="6" cy="18" r="2.6"/><circle cx="18" cy="6" r="2.6"/><path d="M8.6 18h5.4a3.4 3.4 0 0 0 3.4-3.4V8.6"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7.2V12l3.2 2"/>',
  phone:
    '<path d="M6.4 3.8h2.9l1.5 3.7-1.9 1.3a11.4 11.4 0 0 0 5.3 5.3l1.3-1.9 3.7 1.5v2.9a2.1 2.1 0 0 1-2.3 2.1A15.6 15.6 0 0 1 4.3 6.1a2.1 2.1 0 0 1 2.1-2.3z"/>',
  box: '<path d="M20.6 8.3 12 3.4 3.4 8.3v7.4L12 20.6l8.6-4.9z"/><path d="M3.4 8.3 12 13.2l8.6-4.9M12 13.2v7.4"/>',
  zap: '<path d="M13.4 2.8 4.8 13.6h6.2L10.6 21.2l8.6-10.8h-6.2z"/>',
  activity: '<path d="M2.8 12.4h3.6L9 4.6l4.2 14.8 2.6-7h5.4"/>',
  wifi: '<path d="M3.8 9.4a12.4 12.4 0 0 1 16.4 0"/><path d="M7 12.9a8 8 0 0 1 10 0"/><path d="M10.1 16.3a3.6 3.6 0 0 1 3.8 0"/><circle cx="12" cy="19.4" r="1.15" fill="currentColor" stroke="none"/>',
  layers: '<path d="M12 3 3.2 7.6 12 12.2l8.8-4.6z"/><path d="M3.2 12.6 12 17.2l8.8-4.6M3.2 17 12 21.6 21 17"/>',
  shield: '<path d="M12 3 5 6.1v6.1c0 4.3 3 7.8 7 9.2 4-1.4 7-4.9 7-9.2V6.1z"/><path d="M12 9.4v4.1"/><circle cx="12" cy="16.4" r=".9" fill="currentColor" stroke="none"/>',
  history:
    '<path d="M3.6 12a8.4 8.4 0 1 0 2.5-6"/><path d="M3.4 4.6v5.2h5.2"/><path d="M12 8.4V12l3.2 2"/>',
  database:
    '<ellipse cx="12" cy="6" rx="7.6" ry="3"/><path d="M4.4 6v12c0 1.7 3.4 3 7.6 3s7.6-1.3 7.6-3V6"/><path d="M4.4 12c0 1.7 3.4 3 7.6 3s7.6-1.3 7.6-3"/>',
  trending: '<path d="M3.2 16.6 9.2 10l4 3.6L20.8 5.4"/><path d="M15.6 5.4h5.2v5.2"/>',
  speed: '<path d="M12 20.4a8.4 8.4 0 1 1 8.4-8.4"/><path d="M12 12l5.6-3.6"/><path d="M3.6 12H12"/>',

  /* ---------- 状态 ---------- */
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11.2v5.6"/><circle cx="12" cy="7.9" r=".95" fill="currentColor" stroke="none"/>',
  question:
    '<circle cx="12" cy="12" r="9"/><path d="M9.5 9.3a2.6 2.6 0 1 1 3.4 2.5c-.7.25-.9.75-.9 1.4v.4"/><circle cx="12" cy="16.7" r=".92" fill="currentColor" stroke="none"/>',
  alert:
    '<path d="M12 4.2 21.2 19.8H2.8z"/><path d="M12 10v4.3"/><circle cx="12" cy="17.2" r=".92" fill="currentColor" stroke="none"/>',
  'alert-circle':
    '<circle cx="12" cy="12" r="9"/><path d="M12 7.6v5.2"/><circle cx="12" cy="16.4" r=".95" fill="currentColor" stroke="none"/>',
  'x-circle': '<circle cx="12" cy="12" r="9"/><path d="M9.2 9.2 14.8 14.8M14.8 9.2 9.2 14.8"/>',
  ban: '<circle cx="12" cy="12" r="9"/><path d="M5.6 5.6 18.4 18.4"/>',
  hourglass: '<path d="M7 3.5h10M7 20.5h10"/><path d="M8 3.5v3.2c0 2 4 3.6 4 5.3s-4 3.3-4 5.3v3.2"/><path d="M16 3.5v3.2c0 2-4 3.6-4 5.3s4 3.3 4 5.3v3.2"/>',

  /* ---------- 其他 ---------- */
  bolt: '<path d="M13.4 2.8 4.8 13.6h6.2L10.6 21.2l8.6-10.8h-6.2z"/>',
  external: '<path d="M14 4.5h5.5V10"/><path d="M19.5 4.5 11 13"/><path d="M18 14.5v4a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 4 18.5v-11A1.5 1.5 0 0 1 5.5 6h4"/>',
  dot: '<circle cx="12" cy="12" r="4" fill="currentColor" stroke="none"/>',
}

const d = PATHS[props.name] || PATHS.dot
const px = typeof props.size === 'number' ? `${props.size}px` : props.size
</script>

<template>
  <svg
    class="app-icon"
    :width="px"
    :height="px"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
    v-html="d"
  />
</template>

<style scoped>
.app-icon {
  display: block;
  flex: 0 0 auto;
}
</style>
