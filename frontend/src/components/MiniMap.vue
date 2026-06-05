<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { loadAMap } from '../utils/amap'
import { distanceMeters } from '../utils/geo'
import { formatDistance } from '../utils/format'

/**
 * 态势地图（高德地图 JS API 2.0 真实底图）。
 *
 * 用户下单、订单追踪、骑手工作台、调度后台四个页面共用本组件，通过 props 与
 * 事件对外暴露能力：
 *   - pickup / deliver / rider：{lng,lat}，分别渲染成 取件(绿) / 送达(橙) / 骑手(蓝) 标记
 *   - track：[{lng,lat}] 骑手历史轨迹折线
 *   - pickable + @pick：点击地图选点，回传 {lng,lat}
 *
 * 实现要点：
 *  1. SDK 经 utils/amap 单例加载（Key / 安全密钥放在 .env.local），整个应用只加载一次。
 *  2. 地图只在 onMounted 创建一次；骑手位置、轨迹通过 WebSocket 高频更新时，只调用
 *     marker.setPosition / polyline.setPath，不重建地图（重建会闪烁、卡顿）。
 *  3. 视野只在「换单」（取件 / 送达坐标变化）时 setFitView 一次；骑手移动、轨迹增长
 *     不再重新缩放，避免画面跟着骑手频繁抖动。
 *  4. 高德坐标系为 GCJ-02；本项目使用北京城区模拟坐标，可直接上图。将来接入真实
 *     手机 GPS（WGS-84）时，需要先纠偏再 setPosition。
 */
const props = defineProps({
  pickup: { type: Object, default: null },
  deliver: { type: Object, default: null },
  rider: { type: Object, default: null },
  /** 骑手轨迹点 [{lng, lat}] */
  track: { type: Array, default: () => [] },
  /** 订单状态：决定提示文字是「距取件点」还是「距送达点」 */
  status: { type: String, default: '' },
  height: { type: Number, default: 240 },
  /** 底图明暗：map = 深色态势图；plain = 浅色（嵌在浅色区域里时用） */
  theme: { type: String, default: 'map' },
  /** 是否在角落显示经纬度读数：调度后台需要，用户端 / 骑手端默认隐藏 */
  showCoords: { type: Boolean, default: false },
  /** 选点模式：地图可点击，点击后通过 pick 事件回传经纬度 */
  pickable: { type: Boolean, default: false },
  /** 选点模式的初始经纬度窗口 {minLng,maxLng,minLat,maxLat} */
  bounds: { type: Object, default: null },
  /** 选点模式底部引导文案，例如「点击地图选择取件点」 */
  pickHint: { type: String, default: '' },
})

const emit = defineEmits(['pick'])

const root = ref(null)
const mapEl = ref(null)
const ready = ref(false)
const loadError = ref(false)

// 地图实例与覆盖物不需要响应式（高德自己管理 DOM），用普通变量持有
let map = null
let mkPick = null
let mkDeliver = null
let mkRider = null
let plTrack = null
let plRoute = null
let ro = null
let fitTimer = null
let fittedKey = ''
let riderCentered = false
let lastPick = null
let lastDeliver = null
let lastRider = null
let lastW = 0
let lastH = 0

// 自定义标记的 HTML（圆点中心对准坐标，标签浮在上方）。样式见文件末尾的非 scoped 块。
const PICK_HTML =
  '<div class="sd-mk sd-mk-pick"><span class="sd-mk-ring"></span><span class="sd-mk-dot"></span><span class="sd-mk-tag">取件</span></div>'
const DELIVER_HTML =
  '<div class="sd-mk sd-mk-deliver"><span class="sd-mk-ring"></span><span class="sd-mk-dot"></span><span class="sd-mk-tag">送达</span></div>'
const RIDER_HTML =
  '<div class="sd-mk sd-mk-rider"><span class="sd-rider-ping"></span><span class="sd-rider-dot"></span><span class="sd-mk-tag">骑手</span></div>'

/** 无任何定位信息时的兜底中心（北京天安门附近） */
const DEFAULT_CENTER = [116.4074, 39.915]

const h = computed(() => props.height)

function toLL(p) {
  if (!p || p.lng == null || p.lat == null) return null
  const lng = Number(p.lng)
  const lat = Number(p.lat)
  if (Number.isNaN(lng) || Number.isNaN(lat)) return null
  return [lng, lat]
}

/* ---------------- HUD 数据（与底图无关，距离仍用真实球面公式） ---------------- */

