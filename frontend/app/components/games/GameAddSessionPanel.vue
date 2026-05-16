<template>
  <section class="add-session-panel">
    <SectionHeading
      eyebrow="Sessão"
      title="Registrar jornada"
      description="Use sessões para separar backlog, jogando, finalizado e abandonado, cada uma com início e fim próprios."
      summary="O modelo segue a ideia de temporadas e leituras: múltiplas passagens preservadas no tempo."
    />

    <article class="panel-card">
      <form class="panel-form" @submit.prevent="handleSubmit">
        <label class="field">
          <span>Status</span>
          <select v-model="status">
            <option value="PLAYING">Jogando</option>
            <option value="BACKLOG">Backlog</option>
            <option value="COMPLETED">Finalizado</option>
            <option value="ABANDONED">Abandonado</option>
          </select>
        </label>

        <label class="field">
          <span>Início</span>
          <input v-model="startedAtInput" type="datetime-local" />
        </label>

        <label class="field">
          <span>Fim</span>
          <input v-model="endedAtInput" type="datetime-local" />
        </label>

        <label class="field field-wide">
          <span>Notas</span>
          <textarea v-model="notes" rows="3" placeholder="Opcional" />
        </label>

        <div class="actions">
          <button type="button" class="secondary-button" :disabled="submitting" @click="setNow">Agora</button>
          <button type="submit" class="primary-button" :disabled="submitting || !startedAtInput">
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
import type { GameSessionCreateRequest, GameSessionCreateResponse, GameSessionStatus } from '~/types/games'

const props = defineProps<{ gameId: number }>()
const emit = defineEmits<{ created: [response: GameSessionCreateResponse] }>()

const config = useRuntimeConfig()
const status = ref<GameSessionStatus>('PLAYING')
const startedAtInput = ref(toDatetimeLocalValue(new Date()))
const endedAtInput = ref('')
const notes = ref('')
const submitting = ref(false)
const feedback = ref<string | null>(null)
const feedbackError = ref(false)

function toDatetimeLocalValue(date: Date) {
  const offset = date.getTimezoneOffset()
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 16)
}

function setNow() {
  startedAtInput.value = toDatetimeLocalValue(new Date())
}

function toIso(value: string) {
  if (!value.trim()) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

async function handleSubmit() {
  const startedAt = toIso(startedAtInput.value)
  if (!startedAt) return

  submitting.value = true
  feedback.value = null
  feedbackError.value = false

  try {
    const body: GameSessionCreateRequest = {
      status: status.value,
      startedAt,
      endedAt: toIso(endedAtInput.value),
      notes: notes.value.trim() || null,
    }
    const response = await $fetch<GameSessionCreateResponse>(`/api/games/${props.gameId}/sessions`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body,
    })
    feedback.value = 'Sessão adicionada ao histórico.'
    notes.value = ''
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
.add-session-panel,
.panel-card {
  display: grid;
  gap: 18px;
}

.panel-card {
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.panel-form {
  display: grid;
  grid-template-columns: 12rem 14rem 14rem minmax(0, 1fr) auto;
  gap: 14px;
  align-items: end;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: var(--base-color-text-secondary);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field input,
.field select,
.field textarea {
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
  font: inherit;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
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

.feedback {
  margin: 0;
  color: #103c25;
}

.feedback.error {
  color: #7a1414;
}

@media (max-width: 1100px) {
  .panel-form {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 680px) {
  .panel-form {
    grid-template-columns: 1fr;
  }
}
</style>
