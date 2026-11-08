<template>
  <section class="images-panel">
    <SectionHeading eyebrow="Catálogo" title="Adicionar imagens" />
    <div class="launch-card">
      <p>Explore capas, fundos e imagens do jogo. A capa atual será mantida.</p>
      <button v-owner type="button" class="primary-button" :disabled="saving" @click="openSelector">
        {{ selected.length ? `Continuar seleção (${selected.length})` : 'Escolher imagens' }}
      </button>
      <p v-if="message && !selectorOpen" class="feedback" role="status">{{ message }}</p>
      <p v-if="error && !selectorOpen" class="feedback error" role="alert">{{ error }}</p>
    </div>
    <dialog ref="selector" class="image-selector" aria-labelledby="image-selector-title" @close="selectorClosed">
      <div class="selector-layout">
        <header class="selector-header">
          <div class="selector-heading">
            <p class="eyebrow">{{ gameTitle || 'Imagens do jogo' }}</p>
            <h2 id="image-selector-title">Escolher imagens</h2>
            <p class="selector-description">Amplie para conferir os detalhes e marque o que quer guardar.</p>
          </div>
          <button type="button" class="secondary-button return-button" autofocus @click="selector?.close()">
            Voltar ao jogo <span aria-hidden="true">✕</span>
          </button>
        </header>
        <div ref="selectorScroll" class="selector-scroll" :aria-busy="loading">
          <div class="selector-content">
            <div class="options-toolbar">
              <p class="options-count" role="status">
                {{
                  loading
                    ? 'Buscando imagens…'
                    : `${candidates.length} ${candidates.length === 1 ? 'opção disponível' : 'opções disponíveis'}`
                }}
              </p>
              <button type="button" class="text-button" :disabled="busy" @click="loadImages">Atualizar opções</button>
            </div>
            <p v-if="lookupAvailable === false" class="lookup-notice" role="status">
              Não foi possível conferir o acervo. Você pode continuar escolhendo.
            </p>
            <p v-for="warning in warnings" :key="warning" class="lookup-notice" role="status">{{ warning }}</p>
            <div v-if="loading && !candidates.length" class="candidate-grid skeleton-grid" aria-hidden="true">
              <div v-for="index in 6" :key="index" class="candidate-skeleton" />
            </div>
            <div v-else-if="!loading && !candidates.length" class="empty-state">
              <h3>{{ error ? 'Não foi possível buscar imagens' : 'Nenhuma imagem encontrada' }}</h3>
              <p>Você pode tentar buscar novamente.</p>
              <button type="button" class="secondary-button" :disabled="busy" @click="loadImages">
                Tentar novamente
              </button>
            </div>
            <div v-else ref="candidateGrid" class="candidate-grid">
              <article
                v-for="(image, index) in candidates"
                :key="image.id"
                :data-candidate-id="image.id"
                class="candidate"
                :class="{ selected: selected.includes(image.id) }"
              >
                <div class="candidate-visual">
                  <button
                    type="button"
                    class="candidate-preview"
                    :aria-label="`Ampliar opção ${index + 1}: ${kindLabel(image.kind)}`"
                    @click="openCandidate(index)"
                  >
                    <img
                      :src="image.previewUrl"
                      :alt="`${kindLabel(image.kind)} — ${providerLabel(image.provider)}`"
                      loading="lazy"
                      referrerpolicy="no-referrer"
                    />
                    <span class="preview-hint" aria-hidden="true">Ampliar ↗</span>
                  </button>
                  <label class="candidate-toggle" :class="{ chosen: selected.includes(image.id) }">
                    <input
                      v-model="selected"
                      type="checkbox"
                      :value="image.id"
                      :disabled="selectionDisabled(image.id)"
                      :aria-label="`Selecionar imagem ${index + 1}: ${kindLabel(image.kind)}`"
                    />
                  </label>
                </div>
                <div class="candidate-info">
                  <div class="candidate-caption">
                    <h3>{{ kindLabel(image.kind) }}</h3>
                    <span v-if="selected.includes(image.id)" class="selected-label">Selecionada</span>
                  </div>
                  <p class="candidate-source">
                    {{ providerLabel(image.provider)
                    }}<span v-if="image.width && image.height"> · {{ image.width }} × {{ image.height }}</span>
                  </p>
                  <div class="candidate-state"><GameImageStateBadge :state="badgeState(image.id)" /></div>
                </div>
              </article>
            </div>
          </div>
        </div>
        <footer class="selector-footer">
          <div
            v-if="error || message"
            ref="saveFeedback"
            tabindex="-1"
            class="save-feedback"
            :class="{ error }"
            :role="error ? 'alert' : 'status'"
          >
            {{ error || message }}
          </div>
          <div class="selection-summary">
            <strong>{{
              selected.length
                ? `${selected.length} ${selected.length === 1 ? 'imagem selecionada' : 'imagens selecionadas'}`
                : 'Nenhuma imagem selecionada'
            }}</strong
            ><span>{{
              selected.length >= 50 ? 'Limite de 50 imagens por vez.' : 'As escolhas só serão guardadas ao salvar.'
            }}</span>
          </div>
          <div class="footer-actions">
            <button
              v-if="selected.length"
              type="button"
              class="text-button clear-button"
              :disabled="busy"
              @click="clearSelection"
            >
              Limpar seleção
            </button>
            <button
              v-owner
              type="button"
              class="primary-button save-button"
              :disabled="busy || !selected.length"
              @click="saveImages"
            >
              {{
                saving
                  ? 'Salvando…'
                  : selected.length
                    ? `Salvar ${selected.length} ${selected.length === 1 ? 'imagem' : 'imagens'}`
                    : 'Salvar imagens'
              }}
            </button>
          </div>
        </footer>
      </div>
      <MediaImageGallery
        ref="candidateViewer"
        :images="candidates.map((image) => image.url)"
        :preview-images="candidates.map((image) => image.previewUrl)"
        title="Imagem ampliada"
        close-label="Voltar à seleção"
        viewer-only
        @image-change="queueLookup(candidates[$event]?.id)"
      >
        <template #selection="{ index }">
          <div v-if="candidates[index]" class="preview-lookup">
            <GameImageStateBadge :state="badgeState(candidates[index]!.id)" dark />
            <label class="preview-selection" :class="{ chosen: selected.includes(candidates[index]!.id) }">
              <input
                v-model="selected"
                type="checkbox"
                :value="candidates[index]!.id"
                :disabled="selectionDisabled(candidates[index]!.id)"
              />
              {{ selected.includes(candidates[index]!.id) ? 'Selecionada' : 'Selecionar imagem' }}
            </label>
          </div>
        </template>
      </MediaImageGallery>
    </dialog>
  </section>
