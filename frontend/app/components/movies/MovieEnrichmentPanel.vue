<template>
  <section class="enrichment-panel">
    <SectionHeading
      eyebrow="Catálogo"
      title="Buscar sugestões no TMDb"
      description="Um ponto de revisão para puxar metadados sem atropelar o que o catálogo já sabe."
      summary="Primeiro a comparação, depois a decisão."
    />

    <article class="action-card">
      <div class="copy">
        <p class="copy-title">Enriquecimento por TMDb</p>
        <p class="copy-body">
          Use o vínculo já conhecido do filme ou informe um `TMDb ID` para abrir uma leitura curta do que vale trazer.
        </p>
      </div>

      <form class="action-form" @submit.prevent="handlePreview">
        <label class="field">
          <span>TMDb ID</span>
          <input v-model="tmdbId" type="text" placeholder="Ex.: 438631">
        </label>

        <div class="actions">
          <button type="submit" class="primary-button" :disabled="previewLoading">
            {{ previewLoading ? 'Buscando...' : 'Ver sugestão' }}
          </button>
        </div>
      </form>

      <p v-if="errorMessage" class="feedback error">{{ errorMessage }}</p>
      <p v-else-if="successMessage" class="feedback success">{{ successMessage }}</p>
    </article>

    <div v-if="preview" class="preview-overlay" @click.self="closePreview">
      <div class="preview-dialog">
        <div class="dialog-head">
          <div class="dialog-copy">
            <p class="dialog-eyebrow">Diff</p>
            <h3>{{ preview.title }}</h3>
            <p class="dialog-summary">TMDb {{ preview.resolvedTmdbId }}</p>
            <p class="dialog-lead">
              O quadro abaixo separa metadados e imagem para que a escolha pareça editorial, não operacional.
            </p>
          </div>
          <button type="button" class="close-button" @click="closePreview">Fechar</button>
        </div>

        <section class="preview-hero">
          <div class="hero-poster">
            <img v-if="suggestedPosterUrl || currentCoverUrl" :src="suggestedPosterUrl || currentCoverUrl || undefined" alt="Poster sugerido">
            <div v-else class="image-fallback hero-fallback">Sem imagem sugerida</div>
          </div>

          <div class="hero-summary">
            <article class="summary-card">
              <p class="summary-label">O que chegou</p>
              <p class="summary-value">{{ availableFieldsCount }} bloco<span v-if="availableFieldsCount !== 1">s</span> com sugestão</p>
              <p class="summary-note">Incluindo metadados e novas imagens quando houver ganho claro para o detalhe do filme.</p>
            </article>

            <article class="summary-card">
              <p class="summary-label">Leitura inicial</p>
              <p class="summary-value">{{ selectedFields.length }} bloco<span v-if="selectedFields.length !== 1">s</span> pronto<span v-if="selectedFields.length !== 1">s</span> para aplicar</p>
              <p class="summary-note">A seleção padrão privilegia lacunas e o material visual mais útil como nova referência.</p>
            </article>
          </div>
        </section>

        <section class="metadata-shelf">
          <label
            v-for="field in preview.fields"
            :key="field.field"
            class="field-card"
            :class="{ muted: !field.available, selected: selectedFields.includes(field.field) }"
          >
            <input
              v-model="selectedFields"
              type="checkbox"
              :value="field.field"
              :disabled="!field.available"
            >
            <div class="field-copy">
              <div class="field-head">
                <strong>{{ field.label }}</strong>
                <span class="field-state">{{ field.missing ? 'Preenche uma lacuna' : field.changed ? 'Atualiza o que já existe' : 'Sem ganho agora' }}</span>
              </div>
              <p class="field-current"><strong>Agora</strong> {{ field.currentValue || 'Sem valor registrado' }}</p>
              <p class="field-suggested"><strong>TMDb</strong> {{ field.suggestedValue || 'Sem sugestão útil' }}</p>
            </div>
          </label>
        </section>

        <section class="image-studio" :class="{ muted: !preview.images.available }">
          <div class="studio-head">
            <div>
              <p class="dialog-eyebrow">Imagens</p>
              <h4>Escolha o que entra no detalhe</h4>
              <p class="studio-summary">A principal atual fica em contraste com as novas sugestões. Marque o que mantém no filme e promova uma imagem para capa principal.</p>
            </div>
            <label class="studio-toggle">
              <input
                v-model="selectedFields"
                type="checkbox"
                value="IMAGES"
                :disabled="!preview.images.available"
              >
              <span>Aplicar imagens</span>
            </label>
          </div>

          <div class="current-frame">
            <div class="current-image">
              <img v-if="currentCoverUrl" :src="currentCoverUrl" alt="Capa atual">
              <div v-else class="image-fallback">Sem capa atual</div>
            </div>
            <div class="current-copy">
              <p class="current-kicker">Principal atual</p>
              <p class="current-title">{{ currentPrimaryLabel }}</p>
              <p class="current-note">{{ preview.images.missing ? 'O filme ainda não tem uma imagem principal definida.' : 'Você pode manter essa referência ou trocar a capa principal abaixo.' }}</p>
            </div>
          </div>

          <div class="candidate-board">
            <article
              v-for="candidate in preview.images.candidates"
              :key="candidate.key"
              class="candidate-card"
              :class="{
                selected: selectedImageKeys.includes(candidate.key),
                primary: selectedImagePrimaryKey === candidate.key,
              }"
            >
              <button type="button" class="candidate-visual" @click="toggleCandidate(candidate.key)">
                <img :src="candidate.imageUrl" :alt="candidate.label">
                <span v-if="selectedImagePrimaryKey === candidate.key" class="pin-badge">Principal</span>
                <span v-else-if="candidate.suggestedAsPrimary" class="pin-badge muted-badge">Sugestão</span>
              </button>

              <div class="candidate-copy">
                <div class="candidate-head">
                  <strong>{{ candidate.label }}</strong>
                  <span>{{ candidate.kind }}</span>
                </div>
                <p class="candidate-note">{{ selectedImageKeys.includes(candidate.key) ? 'Entrará na página do filme.' : 'Fica fora até você decidir incluir.' }}</p>

                <div class="candidate-actions">
                  <button
                    type="button"
                    class="chip-button"
                    :class="{ active: selectedImageKeys.includes(candidate.key) }"
                    @click="toggleCandidate(candidate.key)"
                  >
                    {{ selectedImageKeys.includes(candidate.key) ? 'Selecionada' : 'Selecionar' }}
                  </button>
                  <button
                    type="button"
                    class="chip-button"
                    :class="{ active: selectedImagePrimaryKey === candidate.key }"
                    :disabled="!selectedImageKeys.includes(candidate.key)"
                    @click="promoteCandidate(candidate.key)"
                  >
                    Tornar principal
                  </button>
                </div>
              </div>
            </article>
          </div>
        </section>

        <div class="dialog-actions">
          <button type="button" class="secondary-button" :disabled="applyLoading" @click="applyMissing">
            {{ applyLoading ? 'Aplicando...' : 'Preencher lacunas' }}
          </button>
          <button type="button" class="primary-button" :disabled="applyLoading || !selectedFields.length" @click="applySelected">
            {{ applyLoading ? 'Aplicando...' : 'Aplicar seleção' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type {
  MovieEnrichmentApplyRequest,
  MovieEnrichmentApplyResponse,
  MovieEnrichmentField,
  MovieEnrichmentPreviewResponse,
} from '~/types/movies'

const props = defineProps<{
  movieId: number
  identifiers: Array<{
    provider: string
    externalId: string
  }>
}>()

const emit = defineEmits<{
  applied: [response: MovieEnrichmentApplyResponse]
}>()

const { resolveMediaUrl } = useMediaUrl()
const config = useRuntimeConfig()
const tmdbId = ref(props.identifiers.find((identifier) => identifier.provider === 'TMDB')?.externalId ?? '')
const preview = ref<MovieEnrichmentPreviewResponse | null>(null)
const selectedFields = ref<MovieEnrichmentField[]>([])
const selectedImageKeys = ref<string[]>([])
const selectedImagePrimaryKey = ref<string | null>(null)
const previewLoading = ref(false)
const applyLoading = ref(false)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)

const currentCoverUrl = computed(() => resolveMediaUrl(preview.value?.images.currentCoverUrl ?? null))
const suggestedPosterUrl = computed(() => preview.value?.images.candidates.find(candidate => candidate.suggestedAsPrimary)?.imageUrl ?? null)
const currentPrimaryLabel = computed(() => {
  if (!preview.value?.images.currentCoverUrl) {
    return 'Sem capa definida'
  }

  return 'A imagem que está em destaque hoje'
})
const availableFieldsCount = computed(() => {
  if (!preview.value) return 0

  return preview.value.fields.filter(field => field.available).length + (preview.value.images.available ? 1 : 0)
})

function hydrateSelection(payload: MovieEnrichmentPreviewResponse) {
  selectedFields.value = payload.fields.filter((field) => field.selectedByDefault).map((field) => field.field)
  if (payload.images.selectedByDefault) {
    selectedFields.value.push('IMAGES')
  }
  selectedImageKeys.value = payload.images.candidates.filter(candidate => candidate.selectedByDefault).map(candidate => candidate.key)
  selectedImagePrimaryKey.value =
    payload.images.candidates.find(candidate => candidate.suggestedAsPrimary && selectedImageKeys.value.includes(candidate.key))?.key
    ?? selectedImageKeys.value[0]
    ?? null
}

async function handlePreview() {
  previewLoading.value = true
  errorMessage.value = null
  successMessage.value = null

  try {
    const response = await $fetch<MovieEnrichmentPreviewResponse>(`/api/movies/${props.movieId}/enrichment/preview`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        tmdbId: tmdbId.value.trim() || null,
      },
    })

    preview.value = response
    tmdbId.value = response.resolvedTmdbId
    hydrateSelection(response)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Não foi possível buscar sugestão no provider.'
  } finally {
    previewLoading.value = false
  }
}

