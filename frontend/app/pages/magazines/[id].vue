<template>
  <main class="mag-page mag-detail">
    <div v-if="pending && !data" class="mag-state" role="status">Carregando…</div>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      Não foi possível carregar a revista. <button class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <BookPageHero
        back-link="/magazines"
        back-label="Voltar para revistas"
        :title="data.issue.publication.name"
        :authors="[]"
        :subtitle="magazineNumber(data.issue)"
        :description="null"
        :cover-url="data.issue.coverUrl"
        :hero-meta="heroMeta"
      />
      <div class="mag-actions">
        <NuxtLink class="mag-button" :to="`/magazines?publicationId=${data.issue.publication.id}`"
          >Ver números</NuxtLink
        >
        <button class="mag-button primary" @click="openRead(openJourney)">
          {{ openJourney ? 'Atualizar leitura' : data.reads.length ? 'Reler' : 'Ler' }}
        </button>
        <button class="mag-button" :aria-expanded="editIssue" @click="editIssue = !editIssue">Editar número</button>
        <button class="mag-button" :aria-expanded="editPublication" @click="openPublication">Editar publicação</button>
      </div>
      <div v-if="readEditorOpened && !editingRead" v-show="readEditorOpen" id="magazine-read-editor-new">
        <MagazineReadEditor
          :key="editorKey"
          :issue="data.issue"
          :reread="data.reads.length > 0"
          @saved="savedRead"
          @cancel="readEditorOpen = false"
        />
      </div>
      <div v-if="issueEditorOpened" v-show="editIssue" class="mag-section">
        <MagazineIssueForm
          :issue="data.issue"
          :publications="publications || []"
          @saved="savedIssue"
          @cancel="editIssue = false"
        />
        <button class="mag-button mag-danger" @click="deleteTarget = { kind: 'issue' }">Excluir número</button>
        <div v-if="deleteTarget?.kind === 'issue'" class="mag-panel" role="alert">
          <p>Excluir este número, leituras e comentários?</p>
          <div class="mag-actions">
            <button class="mag-button" :disabled="saving" @click="confirmDelete">Excluir</button
            ><button class="mag-button" :disabled="saving" @click="deleteTarget = null">Cancelar</button>
          </div>
        </div>
      </div>
      <form v-if="editPublication" class="mag-panel mag-form" @submit.prevent="savePublication">
        <h2>Editar publicação</h2>
        <fieldset class="mag-fields" :disabled="saving">
          <label><span>Nome</span><input v-model="publicationName" required maxlength="200" /></label
          ><label><span>ISSN</span><input v-model="publicationIssn" maxlength="9" /></label>
        </fieldset>
        <small class="mag-muted">Altera todos os números desta publicação.</small>
        <div class="mag-actions">
          <button class="mag-button primary" :disabled="saving">Salvar</button
          ><button type="button" class="mag-button" @click="editPublication = false">Cancelar</button>
        </div>
      </form>
      <p v-if="actionError" class="mag-error" role="alert">{{ actionError }}</p>
      <p v-if="feedback" class="mag-feedback" role="status">{{ feedback }}</p>
      <section class="mag-reads" aria-labelledby="magazine-reads-title">
        <h2 id="magazine-reads-title">Leituras</h2>
        <article v-for="read in data.reads" :key="read.id" class="mag-read">
          <div class="mag-read-row">
            <div>
              <strong
                >{{ magazineStates[read.status]
                }}<template v-if="read.status === 'CURRENTLY_READING'">
                  · {{ Math.round(read.progressPct) }}%</template
                ></strong
              >
              <p>
                {{ read.startedAt ? magazineDate(read.startedAt) : 'Não iniciada'
                }}<template v-if="read.finishedAt"> → {{ magazineDate(read.finishedAt) }}</template
                ><template v-if="read.currentPage != null && read.status !== 'READ'">
                  · Página {{ read.currentPage }}</template
                >
              </p>
            </div>
            <div class="mag-actions">
              <button
                class="mag-button ghost"
                :aria-expanded="readEditorOpen && editingRead?.id === read.id"
                @click="openRead(read)"
              >
                Editar leitura</button
              ><button class="mag-button ghost" @click="deleteTarget = { kind: 'read', id: read.id }">Excluir</button>
            </div>
          </div>
          <div
            v-if="readEditorOpened && editingRead?.id === read.id"
            v-show="readEditorOpen"
            :id="`magazine-read-editor-${read.id}`"
          >
            <MagazineReadEditor
              :key="editorKey"
              :issue="data.issue"
              :existing="editingRead"
              @saved="savedRead"
              @cancel="readEditorOpen = false"
            />
          </div>
          <div v-if="deleteTarget?.kind === 'read' && deleteTarget.id === read.id" class="mag-delete" role="alert">
            <p>Excluir esta leitura?</p>
            <div class="mag-actions">
              <button class="mag-button" :disabled="saving" @click="confirmDelete">Excluir leitura</button
              ><button class="mag-button" :disabled="saving" @click="deleteTarget = null">Cancelar</button>
            </div>
          </div>
        </article>
        <p v-if="!data.reads.length" class="mag-muted">Nenhuma leitura.</p>
      </section>
      <MediaCommentsPanel
        :key="data.issue.id"
        compact
        media-type="magazines"
        :entity-id="data.issue.id"
        title="Comentários"
        description=""
        :comments="data.comments"
        empty-label="Nenhum comentário."
      />
    </template>
  </main>
