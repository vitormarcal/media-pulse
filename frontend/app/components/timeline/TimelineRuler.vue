<template>
  <aside
    ref="ruler"
    class="time-ruler"
    aria-label="Navegação pelo histórico"
    @keydown.esc.stop.prevent="closePicker(true)"
  >
    <button
      ref="periodButton"
      type="button"
      class="current-period"
      :aria-expanded="pickerOpen"
      aria-controls="timeline-period-picker"
      @click="togglePicker"
    >
      {{ label(activePeriod) }}
      <span aria-hidden="true">⌄</span>
    </button>
    <div
      ref="track"
      class="ruler-track"
      role="slider"
      :tabindex="months.length ? 0 : -1"
      aria-label="Histórico. Setas escolhem um mês; Enter abre a seleção de ano e mês."
      aria-orientation="vertical"
      :aria-valuemin="0"
      :aria-valuemax="Math.max(0, months.length - 1)"
      :aria-valuenow="selectedIndex"
      :aria-valuetext="label(activePeriod)"
      :aria-disabled="!months.length"
      @keydown="onKey"
      @pointerdown="pointerDown"
      @pointermove="pointerMove"
      @pointerup="pointerUp"
      @pointercancel="cancelDrag"
      @lostpointercapture="cancelDrag"
      @pointerleave="!dragging && (previewYear = '')"
    >
      <span class="ruler-line" />
      <span
        v-for="year in visibleYears"
        :key="year.value"
        class="year-mark"
        :style="{ top: position(year.index) + '%' }"
        aria-hidden="true"
      >
        <span class="year-label">{{ year.value }}</span
        ><span class="year-dot" />
      </span>
      <span
        v-if="months.length"
        class="ruler-marker"
        :style="{ top: position(selectedIndex) + '%' }"
        aria-hidden="true"
      />
      <span
        v-if="previewYear"
        class="period-tooltip"
        :style="{ top: position(years.find((y) => y.value === previewYear)?.index ?? 0) + '%' }"
        aria-hidden="true"
        >{{ previewYear }} · escolher mês</span
      >
    </div>

    <section
      v-if="pickerOpen"
      id="timeline-period-picker"
      class="period-picker"
      role="dialog"
      aria-labelledby="timeline-picker-title"
    >
      <div class="picker-heading">
        <h2 id="timeline-picker-title">Ir para o período</h2>
        <button type="button" aria-label="Fechar seleção de período" @click="closePicker(true)">×</button>
      </div>
      <label
        >Ano
        <select ref="yearSelect" v-model="pickerYear">
          <option v-for="year in years" :key="year.value" :value="year.value">{{ year.value }}</option>
        </select>
      </label>
      <div class="month-options" aria-label="Meses com registros">
        <button
          v-for="(name, index) in monthNames"
          :key="name"
          type="button"
          :disabled="!availableMonths.has(index + 1)"
          :aria-label="`${name} de ${pickerYear}`"
          :aria-pressed="activePeriod === monthValue(index)"
          @click="chooseMonth(index)"
        >
          {{ name }}
        </button>
      </div>
      <p>Somente meses com registros.</p>
    </section>
  </aside>
</template>

