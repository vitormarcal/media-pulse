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
            <div v-if="editingId !== session.sessionId">
              <p class="timeline-context">{{ session.statusLabel }}</p>
              <h3>{{ session.title }}</h3>
              <p v-if="session.notes" class="notes">{{ session.notes }}</p>
            </div>

            <form v-else class="edit-form" @submit.prevent="saveEdit(session)">
              <label class="field">
                <span>Status</span>
                <select v-model="editStatus">
                  <option value="PLAYING">Jogando</option>
                  <option value="BACKLOG">Backlog</option>
                  <option value="COMPLETED">Finalizado</option>
                  <option value="ABANDONED">Abandonado</option>
                </select>
              </label>

              <label class="field">
                <span>Início</span>
                <input v-model="editStartedAt" type="datetime-local" />
              </label>

              <label class="field">
                <span>Fim</span>
                <input v-model="editEndedAt" type="datetime-local" />
              </label>

              <label class="field field-wide">
                <span>Notas</span>
                <textarea v-model="editNotes" rows="3" />
              </label>

              <div class="edit-actions">
                <button type="submit" class="primary-button" :disabled="savingId === session.sessionId">
                  {{ savingId === session.sessionId ? 'Salvando...' : 'Salvar' }}
                </button>
                <button type="button" class="secondary-button" :disabled="savingId === session.sessionId" @click="cancelEdit">
                  Cancelar
                </button>
              </div>
            </form>

            <div class="timeline-dates">
              <strong>{{ session.relativeStartedAt }}</strong>
              <span>{{ session.meta }}</span>
            </div>
          </div>
          <div v-if="editingId !== session.sessionId" class="timeline-actions">
            <button type="button" class="edit-button" :disabled="deletingId === session.sessionId" @click="startEdit(session)">
              Editar
            </button>
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
import type { GamePageData, GameSessionStatus, GameSessionUpdateRequest } from '~/types/games'

const props = defineProps<{
  gameId: number
  sessions: GamePageData['sessions']
}>()

const emit = defineEmits<{ changed: [] }>()
const config = useRuntimeConfig()
const deletingId = ref<number | null>(null)
const editingId = ref<number | null>(null)
const savingId = ref<number | null>(null)
const editStatus = ref<GameSessionStatus>('PLAYING')
const editStartedAt = ref('')
const editEndedAt = ref('')
const editNotes = ref('')
const feedback = ref<string | null>(null)
const feedbackError = ref(false)

function startEdit(session: GamePageData['sessions'][number]) {
  feedback.value = null
  feedbackError.value = false
  editingId.value = session.sessionId
  editStatus.value = session.status
  editStartedAt.value = toDatetimeLocalValue(session.startedAt)
  editEndedAt.value = session.endedAt ? toDatetimeLocalValue(session.endedAt) : ''
  editNotes.value = session.notes ?? ''
}

function cancelEdit() {
  editingId.value = null
  editStartedAt.value = ''
  editEndedAt.value = ''
  editNotes.value = ''
}

async function saveEdit(session: GamePageData['sessions'][number]) {
  const startedAt = toIso(editStartedAt.value)
  if (!startedAt) {
    feedback.value = 'Informe uma data de início válida.'
    feedbackError.value = true
    return
  }

  savingId.value = session.sessionId
  feedback.value = null
  feedbackError.value = false

  try {
    const body: GameSessionUpdateRequest = {
      status: editStatus.value,
      startedAt,
      endedAt: toIso(editEndedAt.value),
      notes: editNotes.value.trim() || null,
    }
    await $fetch(`/api/games/${props.gameId}/sessions/${session.sessionId}`, {
      baseURL: config.public.apiBase,
      method: 'PATCH',
      body,
    })
    feedback.value = 'Sessão atualizada.'
    cancelEdit()
    emit('changed')
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível atualizar a sessão.'
    feedbackError.value = true
  } finally {
    savingId.value = null
  }
}

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
    emit('changed')
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível remover a sessão.'
    feedbackError.value = true
  } finally {
    deletingId.value = null
  }
}

function toDatetimeLocalValue(value: string) {
  const date = new Date(value)
  const offset = date.getTimezoneOffset()
  return new Date(date.getTime() - offset * 60_000).toISOString().slice(0, 16)
}

function toIso(value: string) {
  if (!value.trim()) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
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

.timeline-actions,
.edit-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.edit-form {
  display: grid;
  grid-template-columns: 11rem 13rem 13rem minmax(0, 1fr);
  gap: 12px;
  width: 100%;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: var(--base-color-text-secondary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field input,
.field select,
.field textarea {
  padding: 10px 12px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
  font: inherit;
}

.field-wide,
.edit-actions {
  grid-column: 1 / -1;
}

.remove-button,
.edit-button,
.primary-button,
.secondary-button {
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.remove-button,
.edit-button,
.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.primary-button {
  background: var(--base-color-brand-red);
  color: #000000;
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

  .edit-form {
    grid-template-columns: 1fr;
  }
}
</style>