async function applyMissing() {
  await apply('MISSING', [])
}

async function applySelected() {
  await apply('SELECTED', selectedFields.value)
}

async function apply(mode: 'MISSING' | 'SELECTED', fields: MovieEnrichmentField[]) {
  applyLoading.value = true
  errorMessage.value = null

  try {
    const body: MovieEnrichmentApplyRequest = {
      tmdbId: tmdbId.value.trim() || null,
      mode,
      fields,
      imageSelection: (fields.includes('IMAGES') || (mode === 'MISSING' && selectedImageKeys.value.length > 0))
        ? {
            selectedKeys: selectedImageKeys.value,
            primaryKey: selectedImagePrimaryKey.value,
          }
        : null,
    }

    const response = await $fetch<MovieEnrichmentApplyResponse>(`/api/movies/${props.movieId}/enrichment/apply`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body,
    })

    successMessage.value =
      response.appliedFields.length > 0
        ? `${response.appliedFields.length} bloco(s) aplicados ao filme.`
        : 'Nenhuma mudança foi necessária.'
    preview.value = null
    emit('applied', response)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Não foi possível aplicar as sugestões.'
  } finally {
    applyLoading.value = false
  }
}

function closePreview() {
  if (applyLoading.value) return
  preview.value = null
}

function toggleCandidate(key: string) {
  if (selectedImageKeys.value.includes(key)) {
    selectedImageKeys.value = selectedImageKeys.value.filter(value => value !== key)
    return
  }

  selectedImageKeys.value = [...selectedImageKeys.value, key]
}