</template>
<script setup lang="ts">
import GameImageStateBadge from '~/components/games/GameImageStateBadge.vue'
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
interface Candidate {
  id: string
  provider: string
  kind: string
  url: string
  previewUrl: string
  width: number | null
  height: number | null
}
const props = defineProps<{ gameId: number; gameTitle?: string }>()
const emit = defineEmits<{ imported: [] }>()
const candidateViewer = ref<InstanceType<typeof MediaImageGallery> | null>(null)
const config = useRuntimeConfig()
const loading = ref(false)
const saving = ref(false)
const busy = computed(() => loading.value || saving.value)
const opened = ref(false)
const error = ref('')
const message = ref('')
const warnings = ref<string[]>([])
const candidates = ref<Candidate[]>([])
const selected = ref<string[]>([])
type Existence = 'CHECKING' | 'IN_GALLERY' | 'IN_LIBRARY' | 'NOT_FOUND' | 'UNAVAILABLE'
const existence = ref<Record<string, Existence>>({})
const lookupAvailable = ref<boolean | null>(null)
const candidateGrid = ref<HTMLElement>()
const selector = ref<HTMLDialogElement>()
const selectorScroll = ref<HTMLElement>()
const selectorOpen = ref(false)
const saveFeedback = ref<HTMLElement>()
let selectorOpener: HTMLElement | null = null
let previousOverflow = ''
function openSelector() {
  if (!selector.value || selector.value.open) return
  selectorOpener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  selectorOpen.value = true
  selector.value.showModal()
  if (!opened.value && !loading.value) void loadImages()
  else void nextTick(observeOptions)
}
function selectorClosed() {
  selectorOpen.value = false
  document.body.style.overflow = previousOverflow
  observer?.disconnect()
  lookupGeneration++
  pendingLookups.clear()
  existence.value = Object.fromEntries(Object.entries(existence.value).filter(([, state]) => state !== 'CHECKING'))
  if (selectorOpener?.isConnected) selectorOpener.focus()
}
function trapSelectorFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab' || !selectorOpen.value || !selector.value?.open) return
  const activeDialog = document.activeElement?.closest('dialog')
  if (activeDialog && activeDialog !== selector.value) return
  const controls = Array.from(
    selector.value?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled)') ?? [],
  ).filter((element) => element.closest('dialog') === selector.value && element.getClientRects().length > 0)
  const first = controls[0]
  const last = controls.at(-1)
  if (!controls.includes(document.activeElement as HTMLElement)) {
    event.preventDefault()
    if (event.shiftKey) last?.focus()
    else first?.focus()
  } else if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}