/** 骑手到「当前目标」的直线距离：待取货看取件点，配送中看送达点 */
const riderToTarget = computed(() => {
  if (!props.rider) return null
  const target =
    props.status === 'DELIVERING'
      ? props.deliver
      : props.status === 'PENDING_PICKUP'
        ? props.pickup
        : null
  if (!target) return null
  return distanceMeters(
    Number(props.rider.lng),
    Number(props.rider.lat),
    Number(target.lng),
    Number(target.lat),
  )
})

const targetLabel = computed(() => (props.status === 'DELIVERING' ? '送达点' : '取件点'))

/** 还没接到骑手：提示「正在匹配骑手」。选点模式（下单页）订单尚未创建，不扫描 */
const scanning = computed(() => !props.pickable && !props.rider && !!props.pickup)

const coordReadout = computed(() => {
  const p = props.rider || props.pickup
  if (!p) return null
  return `${Number(p.lng).toFixed(5)}, ${Number(p.lat).toFixed(5)}`
})

const showEmpty = computed(
  () => ready.value && !props.pickable && !props.pickup && !props.deliver && !props.rider,
)

/* ---------------- 初始化地图 ---------------- */

function initMap(AMap) {
  const dark = props.theme !== 'plain'
  map = new AMap.Map(mapEl.value, {
    viewMode: '2D',
    zoom: 13,
    center: DEFAULT_CENTER,
    mapStyle: dark ? 'amap://styles/dark' : 'amap://styles/normal',
    dragEnable: true,
    zoomEnable: true,
    // 选点模式关掉双击缩放，否则双击会被浏览器拆成两次 click 而误选两个点
    doubleClickZoom: !props.pickable,
    jogEnable: false, // 鼠标移到边缘时地图自动漂移，选点时会干扰
    keyboardEnable: false,
    showLabel: true, // 显示道路 / POI 名称，这是真实地图的核心
  })

  mkPick = new AMap.Marker({ content: PICK_HTML, anchor: 'center', zIndex: 130, visible: false })
  mkDeliver = new AMap.Marker({ content: DELIVER_HTML, anchor: 'center', zIndex: 130, visible: false })
  mkRider = new AMap.Marker({ content: RIDER_HTML, anchor: 'center', zIndex: 140, visible: false })

  // 取件 → 送达 的参考航线（淡虚线）
  plRoute = new AMap.Polyline({
    strokeColor: dark ? '#94a3b8' : '#64748b',
    strokeWeight: 2,
    strokeOpacity: dark ? 0.45 : 0.5,
    strokeStyle: 'dashed',
    strokeDasharray: [6, 6],
    lineJoin: 'round',
    lineCap: 'round',
    zIndex: 60,
  })
  // 骑手实际轨迹（蓝色实线）
  plTrack = new AMap.Polyline({
    strokeColor: '#0ea5e9',
    strokeWeight: 5,
    strokeOpacity: 0.85,
    lineJoin: 'round',
    lineCap: 'round',
    showDir: false,
    zIndex: 90,
  })

  map.add([plRoute, plTrack, mkPick, mkDeliver, mkRider])

  if (props.pickable) {
    map.on('click', (e) => {
      const lng = e.lnglat.getLng()
      const lat = e.lnglat.getLat()
      emit('pick', { lng: Number(lng.toFixed(6)), lat: Number(lat.toFixed(6)) })
    })
  }

  ready.value = true

  // 选点模式：把初始视野锁定到固定城区窗口；展示模式交给 syncOverlays 自适应
  if (props.pickable && props.bounds) {
    const b = props.bounds
    map.setBounds(new AMap.Bounds([b.minLng, b.minLat], [b.maxLng, b.maxLat]), false, [24, 24, 24, 24])
  }
  nextTick(() => {
    syncOverlays()
    // 挂载时卡片 / 抽屉可能还在布局，下一帧再让地图按真实容器尺寸重算一次，避免首屏停在空网格
    requestAnimationFrame(() => setTimeout(refreshSize, 200))
  })
}

/* ---------------- 同步覆盖物（高频更新只走这里） ---------------- */

function setMarker(mk, ll) {
  if (ll) {
    mk.setPosition(ll)
    mk.show()
  } else {
    mk.hide()
  }
}

function syncOverlays() {
  if (!map || !ready.value) return

  lastPick = toLL(props.pickup)
  lastDeliver = toLL(props.deliver)
  lastRider = toLL(props.rider)
  setMarker(mkPick, lastPick)
  setMarker(mkDeliver, lastDeliver)
  setMarker(mkRider, lastRider)

  const trail = (props.track || []).map(toLL).filter(Boolean)
  if (trail.length >= 2) {
    plTrack.setPath(trail)
    plTrack.show()
  } else {
    plTrack.hide()
  }

  if (lastPick && lastDeliver) {
    plRoute.setPath([lastPick, lastDeliver])
    plRoute.show()
  } else {
    plRoute.hide()
  }

  updateViewport()
}

