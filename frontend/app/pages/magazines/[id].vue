<template>
  <main class="mag-page mag-detail">
    <NuxtLink to="/magazines">← Revistas</NuxtLink>
    <div v-if="pending && !data" class="mag-state" role="status">Carregando revista…</div>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      Não foi possível carregar este número. <button class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <section class="mag-hero">
        <div class="mag-detail-copy">
          <p class="mag-eyebrow">Revista</p>
          <h1>{{ data.issue.publication.name }}</h1>
          <p>{{ magazineNumber(data.issue) }}</p>
          <NuxtLink :to="`/magazines?publicationId=${data.issue.publication.id}`"
            >Ver números desta publicação →</NuxtLink
          >
          <p v-if="data.issue.publication.issn">ISSN {{ data.issue.publication.issn }}</p>
          <p v-if="data.issue.totalPages">{{ data.issue.totalPages }} páginas</p>
          <p v-if="data.issue.latestRead">
            {{ magazineStates[data.issue.latestRead.status] }} · {{ Math.round(data.issue.latestRead.progressPct) }}%
          </p>
        </div>
        <div class="mag-cover">
          <img
            v-if="resolveMediaUrl(data.issue.coverUrl)"
            :src="resolveMediaUrl(data.issue.coverUrl)!"
            :alt="`${data.issue.publication.name} ${magazineNumber(data.issue)}`"
          /><span v-else class="mag-cover-fallback">{{ data.issue.publication.name.slice(0, 1) }}</span>
        </div>
      </section>
      <section class="mag-section">
        <SectionHeading eyebrow="Leitura" title="Suas páginas, seu ritmo" />
        <div class="mag-actions">
          <button class="mag-button primary" @click="openRead(openJourney)">
            {{
              openJourney ? 'Atualizar leitura' : data.reads.length ? 'Registrar releitura' : 'Registrar leitura'
            }}</button
          ><button class="mag-button" :aria-expanded="editIssue" @click="editIssue = !editIssue">Editar número</button
          ><button class="mag-button" :aria-expanded="editPublication" @click="openPublication">
            Editar publicação</button
          ><button class="mag-button" @click="deleteTarget = { kind: 'issue' }">Excluir número</button>
        </div>
        <MagazineReadEditor
          v-if="readEditorOpened"
          v-show="readEditorOpen"
          :key="editorKey"
          :issue="data.issue"
          :existing="editingRead"
          :reread="data.reads.length > 0 && !editingRead"
          @saved="savedRead"
          @cancel="readEditorOpen = false"
        />
        <MagazineIssueForm
          v-if="issueEditorOpened"
          v-show="editIssue"
          :issue="data.issue"
          :publications="publications || []"
          @saved="savedIssue"
          @cancel="editIssue = false"
        />
        <form v-if="editPublication" class="mag-panel mag-form" @submit.prevent="savePublication">
          <h2>Editar publicação</h2>
          <fieldset class="mag-fields" :disabled="saving">
            <label><span>Nome</span><input v-model="publicationName" required maxlength="200" /></label
            ><label><span>ISSN (opcional)</span><input v-model="publicationIssn" maxlength="9" /></label>
          </fieldset>
          <p class="mag-muted">A alteração vale para todos os números desta publicação.</p>
          <div class="mag-actions">
            <button class="mag-button primary" :disabled="saving">Salvar publicação</button
            ><button type="button" class="mag-button" @click="editPublication = false">Fechar</button>
          </div>
        </form>
        <div v-if="deleteTarget" class="mag-panel" role="alert">
          <p>
            {{
              deleteTarget.kind === 'issue'
                ? 'Excluir este número, todas as suas leituras e comentários?'
                : 'Excluir esta jornada de leitura? Os comentários serão preservados.'
            }}
          </p>
          <div class="mag-actions">
            <button class="mag-button" :disabled="saving" @click="confirmDelete">Confirmar exclusão</button
            ><button class="mag-button" :disabled="saving" @click="deleteTarget = null">Cancelar</button>
          </div>
        </div>
        <p v-if="actionError" class="mag-error" role="alert">{{ actionError }}</p>
        <p v-if="feedback" role="status">{{ feedback }}</p>
      </section>
      <MediaCommentsPanel
        :key="data.issue.id"
        media-type="magazines"
        :entity-id="data.issue.id"
        title="Comentários deste número"
        description=""
        :comments="data.comments"
        empty-label="Nenhum comentário."
      />
      <section class="mag-section">
        <SectionHeading eyebrow="Histórico" title="Jornadas de leitura" />
        <div v-if="data.reads.length" class="mag-timeline">
          <article v-for="(read, index) in data.reads" :key="read.id">
            <strong>{{ magazineStates[read.status] }} · {{ Math.round(read.progressPct) }}%</strong>
            <p>
              {{ index === data.reads.length - 1 ? 'Primeira jornada' : 'Releitura' }} ·
              {{ read.startedAt ? magazineDate(read.startedAt) : 'Ainda não iniciada'
              }}<template v-if="read.finishedAt"> → {{ magazineDate(read.finishedAt) }}</template>
            </p>
            <p v-if="read.currentPage != null">
              Página {{ read.currentPage
              }}<template v-if="data.issue.totalPages"> de {{ data.issue.totalPages }}</template>
            </p>
            <div class="mag-actions">
              <button class="mag-button" @click="openRead(read)">Editar leitura</button
              ><button class="mag-button" @click="deleteTarget = { kind: 'read', id: read.id }">Excluir leitura</button>
            </div>
          </article>
        </div>
        <p v-else class="mag-state">Nenhuma leitura registrada.</p>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MagazineIssueForm from '~/components/magazines/MagazineIssueForm.vue'
import MagazineReadEditor from '~/components/magazines/MagazineReadEditor.vue'
import type { MagazineDetails, MagazinePublication, MagazineRead } from '~/types/magazines'
import { magazineDate, magazineError, magazineNumber, magazineStates } from '~/utils/magazines'
const route = useRoute()
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
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
function openRead(read?: MagazineRead) {
  if (!readEditorOpened.value || editingRead.value?.id !== read?.id) {
    editingRead.value = read
    editorKey.value++
  }
  readEditorOpened.value = true
  readEditorOpen.value = true
  feedback.value = ''
}
async function savedRead(result: MagazineDetails) {
  data.value = result
  readEditorOpen.value = false
  readEditorOpened.value = false
  feedback.value = 'Leitura salva.'
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