</template>
<script setup lang="ts">
import BookPageHero from '~/components/books/BookPageHero.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MagazineIssueForm from '~/components/magazines/MagazineIssueForm.vue'
import MagazineReadEditor from '~/components/magazines/MagazineReadEditor.vue'
import type { MagazineDetails, MagazinePublication, MagazineRead } from '~/types/magazines'
import { magazineDate, magazineError, magazineNumber, magazineStates } from '~/utils/magazines'
const route = useRoute()
const config = useRuntimeConfig()
const { data, pending, error, refresh } = await useAsyncData(`magazine-${route.params.id}`, () =>
  $fetch<MagazineDetails>(`/api/magazines/${route.params.id}`, { baseURL: config.public.apiBase }),
)
const { data: publications, refresh: refreshPublications } = await useAsyncData('magazine-publications', () =>
  $fetch<MagazinePublication[]>('/api/magazines/publications', { baseURL: config.public.apiBase }),
)
const openJourney = computed(() =>
  data.value?.reads.find((read) => ['WANT_TO_READ', 'CURRENTLY_READING'].includes(read.status)),
)
const readEditorOpen = ref(false)
const readEditorOpened = ref(false)
const editingRead = ref<MagazineRead>()
const editorKey = ref(0)
const editIssue = ref(false)
const issueEditorOpened = ref(false)
watch(editIssue, (open) => {
  if (open) issueEditorOpened.value = true
})
const editPublication = ref(false)
const publicationName = ref('')
const publicationIssn = ref('')
const saving = ref(false)
const deleteTarget = ref<{ kind: 'issue' } | { kind: 'read'; id: number } | null>(null)
const actionError = ref('')
const feedback = ref('')
const heroMeta = computed(() => {
  if (!data.value) return []
  const issue = data.value.issue
  return [
    issue.publication.issn ? `ISSN ${issue.publication.issn}` : null,
    issue.totalPages ? `${issue.totalPages} páginas` : null,
    issue.latestRead ? magazineStates[issue.latestRead.status] : null,
  ].filter((item): item is string => Boolean(item))
})
async function openRead(read?: MagazineRead) {
  if (!readEditorOpened.value || editingRead.value?.id !== read?.id) {
    editingRead.value = read
    editorKey.value++
  }
  readEditorOpened.value = true
  readEditorOpen.value = true
  feedback.value = ''
  await nextTick()
  const editor = document.getElementById(`magazine-read-editor-${read?.id ?? 'new'}`)
  editor?.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    block: 'nearest',
  })
  editor?.querySelector<HTMLElement>('select, input')?.focus({ preventScroll: true })
}
async function savedRead(result: MagazineDetails) {
  data.value = result
  readEditorOpen.value = false
  readEditorOpened.value = false
  feedback.value = result.issue.latestRead?.status === 'READ' ? 'Leitura concluída.' : 'Leitura salva.'
}
async function savedIssue(result: MagazineDetails) {
  data.value = result
  editIssue.value = false
  issueEditorOpened.value = false
  readEditorOpened.value = false
  await refreshPublications()
  feedback.value = 'Número atualizado.'
}
function openPublication() {
  if (!editPublication.value) {
    publicationName.value = data.value?.issue.publication.name || ''
    publicationIssn.value = data.value?.issue.publication.issn || ''
  }
  editPublication.value = !editPublication.value
}
async function savePublication() {
  if (!data.value) return
  saving.value = true
  actionError.value = ''
  try {
    await $fetch(`/api/magazines/publications/${data.value.issue.publication.id}`, {
      baseURL: config.public.apiBase,
      method: 'PUT',
      body: { name: publicationName.value, issn: publicationIssn.value || null },
    })
    await Promise.all([refresh(), refreshPublications()])
    editPublication.value = false
    feedback.value = 'Publicação atualizada.'
  } catch (cause) {
    actionError.value = magazineError(cause)
  } finally {
    saving.value = false
  }
}
async function confirmDelete() {
  if (!deleteTarget.value || !data.value) return
  const target = deleteTarget.value
  saving.value = true
  actionError.value = ''
  try {
    await $fetch(`/api/magazines/${data.value.issue.id}${target.kind === 'read' ? `/reads/${target.id}` : ''}`, {
      baseURL: config.public.apiBase,
      method: 'DELETE',
    })
    if (target.kind === 'issue') await navigateTo('/magazines')
    else {
      readEditorOpen.value = false
      readEditorOpened.value = false
      await refresh()
      feedback.value = 'Leitura excluída.'
    }
    deleteTarget.value = null
  } catch (cause) {
    actionError.value = magazineError(cause)
  } finally {
    saving.value = false
  }
}
</script>
<style scoped src="../../assets/css/magazines.css"></style>
