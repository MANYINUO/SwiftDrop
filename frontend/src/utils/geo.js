/** 地球平均半径（米） */
const R = 6371000

const toRad = (d) => (d * Math.PI) / 180

/** Haversine 球面距离（米） */
export function distanceMeters(lng1, lat1, lng2, lat2) {
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)))
}

/** 线性插值 */
export function lerp(a, b, t) {
  return a + (b - a) * t
}

/** 在两点之间按比例 t（0~1）取一个点 */
export function interpolate(lng1, lat1, lng2, lat2, t) {
  return { lng: lerp(lng1, lng2, t), lat: lerp(lat1, lat2, t) }
}

const DEFAULT_PAD = { top: 48, right: 48, bottom: 48, left: 48 }

/**
 * 把一组经纬度点投影到画布坐标。
 * 自动计算包围盒并加边距，保证所有点都在画布内。
 *
 * 边距支持四边独立设置 —— 因为地图上会浮 HUD（左上角坐标读数、左下角
 * 距离提示），那些位置不能再落标记，否则标签会被压住。
 *
 * @param {Array} points  [{lng, lat}]
 * @param {number} width
 * @param {number} height
 * @param {number|{top?,right?,bottom?,left?}} padding 四边统一给数字，或按边给对象
 * @returns {(p: {lng:number,lat:number}) => {x:number,y:number}} 投影函数
 */
export function createProjector(points, width, height, padding = 48) {
  const pad =
    typeof padding === 'number'
      ? { top: padding, right: padding, bottom: padding, left: padding }
      : { ...DEFAULT_PAD, ...padding }

  const valid = points.filter((p) => p && p.lng != null && p.lat != null)

  if (valid.length === 0) {
    return () => ({ x: width / 2, y: height / 2 })
  }

  let minLng = Infinity
  let maxLng = -Infinity
  let minLat = Infinity
  let maxLat = -Infinity
  for (const p of valid) {
    minLng = Math.min(minLng, p.lng)
    maxLng = Math.max(maxLng, p.lng)
    minLat = Math.min(minLat, p.lat)
    maxLat = Math.max(maxLat, p.lat)
  }

  // 单点或退化情况：给一个最小跨度，避免除零
  const spanLng = Math.max(maxLng - minLng, 0.002)
  const spanLat = Math.max(maxLat - minLat, 0.002)

  const innerW = Math.max(1, width - pad.left - pad.right)
  const innerH = Math.max(1, height - pad.top - pad.bottom)

  // 保持等比例，避免图形被拉伸
  const scale = Math.min(innerW / spanLng, innerH / spanLat)
  const offsetX = pad.left + (innerW - spanLng * scale) / 2
  const offsetY = pad.top + (innerH - spanLat * scale) / 2

  const cx = (minLng + maxLng) / 2
  const cy = (minLat + maxLat) / 2

  return (p) => ({
    // 经度向右增大；纬度向上增大，所以 y 要翻转
    x: offsetX + (p.lng - (cx - spanLng / 2)) * scale,
    y: offsetY + (cy + spanLat / 2 - p.lat) * scale,
  })
}

/**
 * 固定经纬度窗口的正投影，并附带逆投影 invert。
 *
 * 与 createProjector 的区别：createProjector 根据传入点集自动计算包围盒，
 * 适合「把若干已知点装进画面」的展示场景；地图选点需要一个稳定不变的映射，
 * 否则在用户点击的瞬间无法把屏幕坐标反算成经纬度。因此这里使用一块固定的
 * 城区窗口 bounds，正反投影严格互逆。
 *
 * @param {{minLng:number,maxLng:number,minLat:number,maxLat:number}} bounds 固定经纬度窗口
 * @param {number} width
 * @param {number} height
 * @param {number|{top?,right?,bottom?,left?}} padding
 * @returns {((p:{lng:number,lat:number})=>{x:number,y:number}) & {invert:(x:number,y:number)=>{lng:number,lat:number}}}
 */
export function createFixedProjector(bounds, width, height, padding = 48) {
  const pad =
    typeof padding === 'number'
      ? { top: padding, right: padding, bottom: padding, left: padding }
      : { ...DEFAULT_PAD, ...padding }

  const spanLng = Math.max(bounds.maxLng - bounds.minLng, 1e-6)
  const spanLat = Math.max(bounds.maxLat - bounds.minLat, 1e-6)
  const innerW = Math.max(1, width - pad.left - pad.right)
  const innerH = Math.max(1, height - pad.top - pad.bottom)
  const scale = Math.min(innerW / spanLng, innerH / spanLat)
  const offsetX = pad.left + (innerW - spanLng * scale) / 2
  const offsetY = pad.top + (innerH - spanLat * scale) / 2

  const project = (p) => ({
    x: offsetX + (p.lng - bounds.minLng) * scale,
    y: offsetY + (bounds.maxLat - p.lat) * scale,
  })

  // 正投影的逆运算：屏幕像素 -> 经纬度
  project.invert = (x, y) => ({
    lng: bounds.minLng + (x - offsetX) / scale,
    lat: bounds.maxLat - (y - offsetY) / scale,
  })

  return project
}
