<template>
  <main class="mag-page mag-detail">
    <div v-if="pending && !data" class="mag-state" role="status">Carregando…</div>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      Não foi possível carregar a revista. <button class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <MagazineHeader
        :title="data.issue.publication.name"
        :title-href="`/magazines/publications/${data.issue.publication.id}`"
        :subtitle="magazineNumber(data.issue)"
        :cover-url="data.issue.coverUrl"
        :back-link="`/magazines/publications/${data.issue.publication.id}`"
        back-label="Voltar para a publicação"
        zoom
      />
      <section v-if="data.reads.length || isOwner" class="mag-reads" aria-labelledby="magazine-reads-title">
        <div class="mag-section-heading">
          <h2 id="magazine-reads-title">Leituras</h2>
          <button v-if="isOwner" class="mag-button primary" @click="openRead(openJourney)">
            {{ openJourney ? 'Atualizar leitura' : data.reads.length ? 'Reler' : 'Ler' }}
          </button>
        </div>
        <div v-if="isOwner && readEditorOpened && !editingRead" v-show="readEditorOpen" id="magazine-read-editor-new">
          <MagazineReadEditor
            :key="editorKey"
            :issue="data.issue"
            :reread="data.reads.length > 0"
            @saved="savedRead"
            @cancel="readEditorOpen = false"
          />
        </div>
        <p v-if="feedback && feedbackTarget === 'read'" class="mag-feedback" role="status">{{ feedback }}</p>
        <article v-for="read in data.reads" :key="read.id" class="mag-read">
          <div class="mag-read-row">
            <div>
              <strong
                >{{ magazineStates[read.status]
                }}<template
                  v-if="read.status === 'CURRENTLY_READING' && read.currentPage == null && read.progressPct > 0"
                >
                  · {{ Math.round(read.progressPct) }}%</template
                ></strong
              >
              <p v-if="read.startedAt || read.finishedAt || read.currentPage != null">
                <template v-if="read.startedAt">Início {{ magazineDate(read.startedAt) }}</template
                ><template v-if="read.finishedAt"> · Fim {{ magazineDate(read.finishedAt) }}</template
                ><template v-if="read.currentPage != null && read.status !== 'READ'">
                  · Página {{ read.currentPage
                  }}<template v-if="data.issue.totalPages"> de {{ data.issue.totalPages }}</template></template
                >
              </p>
            </div>
            <div class="mag-actions">
              <button
                v-owner
                class="mag-button ghost"
                :aria-expanded="readEditorOpen && editingRead?.id === read.id"
                @click="openRead(read)"
              >
                Editar leitura</button
              ><button v-owner class="mag-button ghost" @click="deleteTarget = { kind: 'read', id: read.id }">
                Excluir
              </button>
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
            <p>Excluir esta leitura? Os comentários serão preservados.</p>
            <p v-if="actionError" class="mag-error" role="alert">{{ actionError }}</p>
            <div class="mag-actions">
              <button v-owner class="mag-button" :disabled="saving" @click="confirmDelete">Excluir leitura</button
              ><button v-owner class="mag-button" :disabled="saving" @click="deleteTarget = null">Cancelar</button>
            </div>
          </div>
        </article>
        <p v-if="!data.reads.length && isOwner" class="mag-muted">Nenhuma leitura.</p>
      </section>
      <MediaCommentsPanel
        v-if="isOwner || data.comments.length"
        :key="data.issue.id"
        compact
        editorial
        media-type="magazines"
        :entity-id="data.issue.id"
        title="Comentários"
        description=""
        :comments="data.comments"
        empty-label="Nenhum comentário."
      />
      <section class="mag-panel mag-about">
        <h2>Sobre o número</h2>
        <p v-if="feedback && feedbackTarget === 'about'" class="mag-feedback" role="status">{{ feedback }}</p>
        <p v-if="data.issue.totalPages">{{ data.issue.totalPages }} páginas</p>
        <p v-if="data.issue.publication.issn" class="mag-muted">ISSN {{ data.issue.publication.issn }}</p>
        <NuxtLink :to="`/magazines/publications/${data.issue.publication.id}`"
          >Ver todos os números de {{ data.issue.publication.name }}</NuxtLink
        >
        <div v-if="isOwner" class="mag-actions">
          <button class="mag-button" :aria-expanded="editIssue" @click="editIssue = !editIssue">
            {{ editIssue ? 'Fechar edição do número' : 'Editar número' }}</button
          ><button class="mag-button" :aria-expanded="editPublication" @click="editPublication = !editPublication">
            {{ editPublication ? 'Fechar edição da publicação' : 'Editar publicação' }}
          </button>
        </div>
        <div v-if="isOwner && issueEditorOpened" v-show="editIssue" class="mag-section">
          <MagazineIssueForm
            :issue="data.issue"
            :publications="publications || []"
            @saved="savedIssue"
            @cancel="editIssue = false"
          />
          <button v-owner class="mag-button mag-danger" @click="deleteTarget = { kind: 'issue' }">
            Excluir número
          </button>
          <div v-if="deleteTarget?.kind === 'issue'" class="mag-panel" role="alert">
            <p>Excluir este número, leituras e comentários?</p>
            <p v-if="actionError" class="mag-error" role="alert">{{ actionError }}</p>
            <div class="mag-actions">
              <button v-owner class="mag-button" :disabled="saving" @click="confirmDelete">Excluir</button
              ><button v-owner class="mag-button" :disabled="saving" @click="deleteTarget = null">Cancelar</button>
            </div>
          </div>
        </div>
        <MagazinePublicationEditor
          v-if="isOwner"
          v-show="editPublication"
          :key="data.issue.publication.id"
          :publication="data.issue.publication"
          @saved="savedPublication"
          @cancel="editPublication = false"
        />
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import MagazineHeader from '~/components/magazines/MagazineHeader.vue'
import MagazinePublicationEditor from '~/components/magazines/MagazinePublicationEditor.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MagazineIssueForm from '~/components/magazines/MagazineIssueForm.vue'
import MagazineReadEditor from '~/components/magazines/MagazineReadEditor.vue'
import type { MagazineDetails, MagazinePublication, MagazineRead } from '~/types/magazines'
import { magazineDate, magazineError, magazineNumber, magazineStates } from '~/utils/magazines'
definePageMeta({ key: (route) => String(route.params.id) })
const owner = useOwnerAuth()
const isOwner = computed(() => owner.value.authenticated)
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
const saving = ref(false)
const deleteTarget = ref<{ kind: 'issue' } | { kind: 'read'; id: number } | null>(null)
const actionError = ref('')
const feedback = ref('')
const feedbackTarget = ref<'read' | 'about'>('read')
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
  feedbackTarget.value = 'read'
  feedback.value = result.issue.latestRead?.status === 'READ' ? 'Leitura concluída.' : 'Leitura salva.'
}
async function savedIssue(result: MagazineDetails) {
  data.value = result
  editIssue.value = false
  issueEditorOpened.value = false
  readEditorOpened.value = false
  await refreshPublications()
  feedbackTarget.value = 'about'
  feedback.value = 'Número atualizado.'
}
async function savedPublication() {
  await Promise.all([refresh(), refreshPublications()])
  editPublication.value = false
  feedbackTarget.value = 'about'
  feedback.value = 'Publicação atualizada.'
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
      feedbackTarget.value = 'read'
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
