<template>
  <div class="season-cover-panel">
    <button
      v-if="coverUrl && failedUrl !== coverUrl"
      type="button"
      class="cover-preview"
      :aria-label="`Ampliar capa de ${title}`"
      @click="coverViewer?.open(0)"
    >
      <img :src="resolveMediaUrl(coverUrl) || undefined" :alt="`Capa de ${title}`" @error="coverFailed" />
    </button>
    <div v-if="owner.authenticated" class="cover-actions">
      <button ref="chooseButton" type="button" class="action" :disabled="busy" @click="openSelector">
        {{ coverUrl ? 'Trocar capa' : 'Escolher capa' }}
      </button>
      <button v-if="coverUrl" type="button" class="action" :disabled="busy" @click="confirmRemove = !confirmRemove">
        Remover capa
      </button>
      <div v-if="confirmRemove" class="remove-confirmation">
        <p>Remover a capa desta temporada?</p>
        <button type="button" class="action" :disabled="busy" @click="remove">Confirmar remoção</button>
        <button type="button" class="action" :disabled="busy" @click="confirmRemove = false">Cancelar</button>
      </div>
    </div>
    <p v-if="message" role="status">{{ message }}</p>
    <p v-if="error && !selectorOpen" role="alert">{{ error }}</p>
    <dialog ref="dialog" class="cover-selector" aria-labelledby="season-cover-title" @close="closed" @cancel="cancel">
      <header>
        <div>
          <h2 id="season-cover-title">Escolher capa</h2>
          <p>{{ title }}</p>
        </div>
        <button type="button" class="action" autofocus :disabled="busy" @click="dialog?.close()">
          Voltar à temporada
        </button>
      </header>
      <p v-if="loading" role="status">Buscando pôsteres da temporada…</p>
      <div v-else-if="loadFailed" role="alert">
        <p>Não foi possível buscar as capas.</p>
        <button type="button" class="action" @click="load">Tentar novamente</button>
      </div>
      <template v-else>
        <p v-for="warning in warnings" :key="warning" role="status">{{ warning }}</p>
        <p v-if="!candidates.length && !warnings.length">Nenhum pôster disponível para esta temporada.</p>
        <fieldset v-if="candidates.length" :disabled="busy">
          <legend>Escolha uma capa para a temporada</legend>
          <div class="candidate-grid">
            <div v-for="(candidate, index) in candidates" :key="candidate.id" class="candidate">
              <button
                type="button"
                class="candidate-preview"
                :aria-label="`Ampliar opção ${index + 1}`"
                @click="candidateViewer?.open(index)"
              >
                <MediaImage :src="candidate.previewUrl" :alt="`Pôster ${index + 1} de ${title}`" loading="lazy" />
              </button>
              <label
                ><input v-model="selected" type="radio" name="season-cover" :value="candidate.id" /> Capa
                {{ index + 1 }}</label
              >
            </div>
          </div>
        </fieldset>
      </template>
      <footer>
        <p v-if="error" role="alert">{{ error }}</p>
        <button
          v-if="candidates.length && !loadFailed"
          type="button"
          class="action primary"
          :disabled="!selected || busy || loading"
          @click="save"
        >
          {{ busy ? 'Salvando…' : 'Salvar capa' }}
        </button>
      </footer>
    </dialog>
    <MediaImageGallery ref="coverViewer" :images="coverUrl ? [coverUrl] : []" :title="title" viewer-only />
    <MediaImageGallery ref="candidateViewer" :images="candidates.map((c) => c.url)" :title="title" viewer-only />
  </div>
</template>