function promoteCandidate(key: string) {
  if (!selectedImageKeys.value.includes(key)) {
    selectedImageKeys.value = [...selectedImageKeys.value, key]
  }

  selectedImagePrimaryKey.value = key
}

watch(selectedFields, (fields) => {
  if (!fields.includes('IMAGES')) {
    selectedImagePrimaryKey.value = null
    return
  }

  if (!selectedImageKeys.value.length) {
    selectedImagePrimaryKey.value = null
    return
  }

  if (!selectedImagePrimaryKey.value || !selectedImageKeys.value.includes(selectedImagePrimaryKey.value)) {
    selectedImagePrimaryKey.value = selectedImageKeys.value[0] ?? null
  }
})

watch(selectedImageKeys, (keys) => {
  if (!selectedFields.value.includes('IMAGES') && keys.length > 0) {
    selectedFields.value = [...selectedFields.value, 'IMAGES']
  }

  if (!keys.length) {
    selectedFields.value = selectedFields.value.filter(field => field !== 'IMAGES')
    selectedImagePrimaryKey.value = null
    return
  }

  if (!selectedImagePrimaryKey.value || !keys.includes(selectedImagePrimaryKey.value)) {
    selectedImagePrimaryKey.value = keys[0] ?? null
  }
})
</script>

<style scoped>
.enrichment-panel {
  display: grid;
  gap: 24px;
}

.action-card {
  display: grid;
  gap: 18px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.copy {
  display: grid;
  gap: 6px;
}

.copy-title,
.copy-body,
.feedback,
.dialog-eyebrow,
.dialog-summary,
.field-current,
.field-suggested,
.studio-summary,
.current-kicker,
.current-title,
.current-note,
.candidate-note {
  margin: 0;
}

.copy-title {
  font-size: 1.05rem;
  font-weight: 700;
}

.copy-body {
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}

.action-form {
  display: grid;
  grid-template-columns: minmax(0, 18rem) auto;
  gap: 14px;
  align-items: end;
}

.field {
  display: grid;
  gap: 8px;
}

.field span,
.summary-label,
.current-kicker,
.candidate-head span {
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.field input {
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
}

.actions {
  display: flex;
}

.primary-button,
.secondary-button,
.close-button,
.chip-button {
  border: 0;
  border-radius: 16px;
  padding: 12px 16px;
  cursor: pointer;
}

.primary-button,
.chip-button.active {
  background: var(--base-color-brand-red);
  color: #000000;
}

.secondary-button,
.close-button,
.chip-button,
.studio-toggle {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.feedback {
  color: var(--base-color-text-secondary);
  font-size: 0.9rem;
}

.feedback.error {
  color: #7a1414;
}

.feedback.success {
  color: #103c25;
}

.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 35;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(33, 25, 34, 0.18);
  backdrop-filter: blur(8px);
}

.preview-dialog {
  width: min(1160px, 100%);
  max-height: calc(100vh - 48px);
  overflow: auto;
  display: grid;
  gap: 24px;
  padding: 28px;
  border-radius: 40px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 243, 238, 0.99));
}