let observer: IntersectionObserver | undefined
let lookupGeneration = 0
let lookupRunning = false
let disposed = false
const pendingLookups = new Set<string>()
const savedInSearch = new Set<string>()
function badgeState(id: string) {
  const state = existence.value[id]
  if (lookupAvailable.value === false && (state === 'CHECKING' || state === 'UNAVAILABLE')) return undefined
  return state
}
function queueLookup(id?: string) {
  if (!selectorOpen.value || !id || existence.value[id] || lookupAvailable.value === false || disposed) return
  existence.value[id] = 'CHECKING'
  pendingLookups.add(id)
  void checkVisible()
}
async function checkVisible() {
  if (lookupRunning || !pendingLookups.size || disposed || !selectorOpen.value) return
  lookupRunning = true
  const generation = lookupGeneration
  try {
    while (pendingLookups.size && !disposed && selectorOpen.value && lookupAvailable.value !== false) {
      const ids = [...pendingLookups].slice(0, 4)
      for (const id of ids) pendingLookups.delete(id)
      try {
        const response = await $fetch<{
          available: boolean
          results: { id: string; state: Exclude<Existence, 'CHECKING'> }[]
        }>(`/api/games/${props.gameId}/images/lookup`, {
          baseURL: config.public.apiBase,
          method: 'POST',
          body: { ids },
        })
        if (disposed || generation !== lookupGeneration) return
        lookupAvailable.value = response.available
        for (const id of ids) {
          const state = response.results.find((result) => result.id === id)?.state ?? 'UNAVAILABLE'
          existence.value[id] = savedInSearch.has(id) && state !== 'UNAVAILABLE' ? 'IN_GALLERY' : state
        }
        if (!response.available) pendingLookups.clear()
      } catch {
        if (disposed || generation !== lookupGeneration) return
        lookupAvailable.value = false
        pendingLookups.clear()
      }
    }
  } finally {
    lookupRunning = false
    if (pendingLookups.size && !disposed && selectorOpen.value) void checkVisible()
  }
}
function observeOptions() {
  observer?.disconnect()
  if (!selectorOpen.value || typeof IntersectionObserver === 'undefined') return
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries)
        if (entry.isIntersecting) {
          observer?.unobserve(entry.target)
          queueLookup((entry.target as HTMLElement).dataset.candidateId)
        }
    },
    { root: selectorScroll.value, rootMargin: '160px' },
  )
  for (const element of candidateGrid.value?.querySelectorAll<HTMLElement>('[data-candidate-id]') ?? [])
    observer.observe(element)
}
function openCandidate(index: number) {
  queueLookup(candidates.value[index]?.id)
  candidateViewer.value?.open(index)
}
onMounted(() => window.addEventListener('keydown', trapSelectorFocus, true))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', trapSelectorFocus, true)
  disposed = true
  lookupGeneration++
  observer?.disconnect()
  pendingLookups.clear()
  if (selector.value?.open) document.body.style.overflow = previousOverflow
})
function clearSelection() {
  selected.value = []
  error.value = ''
  message.value = ''
  void nextTick(() => selector.value?.querySelector<HTMLButtonElement>('.return-button')?.focus())
}
const selectionDisabled = (id: string) => busy.value || (selected.value.length >= 50 && !selected.value.includes(id))
const kindLabel = (kind: string) =>
  ({
    GRID: 'Capa vertical',
    GRID_HORIZONTAL: 'Capa horizontal',
    COVER: 'Capa',
    HERO: 'Fundo',
    SCREENSHOT: 'Screenshot',
    ARTWORK: 'Arte promocional',
  })[kind] ?? kind
