<template>
  <aside class="time-ruler" aria-label="Navegação pelo histórico">
    <div
      ref="track"
      class="ruler-track"
      role="slider"
      :tabindex="months.length ? 0 : -1"
      aria-label="Período da linha do tempo. Use as setas para escolher um mês."
      aria-orientation="vertical"
      :aria-valuemin="0"
      :aria-valuemax="Math.max(0, months.length - 1)"
      :aria-valuenow="selectedIndex"
      :aria-valuetext="label(months[selectedIndex]?.month)"
      :aria-disabled="!months.length"
      @keydown="onKey"
      @pointerdown="pointerDown"
      @pointermove="pointerMove"
      @pointerup="pointerUp"
      @pointercancel="cancelDrag"
      @lostpointercapture="cancelDrag"
      @pointerleave="!dragging && (preview = null)"
      @focus="focused = true"
      @blur="focused = false"
    >
      <span class="ruler-line" />
      <span
        v-for="(period, index) in months"
        :key="period.month"
        class="month-dot"
        :style="{ top: position(index) + '%' }"
        aria-hidden="true"
      />
      <span
        v-for="year in years"
        :key="year.label"
        class="year-label"
        :style="{ top: position(year.index) + '%' }"
        aria-hidden="true"
        >{{ year.label }}</span
      >
      <span
        v-if="months.length"
        class="ruler-marker"
        :style="{ top: position(selectedIndex) + '%' }"
        aria-hidden="true"
      />
      <span
        v-if="months.length && (preview !== null || focused || dragging)"
        class="period-tooltip"
        :style="{ top: position(preview ?? selectedIndex) + '%' }"
        aria-hidden="true"
        >{{ label(months[preview ?? selectedIndex]?.month) }}</span
      >
    </div>
  </aside>
</template>

<script setup lang="ts">
import type { TimelineMonth } from '~/types/timeline'
const props = defineProps<{ months: TimelineMonth[]; activeMonth: string }>()
const emit = defineEmits<{ select: [month: string] }>()
const track = ref<HTMLElement | null>(null)
const height = ref(500)
const preview = ref<number | null>(null)
const focused = ref(false)
const dragging = ref(false)
let resizeObserver: ResizeObserver | undefined
const selectedIndex = computed(() =>
  Math.max(
    0,
    props.months.findIndex((m) => m.month === props.activeMonth),
  ),
)
const position = (index: number) => (props.months.length <= 1 ? 0 : (index / (props.months.length - 1)) * 100)
const years = computed(() => {
  const result: { label: string; index: number }[] = []
  let lastPosition = -100
  let lastYear = ''
  props.months.forEach((m, index) => {
    const year = m.month.slice(0, 4)
    const px = (position(index) / 100) * height.value
    if (year !== lastYear && px - lastPosition >= 30) {
      result.push({ label: year, index })
      lastPosition = px
    }
    lastYear = year
  })
  return result
})
function label(month?: string) {
  if (!month) return 'Sem registros'
  return new Intl.DateTimeFormat('pt-BR', { month: 'short', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(`${month}-01T12:00:00Z`),
  )
}
function indexAt(event: PointerEvent) {
  const rect = track.value!.getBoundingClientRect()
  return Math.round(Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)) * (props.months.length - 1))
}
function pointerDown(event: PointerEvent) {
  if (!props.months.length || !event.isPrimary || event.button !== 0) return
  track.value?.focus({ preventScroll: true })
  track.value?.setPointerCapture(event.pointerId)
  dragging.value = true
  preview.value = indexAt(event)
}
function pointerMove(event: PointerEvent) {
  if (props.months.length) preview.value = indexAt(event)
}
function pointerUp(event: PointerEvent) {
  if (!dragging.value) return
  const month = props.months[indexAt(event)]?.month
  dragging.value = false
  if (track.value?.hasPointerCapture(event.pointerId)) track.value.releasePointerCapture(event.pointerId)
  preview.value = null
  if (month) emit('select', month)
}
function cancelDrag() {
  dragging.value = false
  preview.value = null
}
function onKey(event: KeyboardEvent) {
  const offsets: Record<string, number> = {
    ArrowDown: 1,
    ArrowRight: 1,
    ArrowUp: -1,
    ArrowLeft: -1,
    PageDown: 12,
    PageUp: -12,
  }
  let index = selectedIndex.value
  if (event.key === 'Home') index = 0
  else if (event.key === 'End') index = props.months.length - 1
  else if (event.key in offsets) index += offsets[event.key]!
  else return
  event.preventDefault()
  const month = props.months[Math.max(0, Math.min(props.months.length - 1, index))]?.month
  if (month) emit('select', month)
}
watch(() => props.months, cancelDrag)
onMounted(() => {
  resizeObserver = new ResizeObserver(([entry]) => {
    if (entry) height.value = entry.contentRect.height
  })
  if (track.value) resizeObserver.observe(track.value)
})
onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<style scoped>
.time-ruler {
  position: fixed;
  top: 155px;
  bottom: 50px;
  right: max(16px, calc((100vw - 1480px) / 2));
  width: 76px;
  z-index: 15;
}
.ruler-track {
  position: relative;
  height: 100%;
  width: 100%;
  cursor: pointer;
  touch-action: none;
  user-select: none;
}
.ruler-track:focus-visible {
  outline: 2px solid #435ee5;
  outline-offset: 5px;
  border-radius: 12px;
}
.ruler-line {
  position: absolute;
  right: 16px;
  top: 0;
  bottom: 0;
  border-right: 1px solid var(--base-color-surface-warm);
}
.month-dot {
  position: absolute;
  right: 14px;
  width: 5px;
  height: 5px;
  transform: translateY(-50%);
  border-radius: 50%;
  background: var(--base-color-text-secondary);
  opacity: 0.35;
  pointer-events: none;
}
.year-label {
  position: absolute;
  right: 28px;
  transform: translateY(-50%);
  font-size: 0.75rem;
  color: var(--base-color-text-secondary);
  pointer-events: none;
}
.ruler-marker {
  position: absolute;
  right: 0;
  width: 24px;
  height: 2px;
  background: var(--base-color-brand-red);
  pointer-events: none;
}
.period-tooltip {
  position: absolute;
  right: 32px;
  transform: translateY(-50%);
  padding: 8px 10px;
  background: var(--base-color-surface-strong, white);
  color: var(--base-color-text-primary);
  border: 1px solid var(--base-color-surface-warm);
  border-radius: 12px;
  white-space: nowrap;
  font-size: 0.875rem;
  pointer-events: none;
}
@media (max-width: 575px) {
  .time-ruler {
    top: 165px;
    bottom: 40px;
    right: 4px;
    width: 60px;
  }
  .ruler-line {
    right: 10px;
  }
  .month-dot {
    right: 8px;
  }
  .year-label {
    right: 20px;
    font-size: 0.6875rem;
  }
  .ruler-marker {
    width: 16px;
  }
  .period-tooltip {
    right: 26px;
  }
}
</style>
