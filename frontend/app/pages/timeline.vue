<template>
  <main class="timeline-page">
    <header class="timeline-heading">
      <NuxtLink to="/" class="back-link">Voltar para a capa</NuxtLink>
      <p class="eyebrow">Seu arquivo pessoal</p>
      <h1>Linha do tempo</h1>
      <p class="intro">Atividades e descobertas, ao longo dos anos.</p>
    </header>

    <section class="filters" aria-label="Filtros da linha do tempo">
      <label
        >Domínio
        <select :value="domain" @change="setFilter('domain', ($event.target as HTMLSelectElement).value)">
          <option value="">Todos</option>
          <option v-for="(label, value) in timelineDomains" :key="value" :value="value">{{ label }}</option>
        </select>
      </label>
      <label
        >Eventos
        <select :value="kind" @change="setFilter('kind', ($event.target as HTMLSelectElement).value)">
          <option value="">Todos</option>
          <option value="ACTIVITY">Atividades</option>
          <option value="ADDITION">Adições ao acervo</option>
        </select>
      </label>
      <button v-if="month" type="button" @click="jump('')">Voltar aos mais recentes</button>
    </section>
    <p class="archive-note">Adições indicam a entrada no arquivo local. Datas de lançamento não são atividades.</p>
    <p v-if="periodsError" class="feedback" role="alert">
      Não foi possível carregar os períodos.
      <button type="button" @click="loadPeriods">Tentar novamente</button>
    </p>

    <TimelineRuler v-if="periods.length" :months="periods" :active-month="activeMonth" @select="jump" />

    <div ref="stream" class="event-stream" :aria-busy="pending">
      <p v-if="month" class="period-context">A partir de {{ formatMonth(month) }}, em direção ao passado.</p>
      <section v-for="day in days" :key="day.date" class="day-group" :data-month="day.date.slice(0, 7)">
        <h2>
          <time :datetime="day.date">{{ formatDay(day.date) }}</time>
        </h2>
        <div class="day-events">
          <TimelineEventCard v-for="event in day.items" :key="event.id" :event="event" :time-zone="timeZone" />
        </div>
      </section>
      <p v-if="pending" class="feedback" role="status">Carregando histórico…</p>
      <div v-else-if="pageError" class="feedback" role="alert">
        <p>Não foi possível carregar o histórico.</p>
        <button type="button" @click="loadPage(items.length === 0)">Tentar novamente</button>
      </div>
      <p v-else-if="!items.length" class="feedback">Nenhum registro com data neste recorte.</p>
      <p v-else-if="!nextCursor" class="feedback">Você chegou ao início deste histórico.</p>
      <div ref="sentinel" class="sentinel" aria-hidden="true" />
      <button
        v-if="nextCursor && !pageError"
        class="load-more"
        type="button"
        :disabled="pending"
        @click="loadPage(false)"
      >
        Carregar mais registros
      </button>
    </div>
  </main>
</template>

<script setup lang="ts">
import TimelineEventCard from '~/components/timeline/TimelineEventCard.vue'
import TimelineRuler from '~/components/timeline/TimelineRuler.vue'
import {
  timelineDomains,
  type TimelineEvent,
  type TimelineMonth,
  type TimelinePage,
  type TimelinePeriods,
} from '~/types/timeline'