.dialog-head,
.studio-head,
.dialog-actions {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
}

.dialog-copy {
  display: grid;
  gap: 8px;
}

.dialog-eyebrow {
  color: var(--base-color-brand-red);
  font-size: 0.76rem;
}

.dialog-head h3 {
  margin: 6px 0 0;
  font-size: 1.9rem;
  line-height: 0.98;
  letter-spacing: -0.05em;
}

.dialog-summary,
.dialog-lead,
.summary-note,
.field-current,
.field-suggested,
.studio-summary,
.current-note,
.candidate-note {
  color: var(--base-color-text-secondary);
}

.dialog-lead,
.summary-note,
.studio-summary,
.current-note,
.candidate-note,
.field-current,
.field-suggested {
  line-height: 1.6;
}

.preview-hero {
  display: grid;
  grid-template-columns: minmax(0, 15rem) minmax(0, 1fr);
  gap: 18px;
}

.hero-poster img,
.hero-fallback,
.current-image img,
.image-fallback,
.candidate-card img {
  width: 100%;
  min-height: 14rem;
  border-radius: 24px;
  object-fit: cover;
  background: var(--base-color-surface-soft);
}

.hero-poster img,
.hero-fallback {
  height: 100%;
  min-height: 20rem;
}

.hero-summary,
.metadata-shelf,
.candidate-board {
  display: grid;
  gap: 14px;
}

.hero-summary,
.metadata-shelf {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.summary-card,
.field-card,
.image-studio,
.current-frame,
.candidate-card {
  background: color-mix(in srgb, white 76%, var(--base-color-surface-soft));
}

.summary-card,
.field-card,
.image-studio,
.current-frame {
  border-radius: 24px;
}

.summary-card,
.field-card,
.current-frame,
.candidate-card {
  padding: 18px;
}

.summary-value,
.current-title {
  color: var(--base-color-text-primary);
  font-weight: 700;
}

.summary-value {
  font-size: 1.2rem;
  line-height: 1.1;
}

.field-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 14px;
}

.field-card.selected,
.candidate-card.selected {
  border: 1px solid color-mix(in srgb, var(--base-color-brand-red) 38%, white);
}

.field-card.muted,
.image-studio.muted {
  opacity: 0.6;
}

.field-copy,
.candidate-copy {
  display: grid;
  gap: 10px;
}

.field-head,
.candidate-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
}

.field-state {
  max-width: 12rem;
  text-align: right;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
  line-height: 1.4;
}

.field-current strong,
.field-suggested strong {
  color: var(--base-color-text-primary);
  font-size: 0.76rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.image-studio {
  display: grid;
  gap: 18px;
  padding: 22px;
  border-radius: 32px;
}

.studio-head h4 {
  margin: 6px 0 0;
  font-size: 1.55rem;
  line-height: 1;
  letter-spacing: -0.04em;
}

.studio-toggle {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 16px;
  white-space: nowrap;
}

.current-frame {
  display: grid;
  grid-template-columns: minmax(0, 14rem) minmax(0, 1fr);
  gap: 18px;
  align-items: center;
}

.candidate-board {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.candidate-card {
  display: grid;
  gap: 12px;
  border-radius: 24px;
}

.candidate-card.primary {
  background: color-mix(in srgb, white 78%, var(--base-color-surface-warm));
}

.candidate-visual {
  position: relative;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.candidate-card img {
  min-height: 15rem;
  border-radius: 20px;
}

.pin-badge {
  position: absolute;
  left: 12px;
  top: 12px;
  padding: 7px 11px;
  border-radius: 999px;
  background: var(--base-color-brand-red);
  color: #000000;
  font-size: 0.72rem;
  font-weight: 700;
}

.muted-badge {
  background: color-mix(in srgb, white 72%, var(--base-color-surface-warm));
  color: var(--base-color-text-primary);
}

.candidate-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.chip-button:disabled {
  opacity: 0.55;
  cursor: default;
}

@media (max-width: 820px) {
  .preview-hero,
  .hero-summary,
  .metadata-shelf,
  .current-frame,
  .action-form,
  .candidate-board {
    grid-template-columns: 1fr;
  }

  .dialog-head,
  .studio-head,
  .dialog-actions {
    flex-direction: column;
  }
}
</style>