const providerLabel = (provider: string) => (provider === 'IGDB' ? 'IGDB' : 'SteamGridDB')
function errorMessage(cause: unknown, fallback: string) {
  const data = (cause as { data?: { detail?: string; message?: string } }).data
  return data?.detail || data?.message || fallback
}
async function loadImages() {
  if (busy.value) return
  loading.value = true
  lookupGeneration++
  observer?.disconnect()
  pendingLookups.clear()
  savedInSearch.clear()
  existence.value = {}
  lookupAvailable.value = null
  error.value = ''
  message.value = ''
  try {
    const result = await $fetch<{ images: Candidate[]; warnings: string[] }>(
      `/api/games/${props.gameId}/images/candidates`,
      { baseURL: config.public.apiBase },
    )
    candidates.value = result.images
    warnings.value = result.warnings
    selected.value = selected.value.filter((id) => result.images.some((image) => image.id === id))
    opened.value = true
    await nextTick()
    if (selectorScroll.value) selectorScroll.value.scrollTop = 0
    observeOptions()
  } catch (cause) {
    error.value = errorMessage(cause, 'Não foi possível buscar as imagens. Tente novamente.')
  } finally {
    loading.value = false
  }
}
async function saveImages() {
  if (busy.value || !selected.value.length) return
  saving.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await $fetch<{ savedIds: string[]; failedIds: string[] }>(
      `/api/games/${props.gameId}/images/selection`,
      {
        baseURL: config.public.apiBase,
        method: 'POST',
        body: { ids: selected.value },
      },
    )
    selected.value = result.failedIds
    for (const id of result.savedIds) {
      savedInSearch.add(id)
      if (lookupAvailable.value === true) existence.value[id] = 'IN_GALLERY'
    }
    message.value = `${result.savedIds.length} ${result.savedIds.length === 1 ? 'imagem salva' : 'imagens salvas'}.`
    if (result.failedIds.length)
      error.value = `${message.value} ${result.failedIds.length} ${result.failedIds.length === 1 ? 'imagem não pôde ser salva' : 'imagens não puderam ser salvas'}. Tente novamente.`
    if (result.savedIds.length) emit('imported')
  } catch (cause) {
    error.value = errorMessage(cause, 'Não foi possível guardar as imagens. Tente novamente.')
  } finally {
    saving.value = false
    await nextTick()
    if (
      selectorOpen.value &&
      (document.activeElement?.closest('dialog') === selector.value || document.activeElement === document.body)
    )
      saveFeedback.value?.focus()
  }
}
</script>
<style scoped>
.images-panel {
  display: grid;
  gap: 18px;
}
p,
h2,
h3 {
  margin: 0;
}
button {
  font: inherit;
}
.launch-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 24px;
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-wash);
}
.launch-card > p:first-child {
  max-width: 42rem;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
.primary-button,
.secondary-button {
  min-height: 44px;
  padding: 10px 18px;
  border: 0;
  border-radius: 16px;
  font-size: 0.88rem;
  font-weight: 600;
  cursor: pointer;
}
.primary-button {
  background: var(--base-color-brand-red);
  color: #fff;
}
.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
button:focus-visible,
.candidate-toggle:focus-within,
.preview-selection:focus-within {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 4px;
}
.image-selector {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100dvh;
  max-width: none;
  max-height: none;
  margin: 0;
  padding: 0;
  border: 0;
  background: var(--base-color-surface);
  color: var(--base-color-text-primary);
}
.image-selector::backdrop {
  background: var(--base-color-surface);
}
.selector-layout {
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
}
.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  padding: 28px 32px 24px;
  border-bottom: 1px solid var(--base-color-border);
}
.selector-heading {
  display: grid;
  gap: 8px;
  min-width: 0;
}
.eyebrow {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
  font-weight: 600;
}
h2 {
  font-size: clamp(1.65rem, 2.7vw, 2.5rem);
  line-height: 1.05;
  letter-spacing: -0.045em;
}
.selector-description {
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
.return-button {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 16px;
}
.selector-scroll {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}
.selector-content {
  width: min(1600px, 100%);
  padding: 20px 32px 32px;
  margin: 0 auto;
}
.options-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.options-count {
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
.text-button {
  min-height: 44px;
  padding: 8px 4px;
  border: 0;
  background: transparent;
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
  cursor: pointer;
  border-radius: 12px;
}
.text-button:hover {
  color: var(--base-color-text-primary);
  text-decoration: underline;
}
.lookup-notice {
  padding: 12px 16px;
  margin-bottom: 16px;
  border-radius: 16px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
}
.candidate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 20px;
  align-items: start;
}
.candidate {
  min-width: 0;
  border: 2px solid transparent;
  border-radius: 20px;
  padding: 6px;
  background: var(--base-color-surface-strong);
}
.candidate.selected {
  border-color: var(--base-color-brand-red);
}
.candidate-visual {
  position: relative;
  border-radius: 14px;
  background: var(--base-color-surface-soft);
}
.candidate-preview {
  position: relative;
  display: block;
  width: 100%;
  height: 230px;
  padding: 8px;
  border: 0;
  border-radius: 14px;
  background: transparent;
  cursor: zoom-in;
}
.candidate-preview img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 8px;
}
.candidate-toggle {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  background: var(--base-color-surface-strong);
  border-radius: 14px;
  cursor: pointer;
}
.candidate-toggle input,
.preview-selection input {
  width: 24px;
  height: 24px;
  margin: 0;
  accent-color: var(--base-color-brand-red);
  cursor: pointer;
}
.preview-hint {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 6px 10px;
  border-radius: 12px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
  font-size: 0.72rem;
  opacity: 0;
}
.candidate-preview:hover .preview-hint,
.candidate-preview:focus-visible .preview-hint {
  opacity: 1;
}
.candidate-info {
  padding: 12px 6px 2px;
  display: grid;
  gap: 5px;
}
.candidate-caption {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
h3 {
  font-size: 0.88rem;
  font-weight: 600;
}
.selected-label {
  color: var(--base-color-brand-red);
  font-size: 0.7rem;
  font-weight: 600;
}
.candidate-source {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
}
.candidate-state {
  min-height: 28px;
  margin-top: 3px;
}
.selector-footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px 24px;
  padding: 18px 32px max(18px, env(safe-area-inset-bottom));
  border-top: 1px solid var(--base-color-border);
  background: var(--base-color-surface-strong);
}
.selection-summary {
  display: grid;
  gap: 4px;
}
.selection-summary strong {
  font-size: 0.88rem;
  font-weight: 600;
}
.selection-summary span {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
}
.footer-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 24px;
}
.save-button {
  min-width: 170px;
}
.save-feedback {
  grid-column: 1 / -1;
  font-size: 0.8rem;
}
.feedback {
  flex-basis: 100%;
  font-size: 0.88rem;
}
.error {
  color: #9e0a0a;
}
.preview-lookup {
  display: grid;
  justify-items: center;
  gap: 8px;
}
.preview-selection {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 44px;
  padding: 8px 16px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.88rem;
  white-space: nowrap;
  cursor: pointer;
}
.preview-selection.chosen {
  background: var(--base-color-brand-red);
  color: #fff;
}
.preview-selection.chosen input {
  accent-color: #fff;
}
.empty-state {
  display: grid;
  justify-items: center;
  gap: 16px;
  padding: 64px 16px;
  text-align: center;
  color: var(--base-color-text-secondary);
}
.empty-state h3 {
  font-size: 1.2rem;
  color: var(--base-color-text-primary);
}
.empty-state p {
  font-size: 0.88rem;
}
.candidate-skeleton {
  height: 330px;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
}
@media (hover: none) {
  .preview-hint {
    opacity: 1;
  }
}
@media (max-width: 575px) {
  .launch-card {
    padding: 20px;
  }
  .launch-card .primary-button {
    width: 100%;
  }
  .selector-header {
    padding: 16px;
    gap: 12px;
    flex-wrap: wrap;
  }
  .selector-heading {
    flex: 1 1 170px;
  }
  .selector-description {
    font-size: 0.8rem;
  }
  .return-button {
    padding: 10px 12px;
    gap: 8px;
    font-size: 0.75rem;
  }
  .selector-content {
    padding: 12px 16px 24px;
  }
  .candidate-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
  .candidate-preview {
    height: 240px;
  }
  .selector-footer {
    padding: 12px 16px max(12px, env(safe-area-inset-bottom));
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .selection-summary span {
    display: none;
  }
  .footer-actions {
    justify-content: space-between;
    gap: 16px;
  }
  .save-button {
    flex: 1;
    min-width: 0;
  }
  .clear-button {
    flex-shrink: 0;
  }
}
</style>