useHead({ title: 'Linha do tempo · Media Pulse' })
const route = useRoute()
const router = useRouter()
const config = useRuntimeConfig()
const domain = computed(() => (typeof route.query.domain === 'string' ? route.query.domain : ''))
const kind = computed(() => (typeof route.query.kind === 'string' ? route.query.kind : ''))
const month = computed(() => (typeof route.query.month === 'string' ? route.query.month : ''))
const items = ref<TimelineEvent[]>([])
const periods = ref<TimelineMonth[]>([])
const nextCursor = ref<string | null>(null)
const activeMonth = ref('')
const timeZone = ref('America/Sao_Paulo')
const pending = ref(false)
const pageError = ref(false)
const periodsError = ref(false)
const stream = ref<HTMLElement | null>(null)
const sentinel = ref<HTMLElement | null>(null)
let pageGeneration = 0
let periodsGeneration = 0
let observer: IntersectionObserver | undefined
let frame = 0
let disposed = false
const days = computed(() => {
  const groups: { date: string; items: TimelineEvent[] }[] = []
  for (const event of items.value) {
    const last = groups[groups.length - 1]
    if (last?.date === event.date) last.items.push(event)
    else groups.push({ date: event.date, items: [event] })
  }
  return groups
})
function queryFilters() {
  return { ...(domain.value ? { domain: domain.value } : {}), ...(kind.value ? { kind: kind.value } : {}) }
}
function setFilter(key: 'domain' | 'kind', value: string) {
  const query = { ...queryFilters(), [key]: value || undefined }
  void router.push({ path: '/timeline', query })
}
function jump(value: string) {
  if (value === month.value) {
    stream.value?.scrollIntoView({ block: 'start' })
    activeMonth.value = value || periods.value[0]?.month || ''
    return
  }
  void router.push({ path: '/timeline', query: { ...queryFilters(), ...(value ? { month: value } : {}) } })
}
async function loadPeriods() {
  const generation = ++periodsGeneration
  periodsError.value = false
  periods.value = []
  try {
    const result = await $fetch<TimelinePeriods>('/api/timeline/periods', {
      baseURL: config.public.apiBase,
      query: queryFilters(),
    })
    if (disposed || generation !== periodsGeneration) return
    periods.value = result.months
  } catch {
    if (!disposed && generation === periodsGeneration) periodsError.value = true
  }
}
async function loadPage(reset: boolean) {
  if (!reset && (pending.value || !nextCursor.value)) return
  const generation = reset ? ++pageGeneration : pageGeneration
  if (reset) {
    items.value = []
    nextCursor.value = null
    activeMonth.value = month.value
  }
  pending.value = true
  pageError.value = false
  try {
    const result = await $fetch<TimelinePage>('/api/timeline', {
      baseURL: config.public.apiBase,
      query: {
        ...queryFilters(),
        ...(month.value ? { month: month.value } : {}),
        ...(reset ? {} : { cursor: nextCursor.value }),
        limit: 40,
      },
    })
    if (disposed || generation !== pageGeneration) return
    const known = new Set(items.value.map((event) => event.id))
    items.value.push(...result.items.filter((event) => !known.has(event.id)))
    nextCursor.value = result.nextCursor
    timeZone.value = result.timeZone
    if (reset) activeMonth.value = result.items[0]?.date.slice(0, 7) || month.value
  } catch {
    if (!disposed && generation === pageGeneration) pageError.value = true
  } finally {
    if (!disposed && generation === pageGeneration) {
      pending.value = false
      await nextTick()
      observeEnd()
    }
  }
}
function observeEnd() {
  observer?.disconnect()
  if (sentinel.value && nextCursor.value && !pageError.value && !pending.value) observer?.observe(sentinel.value)
}
function trackPeriod() {
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    const groups = stream.value?.querySelectorAll<HTMLElement>('[data-month]')
    if (!groups?.length) return
    let current = groups[0]!
    if (window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 4) {
      activeMonth.value = groups[groups.length - 1]!.dataset.month || ''
      return
    }
    for (const group of groups) {
      if (group.getBoundingClientRect().top > 180) break
      current = group
    }
    activeMonth.value = current.dataset.month || ''
  })
}
function formatDay(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(`${value}T12:00:00Z`),
  )
}
function formatMonth(value: string) {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(value)) return value
  return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(`${value}-01T12:00:00Z`),
  )
}
watch([domain, kind], () => void loadPeriods(), { immediate: true })
watch(
  [domain, kind, month],
  () => {
    void loadPage(true)
    window.scrollTo({ top: 0 })
  },
  { immediate: true },
)
onMounted(() => {
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((entry) => entry.isIntersecting) && !pageError.value) void loadPage(false)
    },
    { rootMargin: '500px' },
  )
  observeEnd()
  window.addEventListener('scroll', trackPeriod, { passive: true })
})
onBeforeUnmount(() => {
  disposed = true
  observer?.disconnect()
  window.removeEventListener('scroll', trackPeriod)
  if (frame) cancelAnimationFrame(frame)
})
</script>

<style scoped>
.timeline-page {
  width: min(1120px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 32px 100px 80px 0;
}
.timeline-heading {
  margin-bottom: 32px;
}
.back-link {
  display: inline-block;
  margin-bottom: 28px;
  font-size: 0.875rem;
  color: var(--base-color-text-secondary);
}
.eyebrow {
  margin: 0 0 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--base-color-text-secondary);
}
h1 {
  margin: 0;
  font-size: clamp(2.5rem, 6vw, 5rem);
  line-height: 1.05;
  letter-spacing: -0.05em;
}
.intro {
  margin: 16px 0 0;
  color: var(--base-color-text-secondary);
}
.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 16px;
}
label {
  display: grid;
  gap: 8px;
  font-size: 0.875rem;
}
select,
button {
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid var(--base-color-surface-warm);
  border-radius: 16px;
  background: var(--base-color-surface-strong, white);
  color: var(--base-color-text-primary);
  font: inherit;
}
button {
  background: var(--base-color-surface-warm);
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: wait;
}
select:focus-visible,
button:focus-visible,
a:focus-visible {
  outline: 2px solid #435ee5;
  outline-offset: 4px;
}
.archive-note {
  margin: 16px 0 32px;
  font-size: 0.75rem;
  color: var(--base-color-text-secondary);
}
.event-stream,
.day-events {
  display: grid;
  gap: 16px;
}
.event-stream {
  gap: 32px;
  scroll-margin-top: 110px;
}
h2 {
  margin: 0 0 16px;
  font-size: 1.125rem;
  letter-spacing: -0.02em;
}
.feedback,
.period-context {
  color: var(--base-color-text-secondary);
  font-size: 0.875rem;
}
.feedback {
  padding: 20px 0;
}
.sentinel {
  height: 1px;
}
.load-more {
  justify-self: start;
}
@media (max-width: 575px) {
  .timeline-page {
    width: calc(100vw - 20px);
    padding: 24px 60px 64px 0;
  }
  .filters {
    gap: 12px;
  }
  label {
    width: 100%;
  }
  select {
    width: 100%;
    min-width: 0;
  }
  h1 {
    font-size: clamp(2rem, 9vw, 3rem);
  }
}
</style>
