<template>
  <section class="session-timeline">
    <SectionHeading
      eyebrow="Histórico"
      title="Sessões registradas"
      description="Cada sessão preserva um estado da relação com o jogo sem sobrescrever passagens anteriores."
      summary="Backlog, jogando, finalizado e abandonado podem aparecer mais de uma vez quando fizer sentido."
    />

    <div v-if="sessions.length" class="timeline-list">
      <article v-for="session in sessions" :key="session.id" class="timeline-item">
        <div class="timeline-marker" />
        <div class="timeline-body">
          <div class="timeline-header">
            <div>
              <p class="timeline-context">{{ session.statusLabel }}</p>
              <h3>{{ session.title }}</h3>
              <p v-if="session.notes" class="notes">{{ session.notes }}</p>
            </div>
            <div class="timeline-dates">
              <strong>{{ session.relativeStartedAt }}</strong>
              <span>{{ session.meta }}</span>
            </div>
          </div>
          <div class="timeline-actions">
            <button type="button" class="remove-button" :disabled="deletingId === session.sessionId" @click="remove(session)">
              {{ deletingId === session.sessionId ? 'Removendo...' : 'Remover sessão' }}
            </button>
          </div>
        </div>
      </article>
    </div>

    <article v-else class="empty-card">
      <p>Nenhuma sessão registrada para este game ainda.</p>
    </article>

    <p v-if="feedback" class="feedback" :class="{ error: feedbackError }">{{ feedback }}</p>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { GamePageData } from '~/types/games'

const props = defineProps<{
  gameId: number
  sessions: GamePageData['sessions']
}>()

const emit = defineEmits<{ deleted: [] }>()
const config = useRuntimeConfig()
const deletingId = ref<number | null>(null)
const feedback = ref<string | null>(null)
const feedbackError = ref(false)

async function remove(session: GamePageData['sessions'][number]) {
  deletingId.value = session.sessionId
  feedback.value = null
  feedbackError.value = false

  try {
    await $fetch(`/api/games/${props.gameId}/sessions/${session.sessionId}`, {
      baseURL: config.public.apiBase,
      method: 'DELETE',
    })
    feedback.value = 'Sessão removida.'
    emit('deleted')
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível remover a sessão.'
    feedbackError.value = true
  } finally {
    deletingId.value = null
  }
}
</script>

<style scoped>
.session-timeline,
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

.timeline-body,
.empty-card {
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
.timeline-dates span,
.notes,
.empty-card p {
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
}

.notes {
  margin-top: 8px;
  line-height: 1.55;
}

.timeline-dates {
  display: grid;
  justify-items: end;
  gap: 6px;
  text-align: right;
}

.timeline-actions {
  display: flex;
  justify-content: flex-end;
}

.remove-button {
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.feedback {
  margin: 0;
  color: #103c25;
}

.feedback.error {
  color: #7a1414;
}

@media (max-width: 680px) {
  .timeline-header {
    display: grid;
  }

  .timeline-dates {
    justify-items: start;
    text-align: left;
  }
}
</style>