/**
 * 视野策略：只在「换单」（取件 / 送达坐标变化）时适配一次；
 * 骑手移动、轨迹增长不重新缩放，避免画面一直跳。
 */
function updateViewport() {
  if (props.pickable) return

  const key = `${lastPick ? lastPick.join(',') : '-'}|${lastDeliver ? lastDeliver.join(',') : '-'}`

  if (lastPick || lastDeliver) {
    if (key !== fittedKey) {
      fittedKey = key
      scheduleFit()
    }
    return
  }

  // 没有取送点（骑手空闲）：首次把镜头居中到骑手自己
  if (fittedKey !== '-|-') {
    fittedKey = '-|-'
    riderCentered = false
  }
  if (lastRider && !riderCentered) {
    riderCentered = true
    map.setZoomAndCenter(15, lastRider, false)
  }
}

function scheduleFit() {
  if (props.pickable || !map) return
  clearTimeout(fitTimer)
  // 稍作延迟：调度后台抽屉有展开动画，等容器尺寸稳定后再适配，避免算到 0 宽高
  fitTimer = setTimeout(() => {
    const vis = []
    if (lastPick) vis.push(mkPick)
    if (lastDeliver) vis.push(mkDeliver)
    if (lastRider) vis.push(mkRider)
    if (vis.length) map.setFitView(vis, false, [72, 72, 72, 72], 17)
  }, 90)
}

/**
 * 让地图按当前容器尺寸重新计算视口。
 * 高德 JS API 2.0 提供了 resize()；若个别小版本没有，则用 setCenter 兜底触发重绘。
 * 只重绘、不改视野，所以响应式断点变化 / 抽屉展开时调用是安全的，不会打断用户拖动。
 */
function refreshSize() {
  if (!map) return
  if (typeof map.resize === 'function') map.resize()
  else map.setCenter(map.getCenter())
}

let resizeTimer = null
function scheduleRefreshSize() {
  if (!map) return
  clearTimeout(resizeTimer)
  resizeTimer = setTimeout(refreshSize, 180)
}

watch(
  () => [props.pickup, props.deliver, props.rider, props.track, props.status],
  () => syncOverlays(),
  { deep: true },
)

/* ---------------- 容器尺寸（响应式断点 / 抽屉展开 / 挂载布局抖动） ---------------- */

function setupResizeObserver() {
  if (!root.value) return
  ro = new ResizeObserver(([entry]) => {
    const { width: w, height: hh } = entry.contentRect
    if (w <= 0 || hh <= 0) return
    const wasHidden = lastW === 0 || lastH === 0
    const changed = w !== lastW || hh !== lastH
    if (map && changed) {
      // 容器真实尺寸变化（断点切换、抽屉展开、挂载时布局稳定）后让地图重算，避免停在空网格
      scheduleRefreshSize()
      // 从隐藏变为可见（后台抽屉首次展开）时，再适配一次视野
      if (wasHidden) scheduleFit()
    }
    lastW = w
    lastH = hh
  })
  ro.observe(root.value)
}

onMounted(() => {
  setupResizeObserver()
  loadAMap()
    .then(initMap)
    .catch((err) => {
      console.error('[MiniMap] 高德地图加载失败：', err)
      loadError.value = true
    })
})

onUnmounted(() => {
  clearTimeout(fitTimer)
  clearTimeout(resizeTimer)
  ro?.disconnect()
  if (map) {
    map.destroy()
    map = null
  }
})
</script>

<template>
  <div ref="root" class="map" :class="[`theme-${theme}`, { pickable }]" :style="{ height: h + 'px' }">
    <div ref="mapEl" class="amap-host"></div>

    <!-- 加载 / 失败 / 空态遮罩 -->
    <div v-if="loadError" class="map-mask">
      <div>地图加载失败</div>
      <div class="mask-sub">请检查网络或地图 Key 配置后刷新</div>
    </div>
    <div v-else-if="!ready" class="map-mask">地图加载中…</div>
    <div v-else-if="showEmpty" class="map-mask faint">暂无位置数据</div>

    <!-- HUD 浮层（地图就绪后才显示） -->
    <template v-if="ready">
      <div v-if="showCoords && coordReadout" class="hud hud-tr num">{{ coordReadout }}</div>

      <div v-if="riderToTarget != null" class="hud hud-bl">
        <span class="hud-dot"></span>
        距{{ targetLabel }}
        <b class="num">{{ formatDistance(riderToTarget) }}</b>
      </div>
      <div v-else-if="pickable && pickHint" class="hud hud-bl">
        <span class="hud-dot picking"></span>
        {{ pickHint }}
      </div>
      <div v-else-if="scanning" class="hud hud-bl">
        <span class="hud-dot scanning"></span>
        正在匹配骑手
      </div>
    </template>
  </div>