<script setup lang="ts">
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
const props = defineProps<{ showId: number; seasonNumber: number; title: string; coverUrl: string | null }>()
const emit = defineEmits<{ changed: []; unavailable: [] }>()
const owner = useOwnerAuth()
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const chooseButton = ref<HTMLButtonElement>()
const dialog = ref<HTMLDialogElement>()
const coverViewer = ref<InstanceType<typeof MediaImageGallery>>()
const candidateViewer = ref<InstanceType<typeof MediaImageGallery>>()
const selectorOpen = ref(false)
const loading = ref(false)
const loadFailed = ref(false)
const busy = ref(false)
const confirmRemove = ref(false)
const selected = ref('')
const error = ref('')
const message = ref('')
const failedUrl = ref('')
const warnings = ref<string[]>([])
const candidates = ref<Array<{ id: string; url: string; previewUrl: string }>>([])
const endpoint = computed(() => `/api/shows/${props.showId}/seasons/${props.seasonNumber}/cover`)
let opener: HTMLElement | null = null
let oldOverflow = ''
function coverFailed() {
  failedUrl.value = props.coverUrl || ''
  emit('unavailable')
}
async function load() {
  loading.value = true
  loadFailed.value = false
  error.value = ''
  try {
    const result = await $fetch<{ images: typeof candidates.value; warnings: string[] }>(
      `${endpoint.value}/candidates`,
      { baseURL: config.public.apiBase },
    )
    candidates.value = result.images
    warnings.value = result.warnings
    if (!result.images.some((c) => c.id === selected.value)) selected.value = ''
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}
async function openSelector() {
  opener = document.activeElement as HTMLElement
  error.value = ''
  message.value = ''
  selectorOpen.value = true
  oldOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialog.value?.showModal()
  await load()
}
function cancel(event: Event) {
  if (busy.value) event.preventDefault()
}
function closed() {
  selectorOpen.value = false
  document.body.style.overflow = oldOverflow
  opener?.focus()
}
async function save() {
  if (!selected.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const result = await $fetch<{ savedIds: string[]; failedIds: string[] }>(endpoint.value, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { ids: [selected.value] },
    })
    if (!result.savedIds.includes(selected.value) || result.failedIds.length) throw new Error('save failed')
    emit('changed')
    message.value = 'Capa salva.'
    dialog.value?.close()
  } catch {
    error.value = 'Não foi possível salvar a capa. A capa anterior foi mantida. Tente novamente.'
  } finally {
    busy.value = false
  }
}
async function remove() {
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await $fetch(endpoint.value, { baseURL: config.public.apiBase, method: 'DELETE' })
    confirmRemove.value = false
    emit('changed')
    message.value = 'Capa removida.'
    await nextTick()
    chooseButton.value?.focus()
  } catch {
    error.value = 'Não foi possível remover a capa. Tente novamente.'
  } finally {
    busy.value = false
  }
}
onBeforeUnmount(() => {
  if (selectorOpen.value) document.body.style.overflow = oldOverflow
})
</script>

<style scoped>
.season-cover-panel {
  display: grid;
  gap: 12px;
  min-width: 0;
}
.cover-preview,
.candidate-preview {
  border: 0;
  padding: 0;
  background: transparent;
  cursor: zoom-in;
}
.cover-preview {
  justify-self: center;
  max-width: 100%;
}
.cover-preview img {
  display: block;
  max-width: 100%;
  width: auto;
  height: auto;
  max-height: 280px;
  border-radius: 20px;
}
.cover-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.remove-confirmation {
  flex-basis: 100%;
}
.action {
  min-height: 44px;
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  font: inherit;
  cursor: pointer;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}
.primary {
  background: var(--base-color-brand-red);
  color: white;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
button:focus-visible,
input:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
.cover-selector {
  width: min(1000px, calc(100vw - 32px));
  max-height: 90dvh;
  box-sizing: border-box;
  overflow: auto;
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  padding: 24px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
}
.cover-selector::backdrop {
  background: rgba(0, 0, 0, 0.55);
}
header {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 16px;
  flex-wrap: wrap;
}
h2 {
  margin: 0;
}
fieldset {
  border: 0;
  margin: 20px 0;
  padding: 0;
  min-width: 0;
}
legend {
  margin-bottom: 16px;
}
.candidate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 20px;
}
.candidate {
  display: grid;
  gap: 8px;
  align-content: start;
  min-width: 0;
}
.candidate-preview :deep(img) {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 16px;
}
label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  cursor: pointer;
}
footer {
  position: sticky;
  bottom: -24px;
  padding: 16px 0;
  background: var(--base-color-surface-strong);
}
@media (max-width: 576px) {
  .cover-selector {
    padding: 16px;
  }
  .candidate-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }
}
</style>
