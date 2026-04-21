<template>
  <section class="manual-add-card">
    <SectionHeading
      eyebrow="Entrada manual"
      title="Adicionar série ao catálogo"
      description="A busca no TMDb traz a série com contexto, poster e episódios, para ela já nascer navegável na biblioteca."
      summary="Se a sugestão não servir ou você já souber os IDs, a entrada mínima continua disponível logo abaixo."
    />

    <article class="form-card">
      <form class="lookup-form" @submit.prevent="handleSuggest">
        <label class="field">
          <span>Nome da série</span>
          <input v-model="title" type="text" placeholder="Ex.: The Bear" />
        </label>

        <div class="actions">
          <button type="submit" class="primary-button" :disabled="loadingSuggestions || !title.trim()">
            {{ loadingSuggestions ? 'Buscando...' : 'Buscar no TMDb' }}
          </button>
          <button type="button" class="secondary-button" @click="showManual = !showManual">
            {{ showManual ? 'Fechar entrada mínima' : 'Criar entrada mínima' }}
          </button>
        </div>
      </form>

      <p v-if="feedback" class="feedback" :class="{ error: feedbackError }">{{ feedback }}</p>

      <section v-if="suggestions.length" class="suggestions-section">
        <div class="suggestions-head">
          <div>
            <p class="suggestions-label">Sugestões TMDb</p>
            <h3>Escolha a melhor correspondência</h3>
          </div>
          <p class="suggestions-summary">
            {{ suggestions.length }} opção<span v-if="suggestions.length !== 1">ões</span> para "{{ searchedQuery }}"
          </p>
        </div>

        <div class="suggestions-grid">
          <article v-for="suggestion in suggestions" :key="suggestion.tmdbId" class="suggestion-card">
            <img v-if="suggestion.posterUrl" :src="suggestion.posterUrl" :alt="suggestion.title" />
            <div v-else class="poster-fallback">Sem poster</div>

            <div class="suggestion-copy">
              <div class="suggestion-head">
                <strong>{{ suggestion.title }}</strong>
                <span v-if="suggestion.year">{{ suggestion.year }}</span>
              </div>
              <p
                v-if="suggestion.originalTitle && suggestion.originalTitle !== suggestion.title"
                class="original-title"
              >
                {{ suggestion.originalTitle }}
              </p>
              <p class="overview">{{ suggestion.overview || 'Sem descrição curta disponível no provider.' }}</p>
            </div>

            <button
              type="button"
              class="primary-button suggestion-button"
              :disabled="submitting"
              @click="handleUseSuggestion(suggestion.tmdbId)"
            >
              {{ submitting ? 'Registrando...' : 'Usar esta sugestão' }}
            </button>
          </article>
        </div>
      </section>

      <section v-else-if="searchedQuery && !loadingSuggestions" class="empty-suggestions">
        <p>Nenhuma sugestão útil apareceu para "{{ searchedQuery }}".</p>
      </section>

      <section v-if="showManual" class="manual-section">
        <div class="manual-head">
          <div>
            <p class="suggestions-label">Entrada mínima</p>
            <h3>Crie uma entrada mínima</h3>
          </div>
          <p class="suggestions-summary">
            Use quando você quiser registrar direto ou quando a busca externa não ajudar.
          </p>
        </div>

        <form class="manual-form" @submit.prevent="handleManualSubmit">
          <label class="field field-wide">
            <span>Título</span>
            <input v-model="manualTitle" type="text" placeholder="Nome da série" />
          </label>

          <label class="field">
            <span>Ano</span>
            <input v-model="year" type="number" inputmode="numeric" placeholder="2024" />
          </label>

          <label class="field">
            <span>TMDb ID</span>
            <input v-model="tmdbId" type="text" placeholder="Opcional" />
          </label>

          <label class="field">
            <span>TVDB ID</span>
            <input v-model="tvdbId" type="text" placeholder="Opcional" />
          </label>

          <label class="check-field">
            <input v-model="importEpisodes" type="checkbox" />
            <span>Importar temporadas e episódios do TMDb</span>
          </label>

          <div class="actions manual-actions">
            <button type="submit" class="primary-button" :disabled="submitting || !manualTitle.trim()">
              {{ submitting ? 'Registrando...' : 'Registrar série' }}
            </button>
          </div>
        </form>
      </section>
    </article>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ManualShowCatalogCreateResponse, ShowCatalogSuggestionsResponse } from '~/types/shows'

const props = defineProps<{
  initialTitle: string
}>()

const title = ref(props.initialTitle)
const manualTitle = ref(props.initialTitle)
const year = ref('')
const tmdbId = ref('')
const tvdbId = ref('')
const importEpisodes = ref(true)
const showManual = ref(false)
const submitting = ref(false)
const loadingSuggestions = ref(false)
const searchedQuery = ref('')
const suggestions = ref<ShowCatalogSuggestionsResponse['suggestions']>([])
const feedback = ref<string | null>(null)
const feedbackError = ref(false)
const config = useRuntimeConfig()
const router = useRouter()

