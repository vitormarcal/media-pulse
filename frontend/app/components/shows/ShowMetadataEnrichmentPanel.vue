<template>
  <section class="enrichment-panel">
    <div class="panel-copy">
      <p class="eyebrow">Catálogo</p>
      <h2>Enriquecer dados da série</h2>
      <p>Compare título, ano, descrição e imagens com o TMDb antes de aplicar alterações.</p>
    </div>

    <form class="lookup" @submit.prevent="loadPreview">
      <label>
        <span>TMDb ID</span>
        <input v-model="tmdbId" inputmode="numeric" autocomplete="off" placeholder="Usar vínculo salvo" />
      </label>
      <button class="primary" type="submit" :disabled="loading">{{ loading ? 'Buscando…' : 'Ver sugestão' }}</button>
    </form>
    <p v-if="errorMessage" class="feedback error" role="alert">{{ errorMessage }}</p>
    <p v-else-if="successMessage" class="feedback success" role="status">{{ successMessage }}</p>

    <div v-if="preview" class="preview">
      <div class="preview-head">
        <div>
          <p class="eyebrow">Comparação</p>
          <h3>{{ preview.title }}</h3>
          <small>TMDb {{ preview.resolvedTmdbId }}</small>
        </div>
        <button type="button" class="quiet" :disabled="applying" @click="preview = null">Fechar</button>
      </div>

      <div class="field-grid">
        <label
          v-for="field in preview.fields"
          :key="field.field"
          class="field-card"
          :class="{ selected: selectedFields.includes(field.field), muted: !field.available }"
        >
          <input v-model="selectedFields" type="checkbox" :value="field.field" :disabled="!field.available" />
          <span
            ><strong>{{ field.label }}</strong
            ><small>{{
              field.missing ? 'Preenche uma lacuna' : field.changed ? 'Atualiza o valor atual' : 'Sem mudança'
            }}</small></span
          >
          <p><b>Agora</b> {{ field.currentValue || 'Sem valor' }}</p>
          <p><b>TMDb</b> {{ field.suggestedValue || 'Sem sugestão' }}</p>
        </label>
      </div>

      <section class="images" :class="{ muted: !preview.images.available }">
        <label class="images-toggle"
          ><input
            v-model="selectedFields"
            type="checkbox"
            value="IMAGES"
            :disabled="!preview.images.available"
          /><strong>Aplicar imagens</strong></label
        >
        <div class="image-grid">
          <article
            v-for="candidate in preview.images.candidates"
            :key="candidate.key"
            :class="{ selected: selectedImageKeys.includes(candidate.key) }"
          >
            <img :src="candidate.imageUrl" :alt="candidate.label" />
            <div>
              <strong>{{ candidate.label }}</strong
              ><button type="button" class="quiet" @click="toggleImage(candidate.key)">
                {{ selectedImageKeys.includes(candidate.key) ? 'Remover' : 'Selecionar' }}</button
              ><button
                type="button"
                class="quiet"
                :disabled="!selectedImageKeys.includes(candidate.key)"
                @click="primaryImageKey = candidate.key"
              >
                {{ primaryImageKey === candidate.key ? 'Principal' : 'Tornar principal' }}
              </button>
            </div>
          </article>
        </div>
      </section>

      <div class="actions">
        <button type="button" class="secondary" :disabled="applying" @click="apply('MISSING', [])">
          Preencher lacunas
        </button>
        <button
          type="button"
          class="primary"
          :disabled="applying || !selectedFields.length"
          @click="apply('SELECTED', selectedFields)"
        >
          {{ applying ? 'Aplicando…' : 'Aplicar seleção' }}
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type {
  ShowMetadataEnrichmentApplyRequest,
  ShowMetadataEnrichmentApplyResponse,
  ShowMetadataEnrichmentField,
  ShowMetadataEnrichmentPreviewResponse,
} from '~/types/shows'

const props = defineProps<{ showId: number; identifiers: Array<{ provider: string; externalId: string }> }>()
const emit = defineEmits<{ applied: [response: ShowMetadataEnrichmentApplyResponse] }>()
const config = useRuntimeConfig()
const tmdbId = ref(props.identifiers.find((item) => item.provider === 'TMDB')?.externalId ?? '')
const preview = ref<ShowMetadataEnrichmentPreviewResponse | null>(null)
const selectedFields = ref<ShowMetadataEnrichmentField[]>([])
const selectedImageKeys = ref<string[]>([])
const primaryImageKey = ref<string | null>(null)
const loading = ref(false)
const applying = ref(false)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)

