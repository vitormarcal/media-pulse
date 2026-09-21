<template>
  <main class="workouts-page">
    <section class="workouts-hero">
      <div class="hero-copy">
        <p class="eyebrow">Seu ritmo, suas lembranças</p>
        <h1>Em movimento.</h1>
        <p>Uma corrida, alguns pulos, um tempo na academia.<br />Guarde os momentos em que você cuidou de você.</p>
        <button ref="addButton" type="button" class="primary-button" @click="composerOpen = true">
          + Registrar treino
        </button>
      </div>
      <div class="hero-images" aria-hidden="true">
        <img
          v-for="category in workoutCategories"
          :key="category.value"
          :src="category.image"
          alt=""
          width="280"
          height="340"
        />
      </div>
    </section>

    <section class="history" aria-labelledby="history-title">
      <div class="history-heading">
        <h2 id="history-title">Seus treinos</h2>
        <div class="filters" aria-label="Filtrar treinos por atividade">
          <button type="button" :aria-pressed="!filter" @click="setFilter(null)">Todos</button>
          <button
            v-for="category in workoutCategories"
            :key="category.value"
            type="button"
            :aria-pressed="filter === category.value"
            @click="setFilter(category.value)"
          >
            {{ category.label }}
          </button>
        </div>
      </div>
      <p v-if="success" class="success" role="status">{{ success }}</p>
      <div v-if="loading" class="state" role="status">Buscando suas lembranças…</div>
      <div v-else-if="loadError && !items.length" class="state" role="alert">
        <p>{{ loadError }}</p>
        <button type="button" class="secondary-button" @click="load(false)">Tentar novamente</button>
      </div>
      <div v-else-if="!items.length" class="state empty">
        <h3>{{ filter ? 'Seu próximo momento começa aqui.' : 'Cada movimento merece uma lembrança.' }}</h3>
        <p>
          {{
            filter
              ? 'Ainda não há treinos desta atividade.'
              : 'Registre seu primeiro treino para começar seu histórico.'
          }}
        </p>
        <button type="button" class="secondary-button" @click="composerOpen = true">Registrar treino</button>
      </div>
      <div v-else class="workout-grid">
        <article v-for="workout in items" :key="workout.id" class="workout-card">
          <img
            :src="resolveMediaUrl(workout.photoUrl) || workoutCategory(workout.category).image"
            :alt="workout.photoUrl ? `Lembrança de ${workoutCategory(workout.category).label.toLowerCase()}` : ''"
            loading="lazy"
            width="400"
            height="400"
            @error="fallbackImage($event, workout.category)"
          />
          <div class="card-copy">
            <time :datetime="workout.startedAt">{{ formatDate(workout.startedAt) }}</time>
            <h3>{{ workoutCategory(workout.category).label }}</h3>
            <p class="workout-metrics">
              <span v-if="workout.distanceKm !== null">{{ numberFormat.format(workout.distanceKm) }} km · </span
              ><span v-if="workout.jumps !== null">{{ numberFormat.format(workout.jumps) }} pulos · </span
              >{{ workout.durationMinutes }} min
            </p>
            <p v-if="workout.location" class="workout-location">{{ workout.location }}</p>
          </div>
        </article>
      </div>
      <p v-if="loadError && items.length" role="alert">{{ loadError }}</p>
      <div v-if="nextPage !== null && !loading" class="load-more">
        <button type="button" class="secondary-button" :disabled="loadingMore" @click="load(true)">
          {{ loadingMore ? 'Buscando…' : 'Ver mais treinos' }}
        </button>
      </div>
    </section>
    <WorkoutComposer :open="composerOpen" @close="composerOpen = false" @saved="saved" />
  </main>
</template>

<script setup lang="ts">
import WorkoutComposer from '~/components/workouts/WorkoutComposer.vue'
import type { Workout, WorkoutCategory } from '~/types/workouts'
import { workoutCategories, workoutCategory } from '~/utils/workouts'

const api = useWorkouts()
const route = useRoute()
const router = useRouter()
const { resolveMediaUrl } = useMediaUrl()
const filter = computed(() => workoutCategories.find((item) => item.value === route.query.category)?.value ?? null)
const composerOpen = ref(false)
const addButton = ref<HTMLButtonElement | null>(null)
const items = ref<Workout[]>([])
const nextPage = ref<number | null>(null)
const loading = ref(true)
const loadingMore = ref(false)
const loadError = ref('')
const success = ref('')
const numberFormat = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 })
const dateFormat = new Intl.DateTimeFormat('pt-BR', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})
let requestId = 0

function formatDate(value: string) {
  return dateFormat.format(new Date(value))
}

function setFilter(value: WorkoutCategory | null) {
  router.replace({ query: { ...route.query, category: value ?? undefined } })
}