<script setup lang="ts">
import type { TimelineMonth } from '~/types/timeline'
const props = defineProps<{ months: TimelineMonth[]; activeMonth: string }>()
const emit = defineEmits<{ select: [month: string] }>()
const ruler = ref<HTMLElement | null>(null)
const track = ref<HTMLElement | null>(null)
const periodButton = ref<HTMLButtonElement | null>(null)
const yearSelect = ref<HTMLSelectElement | null>(null)
const height = ref(500)
const previewYear = ref('')
const dragging = ref(false)
const pickerOpen = ref(false)
const pickerYear = ref('')
const monthNames = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']
let resizeObserver: ResizeObserver | undefined
const selectedIndex = computed(() =>
  Math.max(
    0,
    props.months.findIndex((m) => m.month === props.activeMonth),
  ),
)
const activePeriod = computed(() => props.months[selectedIndex.value]?.month || '')
const position = (index: number) => (props.months.length <= 1 ? 0 : (index / (props.months.length - 1)) * 100)
const years = computed(() => {
  const seen = new Set<string>()
  return props.months.flatMap((m, index) => {
    const value = m.month.slice(0, 4)
    if (seen.has(value)) return []
    seen.add(value)
    return [{ value, index }]
  })
})
const visibleYears = computed(() => {
  let last = -100
  return years.value.filter((year, index) => {
    const px = (position(year.index) / 100) * height.value
    const isLast = index === years.value.length - 1
    // Keep the final year legible without packing marks into a solid line.
    if (px - last < 32 && !isLast) return false
    if (!isLast && height.value - px < 32) return false
    last = px
    return true
  })
})
const availableMonths = computed(
  () =>
    new Set(
      props.months.filter((m) => m.month.startsWith(pickerYear.value + '-')).map((m) => Number(m.month.slice(5))),
    ),
)
function label(month: string) {
  if (!month) return 'Sem registros'
  return new Intl.DateTimeFormat('pt-BR', { month: 'short', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(`${month}-01T12:00:00Z`),
  )
}
function monthValue(index: number) {
  return `${pickerYear.value}-${String(index + 1).padStart(2, '0')}`
}
async function openPicker(year: string) {
  pickerYear.value = year
  pickerOpen.value = true
  previewYear.value = ''
  await nextTick()
  yearSelect.value?.focus({ preventScroll: true })
}
function togglePicker() {
  if (pickerOpen.value) closePicker(false)
  else void openPicker(activePeriod.value.slice(0, 4))
}
function closePicker(restoreFocus: boolean) {
  pickerOpen.value = false
  if (restoreFocus) periodButton.value?.focus({ preventScroll: true })
}
function chooseMonth(index: number) {
  const month = monthValue(index)
  if (!props.months.some((m) => m.month === month)) return
  emit('select', month)
  closePicker(true)
}
function yearAt(event: PointerEvent) {
  const rect = track.value!.getBoundingClientRect()
  const index =
    Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height)) * Math.max(0, props.months.length - 1)
  return (
    years.value.reduce(
      (closest, year) => (Math.abs(year.index - index) < Math.abs(closest.index - index) ? year : closest),
      years.value[0]!,
    )?.value || ''
  )
}
function pointerDown(event: PointerEvent) {
  if (!props.months.length || !event.isPrimary || event.button !== 0) return
  track.value?.focus({ preventScroll: true })
  track.value?.setPointerCapture(event.pointerId)
  dragging.value = true
  previewYear.value = yearAt(event)
}
function pointerMove(event: PointerEvent) {
  if (props.months.length) previewYear.value = yearAt(event)
}
function pointerUp(event: PointerEvent) {
  if (!dragging.value) return
  const year = yearAt(event)
  dragging.value = false
  if (track.value?.hasPointerCapture(event.pointerId)) track.value.releasePointerCapture(event.pointerId)
  if (year) void openPicker(year)
}
function cancelDrag() {
  dragging.value = false
  previewYear.value = ''
}
function onKey(event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    void openPicker(activePeriod.value.slice(0, 4))
    return
  }
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
function outsidePointer(event: PointerEvent) {
  if (pickerOpen.value && !ruler.value?.contains(event.target as Node)) closePicker(false)
}
watch(
  () => props.months,
  () => {
    cancelDrag()
    closePicker(false)
  },
)
onMounted(() => {
  resizeObserver = new ResizeObserver(([entry]) => {
    if (entry) height.value = entry.contentRect.height
  })
  if (track.value) resizeObserver.observe(track.value)
  document.addEventListener('pointerdown', outsidePointer)
})
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  document.removeEventListener('pointerdown', outsidePointer)
})
</script>