async function loadPreview() {
  loading.value = true
  errorMessage.value = null
  successMessage.value = null
  try {
    const result = await $fetch<ShowMetadataEnrichmentPreviewResponse>(
      `/api/shows/${props.showId}/enrichment/preview`,
      { baseURL: config.public.apiBase, method: 'POST', body: { tmdbId: tmdbId.value.trim() || null } },
    )
    preview.value = result
    tmdbId.value = result.resolvedTmdbId
    selectedFields.value = result.fields.filter((field) => field.selectedByDefault).map((field) => field.field)
    if (result.images.selectedByDefault) selectedFields.value.push('IMAGES')
    selectedImageKeys.value = result.images.candidates
      .filter((image) => image.selectedByDefault)
      .map((image) => image.key)
    primaryImageKey.value =
      result.images.candidates.find((image) => image.suggestedAsPrimary && selectedImageKeys.value.includes(image.key))
        ?.key ??
      selectedImageKeys.value[0] ??
      null
  } catch (error) {
    errorMessage.value = resolveError(error, 'Não foi possível consultar o TMDb.')
  } finally {
    loading.value = false
  }
}

async function apply(mode: 'MISSING' | 'SELECTED', fields: ShowMetadataEnrichmentField[]) {
  applying.value = true
  errorMessage.value = null
  const body: ShowMetadataEnrichmentApplyRequest = {
    tmdbId: tmdbId.value.trim() || null,
    mode,
    fields,
    imageSelection: { selectedKeys: selectedImageKeys.value, primaryKey: primaryImageKey.value },
  }
  try {
    const result = await $fetch<ShowMetadataEnrichmentApplyResponse>(`/api/shows/${props.showId}/enrichment/apply`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body,
    })
    preview.value = null
    successMessage.value = result.appliedFields.length
      ? `${result.appliedFields.length} bloco(s) aplicados à série.`
      : 'Nenhuma mudança foi necessária.'
    emit('applied', result)
  } catch (error) {
    errorMessage.value = resolveError(error, 'Não foi possível aplicar as sugestões.')
  } finally {
    applying.value = false
  }
}

function toggleImage(key: string) {
  selectedImageKeys.value = selectedImageKeys.value.includes(key)
    ? selectedImageKeys.value.filter((item) => item !== key)
    : [...selectedImageKeys.value, key]
  if (selectedImageKeys.value.length && !selectedFields.value.includes('IMAGES')) selectedFields.value.push('IMAGES')
  if (!selectedImageKeys.value.includes(primaryImageKey.value ?? ''))
    primaryImageKey.value = selectedImageKeys.value[0] ?? null
}

function resolveError(error: unknown, fallback: string) {
  if (error && typeof error === 'object' && 'data' in error) {
    const data = (error as { data?: { detail?: string; message?: string } }).data
    return data?.detail || data?.message || fallback
  }
  return error instanceof Error ? error.message : fallback
}
</script>

<style scoped>
.enrichment-panel {
  display: grid;
  gap: 20px;
  padding: 26px;
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-wash);
}
.panel-copy,
.preview-head > div {
  display: grid;
  gap: 5px;
}
.panel-copy p,
.panel-copy h2,
.preview-head p,
.preview-head h3 {
  margin: 0;
}
.panel-copy > p:last-child {
  color: var(--base-color-text-secondary);
}
.eyebrow {
  color: var(--base-color-brand-red);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.lookup {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
}
.lookup label {
  display: grid;
  gap: 7px;
  font-size: 0.78rem;
}
.lookup input {
  padding: 11px 13px;
  border: 1px solid var(--base-color-border);
  border-radius: 12px;
  background: white;
  font: inherit;
}
button {
  padding: 6px 14px;
  border: 2px solid transparent;
  border-radius: var(--comp-radius-button);
  background: var(--base-color-surface-strong);
  font: inherit;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.primary {
  border-color: var(--base-color-brand-red);
  background: var(--base-color-brand-red);
  color: #000;
}
.secondary {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}
.quiet {
  padding: 7px 10px;
  font-size: 0.72rem;
}
.feedback {
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
}
.feedback.error {
  background: var(--base-color-surface-warm);
  color: #9e0a0a;
}
.feedback.success {
  background: var(--base-color-surface-warm);
  color: #103c25;
}
.lookup input:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--base-color-focus) 66%, white);
  outline-offset: 3px;
}
.preview {
  display: grid;
  gap: 18px;
  padding-top: 20px;
  border-top: 1px solid var(--base-color-border);
}
.preview-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.field-card {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px;
  padding: 15px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
}
.field-card.selected {
  border-color: var(--base-color-brand-red);
}
.field-card p {
  grid-column: 2;
  margin: 0;
  font-size: 0.78rem;
  color: var(--base-color-text-secondary);
}
.field-card span {
  display: grid;
}
.field-card small {
  color: var(--base-color-text-secondary);
}
.muted {
  opacity: 0.55;
}
.images {
  display: grid;
  gap: 12px;
}
.images-toggle {
  display: flex;
  gap: 8px;
}
.image-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.image-grid article {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 12px;
  padding: 10px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
}
.image-grid article.selected {
  border-color: var(--base-color-brand-red);
}
.image-grid img {
  width: 110px;
  height: 145px;
  object-fit: cover;
  border-radius: 10px;
}
.image-grid article div {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
@media (max-width: 760px) {
  .lookup,
  .field-grid,
  .image-grid {
    grid-template-columns: 1fr;
  }
  .lookup button {
    width: 100%;
  }
  .actions {
    flex-direction: column;
  }
  .actions button {
    width: 100%;
  }
}
</style>
