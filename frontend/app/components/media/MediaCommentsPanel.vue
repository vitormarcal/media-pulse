<template>
  <section class="comments-panel">
    <SectionHeading
      eyebrow="Comentários"
      :title="title"
      :description="description"
      summary="Impressões manuais com data própria, para registrar retornos e mudanças de leitura da obra ao longo do tempo."
    />

    <form class="composer-card" @submit.prevent="submitNewComment">
      <label class="field">
        <span class="field-label">Nova impressão</span>
        <textarea
          v-model="draftBody"
          class="textarea"
          rows="5"
          placeholder="Escreva o que ficou dessa experiência."
          :disabled="submitting"
        />
      </label>

      <label class="field field-compact">
        <span class="field-label">Data do comentário</span>
        <input v-model="draftCommentedAt" class="input" type="datetime-local" :disabled="submitting" />
      </label>

      <div class="actions">
        <button class="button button-primary" type="submit" :disabled="submitting || !draftBody.trim()">
          {{ submitting ? 'Salvando...' : 'Salvar comentário' }}
        </button>
        <p v-if="submitError" class="feedback error">{{ submitError }}</p>
      </div>
    </form>

    <div v-if="localComments.length" class="comment-list">
      <article v-for="comment in localComments" :key="comment.id" class="comment-card">
        <template v-if="editingId === comment.id">
          <label class="field">
            <span class="field-label">Editar comentário</span>
            <textarea v-model="editBody" class="textarea" rows="5" :disabled="savingEdit" />
          </label>

          <label class="field field-compact">
            <span class="field-label">Data do comentário</span>
            <input v-model="editCommentedAt" class="input" type="datetime-local" :disabled="savingEdit" />
          </label>

          <div class="actions">
            <button
              class="button button-primary"
              type="button"
              :disabled="savingEdit || !editBody.trim()"
              @click="saveEdit"
            >
              {{ savingEdit ? 'Salvando...' : 'Atualizar' }}
            </button>
            <button class="button button-secondary" type="button" :disabled="savingEdit" @click="cancelEdit">
              Cancelar
            </button>
            <p v-if="editError" class="feedback error">{{ editError }}</p>
          </div>
        </template>

        <template v-else>
          <div class="comment-header">
            <div>
              <p class="comment-date">{{ buildCommentLabel(comment) }}</p>
              <p v-if="comment.edited" class="comment-edited">Editado {{ formatRelativeDate(comment.updatedAt) }}</p>
            </div>

            <button class="button button-ghost" type="button" @click="startEdit(comment)">Editar</button>
          </div>

          <p
            v-for="(paragraph, index) in splitParagraphs(comment.body)"
            :key="`${comment.id}-${index}`"
            class="comment-paragraph"
          >
            {{ paragraph }}
          </p>
        </template>
      </article>
    </div>

    <article v-else class="empty-card">
      <p>{{ emptyLabel }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { MediaCommentDto } from '~/types/comments'
import { formatAbsoluteDate, formatRelativeDate } from '~/utils/formatting'

const props = defineProps<{
  title: string
  description: string
  mediaType: 'movies' | 'shows' | 'albums' | 'books'
  entityId: number
  comments: MediaCommentDto[]
  emptyLabel: string
}>()

const config = useRuntimeConfig()

const localComments = ref([...props.comments])
const draftBody = ref('')
const draftCommentedAt = ref(toLocalDateTimeInput(new Date().toISOString()))
const submitting = ref(false)
const submitError = ref<string | null>(null)

const editingId = ref<number | null>(null)
const editBody = ref('')
const editCommentedAt = ref('')
const savingEdit = ref(false)
const editError = ref<string | null>(null)

watch(
  () => props.comments,
  (nextComments) => {
    localComments.value = [...nextComments]
  },
)

function splitParagraphs(body: string) {
  return body
    .split(/\n\s*\n/g)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)
}

function buildCommentLabel(comment: MediaCommentDto) {
  return `${formatRelativeDate(comment.commentedAt)} · ${formatAbsoluteDate(comment.commentedAt)}`
}

function startEdit(comment: MediaCommentDto) {
  editingId.value = comment.id
  editBody.value = comment.body
  editCommentedAt.value = toLocalDateTimeInput(comment.commentedAt)
  editError.value = null
}

function cancelEdit() {
  editingId.value = null
  editBody.value = ''
  editCommentedAt.value = ''
  editError.value = null
}

