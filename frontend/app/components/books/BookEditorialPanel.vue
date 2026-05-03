<template>
  <section class="editorial-panel">
    <SectionHeading
      eyebrow="Editorial"
      title="O que ficou escrito sobre este livro"
      description="Comentários manuais e review importado convivem no mesmo plano, ordenados por quando foram escritos."
      summary="Uma linha editorial única para releituras, mudanças de humor e anotações importadas."
    />

    <form class="composer-card" @submit.prevent="submitNewComment">
      <label class="field">
        <span class="field-label">Nova impressão</span>
        <textarea
          v-model="draftBody"
          class="textarea"
          rows="5"
          placeholder="Escreva o que ficou desta leitura."
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

    <div v-if="entries.length" class="entry-list">
      <article v-for="entry in entries" :key="entry.id" class="entry-card">
        <template v-if="entry.kind === 'manual' && editingId === entry.id">
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
          <div class="entry-header">
            <div class="entry-meta-block">
              <div class="entry-kicker-row">
                <p class="entry-kicker">{{ buildEntryLabel(entry) }}</p>
                <span :class="['origin-badge', `origin-badge--${entry.kind}`]">
                  {{ entry.kind === 'manual' ? 'Manual' : 'Hardcover' }}
                </span>
              </div>
              <p v-if="entry.kind === 'manual' && entry.edited" class="entry-edited">
                Editado {{ formatRelativeDate(entry.updatedAt) }}
              </p>
            </div>

            <button v-if="entry.kind === 'manual'" class="button button-ghost" type="button" @click="startEdit(entry)">
              Editar
            </button>
          </div>

          <p
            v-for="(paragraph, index) in splitParagraphs(entry.body)"
            :key="`${entry.id}-${index}`"
            class="entry-paragraph"
          >
            {{ paragraph }}
          </p>
        </template>
      </article>
    </div>

    <article v-else class="empty-card">
      <p>Nenhum texto editorial registrado para este livro ainda.</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { MediaCommentDto } from '~/types/comments'
import { formatAbsoluteDate, formatRelativeDate } from '~/utils/formatting'

type EditorialEntry =
  | {
      id: number
      kind: 'manual'
      body: string
      commentedAt: string
      createdAt: string
      updatedAt: string
      edited: boolean
    }
  | {
      id: string
      kind: 'hardcover'
      body: string
      commentedAt: string
      createdAt: string
      updatedAt: string
      edited: false
    }

const props = defineProps<{
  bookId: number
  reviewRaw: string | null
  reviewedAt: string | null
  comments: MediaCommentDto[]
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

const entries = computed(() => {
  const manualEntries: EditorialEntry[] = localComments.value.map((comment) => ({
    id: comment.id,
    kind: 'manual',
    body: comment.body,
    commentedAt: comment.commentedAt,
    createdAt: comment.createdAt,
    updatedAt: comment.updatedAt,
    edited: comment.edited,
  }))

  const hardcoverEntry: EditorialEntry[] =
    props.reviewRaw && props.reviewedAt
      ? [
          {
            id: `hardcover-${props.reviewedAt}`,
            kind: 'hardcover',
            body: props.reviewRaw,
            commentedAt: props.reviewedAt,
            createdAt: props.reviewedAt,
            updatedAt: props.reviewedAt,
            edited: false,
          },
        ]
      : []

  return [...manualEntries, ...hardcoverEntry].sort((left, right) => {
    const byMoment = new Date(right.commentedAt).getTime() - new Date(left.commentedAt).getTime()
    if (byMoment !== 0) return byMoment
    return String(right.id).localeCompare(String(left.id))
  })
})

function splitParagraphs(body: string) {
  return body
    .split(/\n\s*\n/g)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)
}

function buildEntryLabel(entry: EditorialEntry) {
  return `${formatRelativeDate(entry.commentedAt)} · ${formatAbsoluteDate(entry.commentedAt)}`
}

function startEdit(entry: Extract<EditorialEntry, { kind: 'manual' }>) {
  editingId.value = entry.id
  editBody.value = entry.body
  editCommentedAt.value = toLocalDateTimeInput(entry.commentedAt)
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
    const created = await $fetch<MediaCommentDto>(`/api/comments/books/${props.bookId}`, {
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
.editorial-panel {
  display: grid;
  gap: 24px;
}

.composer-card,
.entry-card,
.empty-card {
  display: grid;
  gap: 18px;
  padding: clamp(22px, 3vw, 32px);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
}

.entry-list {
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
.entry-kicker,
.entry-edited {
  margin: 0;
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field-label,
.entry-kicker {
  color: var(--base-color-brand-red);
}

.entry-edited {
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
.entry-header,
.entry-kicker-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}

.entry-meta-block {
  display: grid;
  gap: 2px;
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

.origin-badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.origin-badge--manual {
  background: color-mix(in srgb, var(--base-color-brand-red) 12%, white);
  color: var(--base-color-brand-red);
}

.origin-badge--hardcover {
  background: color-mix(in srgb, var(--base-color-surface-warm) 72%, white);
  color: var(--base-color-text-secondary);
}

.entry-paragraph,
.empty-card p,
.feedback {
  margin: 0;
}

.entry-paragraph,
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
  .entry-header {
    align-items: stretch;
  }
}
</style>