watch(
  () => props.initialTitle,
  (value) => {
    title.value = value
    manualTitle.value = value
    suggestions.value = []
    searchedQuery.value = ''
  },
)

async function handleSuggest() {
  loadingSuggestions.value = true
  feedback.value = null
  feedbackError.value = false

  try {
    const response = await $fetch<ShowCatalogSuggestionsResponse>('/api/shows/catalog/suggestions', {
      baseURL: config.public.apiBase,
      query: {
        q: title.value.trim(),
      },
    })

    suggestions.value = response.suggestions
    searchedQuery.value = response.query
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível buscar sugestões agora.'
    feedbackError.value = true
    suggestions.value = []
  } finally {
    loadingSuggestions.value = false
  }
}

async function handleUseSuggestion(selectedTmdbId: string) {
  await createCatalogEntry({
    title: title.value.trim(),
    year: year.value.trim() ? Number(year.value) : null,
    tmdbId: selectedTmdbId,
    tvdbId: null,
    importEpisodes: true,
  })
}

async function handleManualSubmit() {
  await createCatalogEntry({
    title: manualTitle.value.trim(),
    year: year.value.trim() ? Number(year.value) : null,
    tmdbId: tmdbId.value.trim() || null,
    tvdbId: tvdbId.value.trim() || null,
    importEpisodes: importEpisodes.value,
  })
}

async function createCatalogEntry(body: {
  title: string
  year: number | null
  tmdbId: string | null
  tvdbId: string | null
  importEpisodes: boolean
}) {
  submitting.value = true
  feedback.value = null
  feedbackError.value = false

  try {
    const response = await $fetch<ManualShowCatalogCreateResponse>('/api/shows/catalog', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body,
    })

    feedback.value = response.createdShow
      ? `Série adicionada com ${response.episodesImported} episódio(s).`
      : 'Série existente reaproveitada e consolidada.'
    await router.push(response.slug ? `/shows/${response.slug}` : `/shows/library?q=${encodeURIComponent(body.title)}`)
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível adicionar a série agora.'
    feedbackError.value = true
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.manual-add-card {
  display: grid;
  gap: 24px;
}

.form-card,
.suggestion-card,
.manual-section,
.empty-suggestions {
  display: grid;
  gap: 18px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.lookup-form,
.manual-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: end;
}

.manual-form {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.field,
.check-field {
  display: grid;
  gap: 8px;
}

.field-wide,
.check-field {
  grid-column: span 2;
}

.field span,
.check-field span {
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
  font-weight: 700;
}

.field input {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: #fff;
  color: var(--base-color-text-primary);
}

.check-field {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
}

.check-field input {
  width: 18px;
  height: 18px;
  accent-color: var(--base-color-brand-red);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.primary-button,
.secondary-button {
  border: 0;
  border-radius: 16px;
  padding: 11px 16px;
  color: var(--base-color-text-primary);
  cursor: pointer;
}

.primary-button {
  background: var(--base-color-brand-red);
  color: #fff;
}

.secondary-button {
  background: var(--base-color-surface-warm);
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.7;
  cursor: default;
}

.feedback,
.empty-suggestions p,
.suggestions-label,
.suggestions-summary,
.original-title,
.overview {
  margin: 0;
}

.feedback,
.suggestions-summary,
.original-title,
.overview,
.empty-suggestions {
  color: var(--base-color-text-secondary);
}

.feedback.error {
  color: #7a1414;
}

.suggestions-section,
.suggestion-copy,
.manual-head {
  display: grid;
  gap: 14px;
}

.suggestions-head,
.manual-head {
  grid-template-columns: minmax(0, 1fr) minmax(12rem, 0.35fr);
  align-items: end;
}

.suggestions-label {
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.suggestions-head h3,
.manual-head h3 {
  margin: 4px 0 0;
  font-size: clamp(1.35rem, 2.4vw, 1.9rem);
  line-height: 0.98;
  letter-spacing: -0.04em;
}

.suggestions-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.suggestion-card {
  align-content: start;
}

.suggestion-card img,
.poster-fallback {
  width: 100%;
  aspect-ratio: 0.68;
  object-fit: cover;
  border-radius: 20px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.poster-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-weight: 700;
}

.suggestion-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
}

.suggestion-head strong {
  font-size: 1.08rem;
  line-height: 1.05;
}

.suggestion-head span {
  color: var(--base-color-text-muted);
  font-size: 0.82rem;
  font-weight: 700;
}

.overview {
  display: -webkit-box;
  overflow: hidden;
  line-height: 1.48;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.suggestion-button,
.manual-actions {
  justify-self: start;
}

@media (max-width: 1100px) {
  .suggestions-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .lookup-form,
  .manual-form,
  .suggestions-head,
  .manual-head {
    grid-template-columns: 1fr;
  }

  .field-wide,
  .check-field {
    grid-column: auto;
  }

  .suggestions-grid {
    grid-template-columns: 1fr;
  }
}
</style>
