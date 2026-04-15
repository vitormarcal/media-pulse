<template>
  <section class="add-watch-panel">
    <SectionHeading
      eyebrow="Sessão"
      title="Adicione uma sessão manual"
      description="Quando o filme entrou por cinema, lembrança ou histórico antigo, você ainda consegue posicionar essa sessão no tempo."
      summary="Uma ação curta para completar a linha do tempo sem depender só do Plex."
    />

    <article class="panel-card">
      <div class="panel-copy">
        <p class="panel-title">Registrar sessão</p>
        <p class="panel-body">Escolha a data e hora que fazem sentido para essa lembrança. O restante da página se atualiza logo depois.</p>
      </div>

      <form class="panel-form" @submit.prevent="handleSubmit">
        <label class="field">
          <span>Data e hora</span>
          <input v-model="watchedAtInput" type="datetime-local">
        </label>

        <div class="actions">
          <button type="button" class="secondary-button" :disabled="submitting" @click="setNow">
            Agora
          </button>
          <button type="submit" class="primary-button" :disabled="submitting || !watchedAtInput">
            {{ submitting ? 'Salvando...' : 'Adicionar sessão' }}
          </button>
        </div>
      </form>

      <p v-if="feedback" class="feedback" :class="{ error: feedbackError }">{{ feedback }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ExistingMovieWatchCreateRequest, ManualMovieWatchCreateResponse } from '~/types/movies'

const props = defineProps<{
  movieId: number
}>()

const emit = defineEmits<{
  created: [response: ManualMovieWatchCreateResponse]
}>()

const config = useRuntimeConfig()
const watchedAtInput = ref(toDatetimeLocalValue(new Date()))
const submitting = ref(false)
const feedback = ref<string | null>(null)
const feedbackError = ref(false)

function toDatetimeLocalValue(date: Date) {
  const offset = date.getTimezoneOffset()
  const localDate = new Date(date.getTime() - offset * 60000)
  return localDate.toISOString().slice(0, 16)
}

function setNow() {
  watchedAtInput.value = toDatetimeLocalValue(new Date())
}

async function handleSubmit() {
  submitting.value = true
  feedback.value = null
  feedbackError.value = false

  try {
    const body: ExistingMovieWatchCreateRequest = {
      watchedAt: new Date(watchedAtInput.value).toISOString(),
    }

    const response = await $fetch<ManualMovieWatchCreateResponse>(`/api/movies/${props.movieId}/watches`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body,
    })

    feedback.value = response.watchInserted ? 'Sessão adicionada ao histórico.' : 'Essa sessão já existia no histórico.'
    emit('created', response)
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível registrar a sessão.'
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
  grid-template-columns: minmax(0, 18rem) auto;
  gap: 14px;
  align-items: end;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.field input {
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
  border: 0;
  border-radius: 16px;
  padding: 12px 16px;
  cursor: pointer;
}

.primary-button {
  background: var(--base-color-brand-red);
  color: #000000;
}

.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.feedback.error {
  color: #7a1414;
}

.feedback:not(.error) {
  color: #103c25;
}

@media (max-width: 720px) {
  .panel-form {
    grid-template-columns: 1fr;
  }

  .actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