</template>

<style scoped>
.map {
  position: relative;
  width: 100%;
  border-radius: var(--r-lg);
  overflow: hidden;
  border: 1px solid #1e283e;
  background: #0b1220;
  isolation: isolate;
}

.theme-plain {
  background: var(--n-50);
  border-color: var(--border);
}

.amap-host {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* 选点模式下地图呈十字光标，明确提示可以点击 */
.map.pickable :deep(.amap-maps) {
  cursor: crosshair;
}

.map-mask {
  position: absolute;
  inset: 0;
  z-index: 200;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px;
  text-align: center;
  font-size: 13px;
  color: #cbd5e1;
  background: #0b1220;
}

.map-mask.faint {
  background: transparent;
  color: rgb(203 213 225 / 0.8);
  pointer-events: none;
}

.mask-sub {
  font-size: 11px;
  color: #94a3b8;
}

.theme-plain .map-mask {
  background: var(--n-50);
  color: var(--text-muted);
}

/* ---------- HUD ---------- */
.hud {
  position: absolute;
  z-index: 200;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: var(--r-full);
  font-size: var(--fs-11);
  line-height: 1;
  color: #cbd5e1;
  background: rgb(11 18 32 / 0.78);
  border: 1px solid rgb(148 163 184 / 0.22);
  backdrop-filter: blur(6px);
  pointer-events: none;
  white-space: nowrap;
  max-width: calc(100% - 20px);
  overflow: hidden;
  text-overflow: ellipsis;
}

.hud b {
  color: #f8fafc;
  font-weight: 700;
}

.hud-tr {
  top: 10px;
  right: 10px;
  letter-spacing: 0.02em;
  color: #94a3b8;
}

.hud-bl {
  left: 10px;
  bottom: 10px;
}

.hud-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #38bdf8;
  flex: 0 0 5px;
}

.hud-dot.picking {
  background: #fb923c;
}

.hud-dot.scanning {
  animation: sd-hud-pulse 1.4s ease-in-out infinite;
  background: #fb923c;
}

@keyframes sd-hud-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

.theme-plain .hud {
  background: rgb(255 255 255 / 0.9);
  border-color: var(--border);
  color: var(--text-muted);
}

.theme-plain .hud b {
  color: var(--text-strong);
}
</style>

<!-- 非 scoped：高德 Marker 的 content 是运行时注入的原生 DOM，scoped 样式作用不到，
     所以标记样式必须放全局，统一用 sd- 前缀避免污染其它组件。 -->
<style>
.sd-mk {
  position: relative;
  width: 0;
  height: 0;
  overflow: visible;
}

.sd-mk-dot {
  position: absolute;
  left: -8px;
  top: -8px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 2px solid #fff;
  box-sizing: border-box;
  box-shadow: 0 0 6px rgb(0 0 0 / 0.35);
}

.sd-mk-ring {
  position: absolute;
  left: -15px;
  top: -15px;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 2px solid currentColor;
  opacity: 0.35;
}

.sd-mk-tag {
  position: absolute;
  left: 0;
  top: -34px;
  transform: translateX(-50%);
  white-space: nowrap;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  padding: 3px 7px;
  border-radius: 9px;
  color: #fff;
  box-shadow: 0 2px 6px rgb(0 0 0 / 0.3);
}

/* 取件：绿 */
.sd-mk-pick {
  color: #059669;
}
.sd-mk-pick .sd-mk-dot {
  background: #34d399;
}
.sd-mk-pick .sd-mk-tag {
  background: #059669;
}

/* 送达：橙 */
.sd-mk-deliver {
  color: #ea580c;
}
.sd-mk-deliver .sd-mk-dot {
  background: #fb923c;
}
.sd-mk-deliver .sd-mk-tag {
  background: #ea580c;
}

/* 骑手：蓝白圆点 + 脉冲圈 */
.sd-rider-dot {
  position: absolute;
  left: -9px;
  top: -9px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  border: 3px solid #0284c7;
  box-sizing: border-box;
  box-shadow: 0 0 8px rgb(56 189 248 / 0.8);
}

.sd-rider-ping {
  position: absolute;
  left: -12px;
  top: -12px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgb(2 132 199 / 0.35);
  animation: sd-rider-ping 1.8s ease-out infinite;
}

.sd-mk-rider .sd-mk-tag {
  background: #0284c7;
}

@keyframes sd-rider-ping {
  0% {
    transform: scale(0.5);
    opacity: 0.8;
  }
  80%,
  100% {
    transform: scale(2.2);
    opacity: 0;
  }
}
</style>
