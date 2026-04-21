<template>
  <section class="add-watch-panel">
    <SectionHeading
      eyebrow="Episódio"
      title="Marque um episódio assistido"
      description="Use quando uma temporada ficou sem evento, mas você sabe onde aquela lembrança precisa entrar."
      summary="A marcação manual entra no histórico e recalcula o progresso da série."
    />

    <article class="panel-card">
      <div class="panel-copy">
        <p class="panel-title">Registrar episódio</p>
        <p class="panel-body">
          Informe temporada, episódio e data. Se o título não estiver à mão, a página usa uma descrição simples para manter o histórico navegável.
        </p>
      </div>

      <form class="panel-form" @submit.prevent="handleSubmit">
        <div class="episode-grid">
          <label class="field">
            <span>Temporada</span>
            <input v-model.number="seasonNumber" type="number" inputmode="numeric" min="0" placeholder="1">
          </label>

          <label class="field">
            <span>Episódio</span>
            <input v-model.number="episodeNumber" type="number" inputmode="numeric" min="0" placeholder="1">
          </label>
        </div>

        <label class="field title-field">
          <span>Título</span>
          <input v-model="episodeTitle" type="text" placeholder="Piloto, The Tangerine Factor...">
        </label>

        <label class="field">
          <span>Data e hora</span>
          <input v-model="watchedAtInput" type="datetime-local">
        </label>

        <div class="actions">
          <button type="button" class="secondary-button" :disabled="submitting" @click="setNow">
            Agora
          </button>
          <button type="submit" class="primary-button" :disabled="submitting || !canSubmit">
            {{ submitting ? 'Salvando...' : 'Marcar episódio' }}
          </button>
        </div>
      </form>

      <p v-if="feedback" class="feedback" :class="{ error: feedbackError }">{{ feedback }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ExistingShowWatchCreateRequest, ManualShowWatchCreateResponse } from '~/types/shows'

const props = defineProps<{
  showId: number
}>()

const emit = defineEmits<{
  created: [response: ManualShowWatchCreateResponse]
}>()

const config = useRuntimeConfig()
const seasonNumber = ref<number | null>(1)
const episodeNumber = ref<number | null>(1)
const episodeTitle = ref('')
const watchedAtInput = ref(toDatetimeLocalValue(new Date()))
const submitting = ref(false)
const feedback = ref<string | null>(null)
const feedbackError = ref(false)

const normalizedSeasonNumber = computed(() => normalizeOptionalNumber(seasonNumber.value))
const normalizedEpisodeNumber = computed(() => normalizeOptionalNumber(episodeNumber.value))
const canSubmit = computed(() => Boolean(watchedAtInput.value && (episodeTitle.value.trim() || normalizedEpisodeNumber.value != null)))

function toDatetimeLocalValue(date: Date) {
  const offset = date.getTimezoneOffset()
  const localDate = new Date(date.getTime() - offset * 60000)
  return localDate.toISOString().slice(0, 16)
}

function normalizeOptionalNumber(value: unknown) {
  if (typeof value !== 'number' || Number.isNaN(value)) return null
  return value >= 0 ? value : null
}

function fallbackEpisodeTitle() {
  if (episodeTitle.value.trim()) return episodeTitle.value.trim()

  const episode = normalizedEpisodeNumber.value
  if (episode != null) return `Episódio ${episode}`

  return 'Episódio marcado manualmente'
}

function setNow() {
  watchedAtInput.value = toDatetimeLocalValue(new Date())
}

async function handleSubmit() {
  submitting.value = true
  feedback.value = null
  feedbackError.value = false

  try {
    const body: ExistingShowWatchCreateRequest = {
      watchedAt: new Date(watchedAtInput.value).toISOString(),
      episodeTitle: fallbackEpisodeTitle(),
      seasonNumber: normalizedSeasonNumber.value,
      episodeNumber: normalizedEpisodeNumber.value,
    }

    const response = await $fetch<ManualShowWatchCreateResponse>(`/api/shows/${props.showId}/watches`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body,
    })

    feedback.value = response.watchInserted ? 'Episódio marcado no histórico.' : 'Esse episódio já estava marcado nessa data.'
    emit('created', response)
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível marcar o episódio.'
    feedbackError.value = true
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.add-watch-panel {
  display: grid;
  gap: 24px;
}

.panel-card {
  display: grid;
  gap: 18px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.panel-copy {
  display: grid;
  gap: 6px;
}

.panel-title,
.panel-body,
.feedback {
  margin: 0;
}

.panel-title {
  font-size: 1.05rem;
  font-weight: 700;
}

.panel-body,
.feedback {
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}

.panel-form {
  display: grid;
  grid-template-columns: minmax(10rem, 0.7fr) minmax(14rem, 1fr) minmax(14rem, 0.9fr) auto;
  gap: 14px;
  align-items: end;
}

.episode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
}

.actions {
  display: flex;
  gap: 10px;
}

.primary-button,
.secondary-button {
  min-height: 44px;
  border: 0;
  border-radius: 16px;
  padding: 12px 16px;
  cursor: pointer;
  white-space: nowrap;
}

.primary-button {
  background: var(--base-color-brand-red);
  color: #000000;
}

.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.feedback.error {
  color: #7a1414;
}

.feedback:not(.error) {
  color: #103c25;
}

@media (max-width: 1100px) {
  .panel-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .actions {
    align-self: stretch;
  }
}

@media (max-width: 720px) {
  .panel-form,
  .episode-grid {
    grid-template-columns: 1fr;
  }

  .actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }
}
</style>