async function load(append: boolean) {
  const id = ++requestId
  const page = append ? nextPage.value : 0
  if (page === null) return
  if (append) loadingMore.value = true
  else {
    loading.value = true
    items.value = []
    nextPage.value = null
    loadingMore.value = false
  }
  loadError.value = ''
  try {
    const response = await api.history(filter.value, page)
    if (id !== requestId) return
    items.value = append
      ? [...items.value, ...response.items.filter((item) => !items.value.some((existing) => existing.id === item.id))]
      : response.items
    nextPage.value = response.nextPage
  } catch {
    if (id === requestId) loadError.value = 'Não foi possível carregar os treinos. Tente novamente.'
  } finally {
    if (id === requestId) {
      loading.value = false
      loadingMore.value = false
    }
  }
}

async function saved(workout: Workout) {
  composerOpen.value = false
  if (filter.value && filter.value !== workout.category) {
    await router.replace({ query: { ...route.query, category: undefined } })
  } else await load(false)
  success.value = `${workoutCategory(workout.category).label}: treino guardado.`
  addButton.value?.focus()
}

function fallbackImage(event: Event, category: WorkoutCategory) {
  const image = event.target as HTMLImageElement
  image.onerror = null
  const fallback = workoutCategory(category).image
  if (!image.src.endsWith(fallback)) image.src = fallback
}

watch(filter, () => {
  success.value = ''
  void load(false)
})
onMounted(() => {
  void load(false)
})
onBeforeUnmount(() => {
  requestId++
})
useHead({ title: 'Treinos · Media Pulse' })
</script>

<style scoped>
.workouts-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}
.workouts-hero {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: center;
  gap: 32px;
  padding: clamp(24px, 4vw, 54px);
  border-radius: 40px;
  background: var(--base-color-surface-soft);
  overflow: hidden;
}
.hero-copy {
  position: relative;
  z-index: 1;
}
.eyebrow {
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
}
h1 {
  margin: 16px 0;
  font-size: clamp(2.8rem, 5vw, 4.375rem);
  line-height: 1;
  font-weight: 600;
  letter-spacing: -0.055em;
}
.hero-copy > p:not(.eyebrow) {
  color: var(--base-color-text-secondary);
  line-height: 1.6;
  margin-bottom: 24px;
}
button {
  cursor: pointer;
}
.primary-button,
.secondary-button,
.filters button {
  border: 0;
  border-radius: 16px;
  padding: 12px 18px;
  color: var(--base-color-text-primary);
  background: var(--base-color-surface-warm);
}
.primary-button {
  background: var(--base-color-brand-red);
  color: white;
  font-weight: 600;
}
.hero-images {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 20px 0;
}
.hero-images img {
  width: calc((100% - 24px) / 3);
  height: auto;
  aspect-ratio: 0.72;
  border-radius: 24px;
  object-fit: cover;
}
.hero-images img:nth-child(2) {
  transform: translateY(-18px);
}
.hero-images img:nth-child(3) {
  transform: translateY(18px);
}
.history {
  display: grid;
  gap: 24px;
}
.history-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
}
h2 {
  margin: 0;
  font-size: 28px;
  letter-spacing: -1.2px;
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.filters button {
  font-size: 0.85rem;
}
.filters button[aria-pressed='true'] {
  background: var(--base-color-text-primary);
  color: white;
}
.workout-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: start;
  gap: 28px 18px;
}
.workout-card {
  min-width: 0;
}
.workout-card > img {
  width: 100%;
  height: auto;
  aspect-ratio: 1;
  border-radius: 20px;
  object-fit: cover;
  background: var(--base-color-surface-warm);
}
.card-copy {
  padding: 14px 4px 0;
}
.card-copy time {
  font-size: 0.78rem;
  color: var(--base-color-text-secondary);
}
.card-copy h3 {
  margin: 6px 0;
  font-size: 1.1rem;
}
.workout-metrics {
  margin: 0;
  font-size: 0.9rem;
}
.workout-location {
  margin: 8px 0 0;
  color: var(--base-color-text-secondary);
  font-size: 0.85rem;
  overflow-wrap: anywhere;
}
.state {
  padding: 40px 24px;
  border-radius: 28px;
  background: var(--base-color-surface-soft);
}
.empty {
  text-align: center;
}
.empty h3 {
  font-size: 1.4rem;
  margin: 0;
}
.empty p {
  color: var(--base-color-text-secondary);
}
.load-more {
  display: flex;
  justify-content: center;
}
.success {
  margin: 0;
  color: var(--base-color-text-secondary);
}
@media (max-width: 1000px) {
  .workout-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .workouts-hero {
    gap: 20px;
  }
}
@media (max-width: 767px) {
  .workouts-hero {
    grid-template-columns: 1fr;
  }
  .hero-images {
    max-width: 420px;
  }
  .hero-images img {
    max-height: 170px;
  }
  .workout-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 575px) {
  .workouts-page {
    width: calc(100vw - 20px);
  }
  .workout-grid {
    grid-template-columns: 1fr;
  }
  .workouts-hero {
    border-radius: 28px;
  }
  .filters button {
    padding: 12px;
  }
}
</style>