async function submitNewComment() {
  const commentedAt = toIsoInstant(draftCommentedAt.value)
  if (!commentedAt) {
    submitError.value = 'Informe uma data e hora válidas.'
    return
  }

  submitting.value = true
  submitError.value = null

  try {
    const created = await $fetch<MediaCommentDto>(`/api/comments/${props.mediaType}/${props.entityId}`, {
      method: 'POST',
      baseURL: config.public.apiBase,
      body: {
        body: draftBody.value,
        commentedAt,
      },
    })

    localComments.value = sortComments([created, ...localComments.value])
    draftBody.value = ''
    draftCommentedAt.value = toLocalDateTimeInput(new Date().toISOString())
  } catch (error) {
    submitError.value = error instanceof Error ? error.message : 'Não foi possível salvar o comentário.'
  } finally {
    submitting.value = false
  }
}

async function saveEdit() {
  if (editingId.value == null) return
  const commentedAt = toIsoInstant(editCommentedAt.value)
  if (!commentedAt) {
    editError.value = 'Informe uma data e hora válidas.'
    return
  }

  savingEdit.value = true
  editError.value = null

  try {
    const updated = await $fetch<MediaCommentDto>(`/api/comments/${editingId.value}/edit`, {
      method: 'POST',
      baseURL: config.public.apiBase,
      body: {
        body: editBody.value,
        commentedAt,
      },
    })

    localComments.value = sortComments(
      localComments.value.map((comment) => (comment.id === updated.id ? updated : comment)),
    )
    cancelEdit()
  } catch (error) {
    editError.value = error instanceof Error ? error.message : 'Não foi possível atualizar o comentário.'
  } finally {
    savingEdit.value = false
  }
}

function toLocalDateTimeInput(value: string) {
  const date = new Date(value)
  const offset = date.getTimezoneOffset()
  const local = new Date(date.getTime() - offset * 60_000)
  return local.toISOString().slice(0, 16)
}

function toIsoInstant(value: string) {
  if (!value.trim()) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date.toISOString()
}

function sortComments(comments: MediaCommentDto[]) {
  return [...comments].sort((left, right) => {
    const byMoment = new Date(right.commentedAt).getTime() - new Date(left.commentedAt).getTime()
    if (byMoment !== 0) return byMoment
    return right.id - left.id
  })
}
</script>

<style scoped>
.comments-panel {
  display: grid;
  gap: 24px;
}

.composer-card,
.comment-card,
.empty-card {
  display: grid;
  gap: 18px;
  padding: clamp(22px, 3vw, 32px);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
}

.comment-list {
  display: grid;
  gap: 18px;
}

.field {
  display: grid;
  gap: 10px;
}

.field-compact {
  max-width: 18rem;
}

.field-label,
.comment-date,
.comment-edited {
  margin: 0;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field-label,
.comment-date {
  color: var(--base-color-brand-red);
}

.comment-edited {
  margin-top: 6px;
  color: var(--base-color-text-muted);
}

.textarea,
.input {
  width: 100%;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 72%, white);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.96);
  color: var(--base-color-text-primary);
  font: inherit;
}

.textarea {
  min-height: 9rem;
  padding: 14px 16px;
  resize: vertical;
  line-height: 1.65;
}

.input {
  padding: 11px 15px;
}

.textarea:focus,
.input:focus {
  outline: 3px solid color-mix(in srgb, var(--base-color-focus) 66%, white);
  outline-offset: 2px;
  border-color: color-mix(in srgb, var(--base-color-focus) 45%, var(--base-color-border));
}

.actions,
.comment-header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}

.button {
  border: 0;
  border-radius: 16px;
  padding: 8px 16px;
  font: inherit;
  cursor: pointer;
  transition:
    background-color 0.18s ease,
    color 0.18s ease,
    opacity 0.18s ease;
}

.button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.button-primary {
  background: var(--base-color-brand-red);
  color: #fff;
}

.button-secondary {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.button-ghost {
  background: transparent;
  color: var(--base-color-text-primary);
}

.comment-paragraph,
.empty-card p,
.feedback {
  margin: 0;
}

.comment-paragraph,
.empty-card p {
  max-width: 54rem;
  color: var(--base-color-text-primary);
  line-height: 1.72;
}

.feedback.error {
  color: #9e0a0a;
}

@media (max-width: 720px) {
  .field-compact {
    max-width: none;
  }

  .actions,
  .comment-header {
    align-items: stretch;
  }
}
</style>