<style scoped>
.time-ruler {
  position: fixed;
  top: 110px;
  bottom: 38px;
  right: max(16px, calc((100vw - 1480px) / 2));
  width: 76px;
  z-index: 15;
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.current-period {
  width: 100%;
  padding: 6px 0;
  min-height: 44px;
  font-size: 0.7rem;
  line-height: 1.2;
  display: grid;
  justify-items: center;
  background: var(--base-color-surface-strong);
  border: 1px solid var(--base-color-surface-warm);
  border-radius: 12px;
}
.ruler-track {
  position: relative;
  flex: 1;
  min-height: 0;
  width: 100%;
  cursor: pointer;
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
}
.ruler-track:focus-visible {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 2px;
  border-radius: 12px;
}
.ruler-line {
  position: absolute;
  right: 12px;
  top: 0;
  bottom: 0;
  border-right: 1px solid var(--base-color-surface-warm);
}
.year-mark {
  position: absolute;
  right: 0;
  width: 100%;
  transform: translateY(-50%);
  pointer-events: none;
}
.year-label {
  display: block;
  padding-right: 24px;
  text-align: right;
  font-size: 0.75rem;
  color: var(--base-color-text-secondary);
}
.year-dot {
  position: absolute;
  right: 10px;
  top: 50%;
  width: 5px;
  height: 5px;
  transform: translateY(-50%);
  border-radius: 50%;
  background: var(--base-color-text-secondary);
  opacity: 0.4;
}
.ruler-marker {
  position: absolute;
  right: 4px;
  width: 16px;
  height: 2px;
  background: var(--base-color-brand-red);
  pointer-events: none;
}
.period-tooltip {
  position: absolute;
  right: 24px;
  transform: translateY(-50%);
  padding: 8px 10px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
  border: 1px solid var(--base-color-surface-warm);
  border-radius: 12px;
  white-space: nowrap;
  font-size: 0.875rem;
  pointer-events: none;
}
.period-picker {
  position: absolute;
  top: 0;
  right: calc(100% + 8px);
  width: min(300px, calc(100vw - 100px));
  max-height: calc(100dvh - 150px);
  overflow-y: auto;
  padding: 16px;
  border: 1px solid var(--base-color-border);
  border-radius: 20px;
  background: var(--base-color-surface-strong);
  box-shadow: var(--base-shadow-soft);
}
.picker-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
h2 {
  margin: 0;
  font-size: 1rem;
}
.picker-heading button {
  flex-shrink: 0;
  width: 44px;
  font-size: 1.5rem;
}
label {
  display: grid;
  gap: 6px;
  font-size: 0.875rem;
  margin: 12px 0;
}
select {
  width: 100%;
  padding: 10px;
  border: 1px solid var(--base-color-border);
  border-radius: 12px;
  color: inherit;
  background: white;
}
button {
  color: inherit;
  font: inherit;
  cursor: pointer;
}
button,
select {
  min-height: 44px;
}
.month-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}
.month-options button,
.picker-heading button {
  border: 0;
  border-radius: 12px;
  background: var(--base-color-surface-soft);
}
.month-options button[aria-pressed='true'] {
  background: var(--base-color-brand-red);
  color: white;
}
.month-options button:disabled {
  opacity: 0.4;
  cursor: default;
}
p {
  margin: 12px 0 0;
  font-size: 0.75rem;
  color: var(--base-color-text-secondary);
}
button:focus-visible,
select:focus-visible {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 2px;
}
@media (max-width: 575px) {
  .time-ruler {
    top: 98px;
    bottom: 28px;
    right: 6px;
    width: 56px;
  }
  .current-period {
    font-size: 0.625rem;
  }
  .year-label {
    font-size: 0.6875rem;
  }
  .period-picker {
    width: min(300px, calc(100vw - 84px));
    padding: 12px;
    max-height: calc(100dvh - 120px);
  }
}
</style>
