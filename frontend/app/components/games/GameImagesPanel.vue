<template>
  <section class="images-panel">
    <SectionHeading eyebrow="Catálogo" title="Adicionar imagens" />
    <article class="action-card" :aria-busy="busy">
      <p class="copy-body">Escolha as imagens que deseja guardar. A capa atual será mantida.</p>
      <div class="actions">
        <button v-owner type="button" class="primary-button" :disabled="busy" @click="loadImages">
          {{ loading ? 'Buscando…' : 'Escolher imagens' }}
        </button>
      </div>
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <p v-if="message" class="feedback" role="status">{{ message }}</p>
      <p v-for="warning in warnings" :key="warning" class="feedback" role="status">{{ warning }}</p>
      <template v-if="opened">
        <p v-if="!candidates.length && !loading" class="copy-body">
          Nenhuma imagem disponível nos provedores configurados.
        </p>
        <div class="candidate-grid">
          <label
            v-for="image in candidates"
            :key="image.id"
            class="candidate"
            :class="{ selected: selected.includes(image.id) }"
          >
            <img
              :src="image.previewUrl"
              :alt="`${kindLabel(image.kind)} — ${image.provider}`"
              loading="lazy"
              referrerpolicy="no-referrer"
            />
            <span class="candidate-caption">
              <input
                v-model="selected"
                type="checkbox"
                :value="image.id"
                :disabled="busy || (selected.length >= 50 && !selected.includes(image.id))"
              />
              <span>{{ kindLabel(image.kind) }} · {{ providerLabel(image.provider) }}</span>
              <span v-if="image.width && image.height" class="dimensions">{{ image.width }} × {{ image.height }}</span>
            </span>
          </label>
        </div>
        <div v-if="candidates.length" class="actions save-actions">
          <span class="copy-body">{{ selected.length }} selecionada(s) · até 50 por vez</span>
          <button v-owner type="button" class="primary-button" :disabled="busy || !selected.length" @click="saveImages">
            {{ saving ? 'Salvando…' : 'Salvar selecionadas' }}
          </button>
        </div>
      </template>
    </article>
  </section>
</template>
<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
interface Candidate {
  id: string
  provider: string
  kind: string
  previewUrl: string
  width: number | null
  height: number | null
}
const props = defineProps<{ gameId: number }>()
const emit = defineEmits<{ imported: [] }>()
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
    message.value = `${result.savedIds.length} imagem(ns) guardada(s).`
    if (result.failedIds.length)
      error.value = 'Algumas imagens não puderam ser guardadas. Elas continuam selecionadas para tentar novamente.'
    if (result.savedIds.length) emit('imported')
  } catch (cause) {
    error.value = errorMessage(cause, 'Não foi possível guardar as imagens. Tente novamente.')
  } finally {
    saving.value = false
  }
}
</script>
<style scoped>
.images-panel {
  display: grid;
  gap: 18px;
}
.action-card {
  display: grid;
  gap: 16px;
  padding: 24px;
  border-radius: 28px;
  background: var(--base-color-surface-wash);
  border: 1px solid var(--base-color-border);
}
p {
  margin: 0;
}
.copy-body {
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
.actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.save-actions {
  justify-content: space-between;
}
.primary-button {
  padding: 10px 16px;
  border: 2px solid transparent;
  border-radius: var(--comp-radius-button);
  background: var(--base-color-brand-red);
  color: white;
  font: inherit;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
}
.primary-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.primary-button:focus-visible,
.candidate:focus-within {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 4px;
}
.feedback {
  padding: 10px 12px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-secondary);
}
.error {
  color: #9e0a0a;
}
.candidate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  align-items: start;
}
.candidate {
  display: grid;
  gap: 8px;
  border: 2px solid var(--base-color-border);
  border-radius: 20px;
  padding: 8px;
  cursor: pointer;
  background: var(--base-color-surface-warm);
}
.candidate.selected {
  border-color: var(--base-color-brand-red);
}
.candidate img {
  width: 100%;
  height: 200px;
  object-fit: contain;
  border-radius: 12px;
}
.candidate-caption {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 0.75rem;
  color: var(--base-color-text-primary);
}
.candidate input {
  width: 20px;
  height: 20px;
  accent-color: var(--base-color-brand-red);
}
.dimensions {
  color: var(--base-color-text-secondary);
}
@media (max-width: 576px) {
  .primary-button {
    width: 100%;
  }
  .candidate-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .action-card {
    padding: 16px;
  }
}
</style>
