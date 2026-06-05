import AMapLoader from '@amap/amap-jsapi-loader'

/**
 * 高德地图 JS API 统一加载器（单例）。
 *
 * Key 与安全密钥放在 .env.local（VITE_AMAP_KEY / VITE_AMAP_SECURITY），
 * 不写死在组件里。整个应用只异步加载一次地图 SDK，多个 MiniMap 实例复用
 * 同一个 Promise，避免重复插入 <script>。
 *
 * 坐标系：高德底图使用 GCJ-02（火星坐标）。本项目骑手/订单坐标是北京城区的
 * 模拟坐标，可直接上图；将来若接入真实手机 GPS（WGS-84），需要先做
 * WGS-84 → GCJ-02 纠偏再 setPosition。
 */

const AMAP_KEY = import.meta.env.VITE_AMAP_KEY
const AMAP_SECURITY = import.meta.env.VITE_AMAP_SECURITY

let amapPromise = null

export function loadAMap() {
  if (amapPromise) return amapPromise

  // 安全密钥必须在 AMapLoader.load 之前挂到 window（高德 JS API 2.0 强制要求）
  window._AMapSecurityConfig = {
    securityJsCode: AMAP_SECURITY,
  }

  amapPromise = AMapLoader.load({
    key: AMAP_KEY,
    version: '2.0',
    // 不加载工具条/比例尺插件，保持画面干净；距离由业务自己的 HUD 浮层显示
    plugins: [],
  })

  return amapPromise
}
