<template>
  <section class="watch-timeline">
    <SectionHeading
      eyebrow="Histórico"
      title="As sessões mais recentes"
      description="Uma linha do tempo curta para localizar quando esse filme voltou a aparecer."
      summary="Aqui a repetição também conta a história."
    />

    <div class="timeline-list">
      <article v-for="watch in watches" :key="watch.id" class="timeline-item">
        <div class="timeline-marker" />
        <div class="timeline-body">
          <div class="timeline-header">
            <div>
              <p class="timeline-context">{{ watch.source }}</p>
              <h3>{{ watch.title }}</h3>
            </div>
            <div class="timeline-dates">
              <strong>{{ watch.relativeWatchedAt }}</strong>
              <span>{{ watch.meta }}</span>
            </div>
          </div>
          <div class="timeline-actions">
            <button
              type="button"
              class="remove-button"
              :disabled="deletingWatchId === watch.watchId"
              @click="requestRemoval(watch)"
            >
              {{ deletingWatchId === watch.watchId ? 'Removendo...' : 'Remover sessão' }}
            </button>
          </div>
        </div>
      </article>
    </div>

    <p v-if="feedback" class="feedback" :class="{ error: feedbackError }">{{ feedback }}</p>

    <Teleport to="body">
      <div v-if="pendingRemoval" class="confirm-backdrop" role="presentation" @click.self="cancelRemoval">
        <section class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="remove-watch-title">
          <p class="confirm-eyebrow">Confirmar remoção</p>
          <h3 id="remove-watch-title">Remover esta sessão?</h3>
          <p>A sessão registrada em {{ pendingRemoval.meta }} será retirada do histórico deste filme.</p>

          <div class="confirm-actions">
            <button type="button" class="secondary-button" :disabled="deletingWatchId != null" @click="cancelRemoval">
              Manter sessão
            </button>
            <button type="button" class="danger-button" :disabled="deletingWatchId != null" @click="confirmRemoval">
              {{ deletingWatchId != null ? 'Removendo...' : 'Remover' }}
            </button>
          </div>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { MovieWatchEntryModel } from '~/types/movies'

const props = defineProps<{
  movieId: number
  watches: MovieWatchEntryModel[]
}>()

const emit = defineEmits<{
  deleted: []
}>()

const config = useRuntimeConfig()
const pendingRemoval = ref<MovieWatchEntryModel | null>(null)
const deletingWatchId = ref<number | null>(null)
const feedback = ref<string | null>(null)
const feedbackError = ref(false)

function requestRemoval(watch: MovieWatchEntryModel) {
  feedback.value = null
  feedbackError.value = false
  pendingRemoval.value = watch
}

function cancelRemoval() {
  if (deletingWatchId.value != null) return
  pendingRemoval.value = null
}

async function confirmRemoval() {
  if (!pendingRemoval.value) return

  deletingWatchId.value = pendingRemoval.value.watchId
  feedback.value = null
  feedbackError.value = false

  try {
    await $fetch(`/api/movies/${props.movieId}/watches/${pendingRemoval.value.watchId}`, {
      baseURL: config.public.apiBase,
      method: 'DELETE',
    })

    pendingRemoval.value = null
    feedback.value = 'Sessão removida do histórico.'
    emit('deleted')
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível remover a sessão.'
    feedbackError.value = true
  } finally {
    deletingWatchId.value = null
  }
}
</script>

<style scoped>
.watch-timeline,
.timeline-list {
  display: grid;
  gap: 24px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 16px;
}

.timeline-marker {
  width: 18px;
  height: 18px;
  margin-top: 14px;
  border-radius: 50%;
  background: var(--base-color-brand-red);
  box-shadow: 0 0 0 8px color-mix(in srgb, var(--base-color-brand-red) 8%, white);
}

.timeline-body {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
  border-radius: 26px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: start;
}

.timeline-context,
.timeline-dates span {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.timeline-context {
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h3 {
  margin: 6px 0 0;
  font-size: 1.14rem;
  line-height: 1.03;
}

.timeline-dates {
  display: grid;
  justify-items: end;
  gap: 6px;
  text-align: right;
}

.timeline-dates strong {
  font-size: 0.9rem;
}

.timeline-dates span {
  font-size: 0.84rem;
}

.timeline-actions {
  display: flex;
  justify-content: flex-end;
}

.remove-button,
.secondary-button,
.danger-button {
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.remove-button,
.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.danger-button {
  background: var(--base-color-brand-red);
  color: #000000;
}

.remove-button:disabled,
.secondary-button:disabled,
.danger-button:disabled {
  cursor: wait;
  opacity: 0.68;
}

.feedback {
  margin: 0;
  color: #103c25;
  line-height: 1.5;
}

.feedback.error {
  color: #7a1414;
}

.confirm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(33, 25, 34, 0.28);
}

.confirm-dialog {
  width: min(420px, 100%);
  display: grid;
  gap: 14px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 92%, var(--base-color-surface-soft));
  color: var(--base-color-text-primary);
}

.confirm-eyebrow,
.confirm-dialog p,
.confirm-dialog h3 {
  margin: 0;
}

.confirm-eyebrow {
  color: var(--base-color-brand-red);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.confirm-dialog h3 {
  font-size: 1.4rem;
  line-height: 1.05;
}

.confirm-dialog p {
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

@media (max-width: 820px) {
  .timeline-header {
    flex-direction: column;
  }

  .timeline-dates {
    justify-items: start;
    text-align: left;
  }

  .timeline-actions,
  .confirm-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
