import { computed, onMounted, onUnmounted, ref } from 'vue'

/**
 * 视口宽度 / 断点判断。
 *
 * 为什么需要它：MiniMap 的高度是 JS 属性（因为它要用 ResizeObserver 量宽度
 * 再按像素画 SVG），没法用纯 CSS 媒体查询切换，所以得在 JS 里知道当前断点。
 *
 * 断点与 styles/layout.css 保持一致。
 */
export function useViewport() {
  const width = ref(typeof window === 'undefined' ? 1280 : window.innerWidth)

  function update() {
    width.value = window.innerWidth
  }

  onMounted(() => {
    update()
    window.addEventListener('resize', update, { passive: true })
  })

  onUnmounted(() => {
    window.removeEventListener('resize', update)
  })

  const isMobile = computed(() => width.value < 640)
  const isTablet = computed(() => width.value >= 640 && width.value < 1024)
  const isDesktop = computed(() => width.value >= 1024)

  return { width, isMobile, isTablet, isDesktop }
}

/** 按断点取一个值：pick({ mobile, tablet, desktop }) */
export function useResponsiveValue(pick) {
  const { isMobile, isTablet } = useViewport()
  return computed(() => {
    if (isMobile.value) return pick.mobile
    if (isTablet.value) return pick.tablet ?? pick.desktop
    return pick.desktop
  })
}
