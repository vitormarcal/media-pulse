<template>
  <form class="mag-panel mag-form" @submit.prevent="save">
    <h2>{{ savedId ? 'Adicionar capa' : issue ? 'Editar número' : 'Adicionar número' }}</h2>
    <fieldset class="mag-form" :disabled="saving">
      <fieldset class="mag-form" :disabled="savedId != null">
        <label class="mag-field"
          ><span>Publicação</span
          ><select v-model="publicationId">
            <option value="">Nova publicação</option>
            <option v-for="publication in publications" :key="publication.id" :value="publication.id">
              {{ publication.name }}
            </option>
          </select></label
        >
        <div v-if="!publicationId" class="mag-fields">
          <label
            ><span>Nome da revista</span
            ><input v-model="name" required maxlength="200" placeholder="Ex.: Superinteressante"
          /></label>
          <label><span>ISSN</span><input v-model="issn" maxlength="9" placeholder="0000-0000" /></label>
        </div>
        <div class="mag-fields">
          <label
            ><span>Número</span><input v-model="number" maxlength="100" :required="!coverDate" placeholder="Ex.: 475"
          /></label>
          <MagazineMonthField v-model="coverDate" :required="!number.trim()" />
        </div>
        <MagazineReadFields
          v-if="!issue"
          v-model="read"
          :total-pages="typeof totalPages === 'number' ? totalPages : null"
        />
      </fieldset>
      <details :open="Boolean(issue) || savedId != null">
        <summary>Capa e total de páginas</summary>
        <div class="mag-form">
          <label class="mag-field"
            ><span>Total de páginas</span
            ><input v-model.number="totalPages" :disabled="savedId != null" type="number" min="1" step="1"
          /></label>
          <label class="mag-field"
            ><span>Capa</span><input type="file" accept="image/jpeg,image/png" @change="chooseCover"
          /></label>
          <MediaImage v-if="preview" :src="preview" alt="Prévia da capa" class="mag-preview" />
          <button v-if="preview" type="button" class="mag-button" @click="removeImage">Remover capa</button>
        </div>
      </details>
      <p v-if="savedId" class="mag-feedback" role="status">Dados salvos. A capa ainda não foi enviada.</p>
      <NuxtLink v-if="savedId" class="mag-button" :to="`/magazines/${savedId}`">Abrir número salvo</NuxtLink>
      <div class="mag-actions">
        <button class="mag-button primary" type="submit">
          {{ saving ? 'Salvando…' : savedId ? 'Tentar capa novamente' : 'Salvar' }}</button
        ><button class="mag-button" type="button" @click="$emit('cancel')">Fechar</button>
      </div>
    </fieldset>
    <p v-if="error" class="mag-error" role="alert">{{ error }}</p>
  </form>
</template>
<script setup lang="ts">
import type { MagazineDetails, MagazineIssue, MagazinePublication, MagazineReadRequest } from '~/types/magazines'
import { magazineError, readPayload } from '~/utils/magazines'
import MagazineReadFields from './MagazineReadFields.vue'
import MagazineMonthField from './MagazineMonthField.vue'
const props = defineProps<{
  publications: MagazinePublication[]
  issue?: MagazineIssue
  initialPublicationId?: number
}>()
const emit = defineEmits<{ saved: [MagazineDetails]; cancel: [] }>()
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const publicationId = ref<number | ''>(props.issue?.publication.id || props.initialPublicationId || '')
const name = ref('')
const issn = ref('')
const number = ref(props.issue?.number || '')
const coverDate = ref(props.issue?.coverDate || '')
const totalPages = ref<number | ''>(props.issue?.totalPages || '')
const cover = shallowRef<File | null>(null)
const objectUrl = ref<string | null>(null)
const removeCover = ref(false)
const preview = computed(() => objectUrl.value || (!removeCover.value ? resolveMediaUrl(props.issue?.coverUrl) : null))
const read = ref<MagazineReadRequest>({ status: 'WANT_TO_READ', progressPct: 0 })
const saving = ref(false)
const error = ref('')
const savedId = ref<number | null>(null)
function releasePreview() {
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = null
}
function chooseCover(event: Event) {
  releasePreview()
  cover.value = (event.target as HTMLInputElement).files?.[0] || null
  objectUrl.value = cover.value ? URL.createObjectURL(cover.value) : null
  removeCover.value = false
}
function removeImage() {
  releasePreview()
  cover.value = null
  removeCover.value = true
}
onBeforeUnmount(releasePreview)
async function save() {
  saving.value = true
  error.value = ''
  try {
    if (savedId.value) {
      if (!cover.value) {
        error.value = 'Escolha uma capa para enviar.'
        return
      }
      const body = new FormData()
      body.append('cover', cover.value)
      const result = await $fetch<MagazineDetails>(`/api/magazines/${savedId.value}/cover`, {
        baseURL: config.public.apiBase,
        method: 'POST',
        body,
      })
      if (result.coverUploadError) error.value = result.coverUploadError
      else emit('saved', result)
      return
    }
    const body = new FormData()
    body.append(
      'issue',
      new Blob(
        [
          JSON.stringify({
            publicationId: publicationId.value || null,
            publication: publicationId.value ? null : { name: name.value, issn: issn.value || null },
            number: number.value || null,
            coverDate: coverDate.value || null,
            totalPages: typeof totalPages.value === 'number' ? totalPages.value : null,
            read: props.issue ? null : readPayload(read.value),
            removeCover: removeCover.value,
          }),
        ],
        { type: 'application/json' },
      ),
    )
    if (cover.value) body.append('cover', cover.value)
    const result = await $fetch<MagazineDetails>(props.issue ? `/api/magazines/${props.issue.id}` : '/api/magazines', {
      baseURL: config.public.apiBase,
      method: props.issue ? 'PUT' : 'POST',
      body,
    })
    if (result.coverUploadError) {
      savedId.value = result.issue.id
      error.value = result.coverUploadError
    } else emit('saved', result)
  } catch (cause) {
    error.value = magazineError(cause)
  } finally {
    saving.value = false
  }
}
</script>
<style scoped src="../../assets/css/magazines.css"></style>
