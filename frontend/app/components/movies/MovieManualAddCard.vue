<template>
  <section class="manual-add-card">
    <SectionHeading
      eyebrow="Entrada manual"
      title="Adicionar filme ao catálogo"
      description="O caminho principal continua sendo uma busca curta no TMDb, para o filme já nascer com contexto, IDs externos e imagem."
      summary="Se a sugestão não servir ou você já souber os IDs, a entrada mínima continua disponível logo abaixo."
    />

    <article class="form-card">
      <form class="lookup-form" @submit.prevent="handleSuggest">
        <label class="field">
          <span>Nome do filme</span>
          <input v-model="title" type="text" placeholder="Ex.: Le Mépris">
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
          <p class="suggestions-summary">{{ suggestions.length }} opção<span v-if="suggestions.length !== 1">ões</span> para "{{ searchedQuery }}"</p>
        </div>

        <div class="suggestions-grid">
          <article v-for="suggestion in suggestions" :key="suggestion.tmdbId" class="suggestion-card">
            <img v-if="suggestion.posterUrl" :src="suggestion.posterUrl" :alt="suggestion.title">
            <div v-else class="poster-fallback">Sem poster</div>

            <div class="suggestion-copy">
              <div class="suggestion-head">
                <strong>{{ suggestion.title }}</strong>
                <span v-if="suggestion.year">{{ suggestion.year }}</span>
              </div>
              <p v-if="suggestion.originalTitle && suggestion.originalTitle !== suggestion.title" class="original-title">{{ suggestion.originalTitle }}</p>
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
          <p class="suggestions-summary">Use quando você quiser registrar direto ou quando a busca externa não ajudar.</p>
        </div>

        <form class="manual-form" @submit.prevent="handleManualSubmit">
          <label class="field field-wide">
            <span>Título</span>
            <input v-model="manualTitle" type="text" placeholder="Nome do filme">
          </label>

          <label class="field">
            <span>Ano</span>
            <input v-model="year" type="number" inputmode="numeric" placeholder="2024">
          </label>

          <label class="field">
            <span>TMDb ID</span>
            <input v-model="tmdbId" type="text" placeholder="Opcional">
          </label>

          <label class="field">
            <span>IMDb ID</span>
            <input v-model="imdbId" type="text" placeholder="Opcional">
          </label>

          <div class="actions manual-actions">
            <button type="submit" class="primary-button" :disabled="submitting || !manualTitle.trim()">
              {{ submitting ? 'Registrando...' : 'Registrar filme' }}
            </button>
          </div>
        </form>
      </section>
    </article>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type {
  ManualMovieCatalogCreateResponse,
  MovieCatalogSuggestionsResponse,
} from '~/types/movies'

const props = defineProps<{
  initialTitle: string
}>()

const title = ref(props.initialTitle)
const manualTitle = ref(props.initialTitle)
const year = ref('')
const tmdbId = ref('')
const imdbId = ref('')
const showManual = ref(false)
const submitting = ref(false)
const loadingSuggestions = ref(false)
const searchedQuery = ref('')
const suggestions = ref<MovieCatalogSuggestionsResponse['suggestions']>([])
const feedback = ref<string | null>(null)
const feedbackError = ref(false)
const config = useRuntimeConfig()
const router = useRouter()

watch(() => props.initialTitle, (value) => {
  title.value = value
  manualTitle.value = value
  suggestions.value = []
  searchedQuery.value = ''
})

async function handleSuggest() {
  loadingSuggestions.value = true
  feedback.value = null
  feedbackError.value = false

  try {
    const response = await $fetch<MovieCatalogSuggestionsResponse>('/api/movies/catalog/suggestions', {
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
    imdbId: null,
  })
}

async function handleManualSubmit() {
  await createCatalogEntry({
    title: manualTitle.value.trim(),
    year: year.value.trim() ? Number(year.value) : null,
    tmdbId: tmdbId.value.trim() || null,
    imdbId: imdbId.value.trim() || null,
  })
}

async function createCatalogEntry(body: {
  title: string
  year: number | null
  tmdbId: string | null
  imdbId: string | null
}) {
  submitting.value = true
  feedback.value = null
  feedbackError.value = false

  try {
    const response = await $fetch<ManualMovieCatalogCreateResponse>('/api/movies/catalog', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body,
    })

    feedback.value = response.createdMovie ? 'Filme adicionado ao catálogo.' : 'Filme existente reaproveitado e consolidado.'
    await router.push(response.slug ? `/movies/${response.slug}` : `/movies/library?q=${encodeURIComponent(body.title)}`)
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível adicionar o filme agora.'
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

.field {
  display: grid;
  gap: 8px;
}

.field-wide {
  grid-column: span 2;
}

.field span,
.suggestions-label {
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

.actions,
.manual-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.manual-actions {
  grid-column: span 2;
}

.primary-button,
.secondary-button {
  border: 0;
  border-radius: 16px;
  padding: 12px 16px;
  cursor: pointer;
}

.primary-button {
  background: var(--base-color-brand-red);
  color: #000000;
}

.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.feedback,
.suggestions-summary,
.overview,
.original-title,
.empty-suggestions p {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.feedback.error {
  color: #7a1414;
}

.feedback {
  color: #103c25;
}

.suggestions-section,
.manual-section {
  display: grid;
  gap: 18px;
}

.suggestions-head,
.manual-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;
}

.suggestions-head h3,
.manual-head h3 {
  margin: 6px 0 0;
  font-size: 1.5rem;
  line-height: 1;
  letter-spacing: -0.04em;
}

.suggestions-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.suggestion-card {
  padding: 18px;
  background: color-mix(in srgb, white 80%, var(--base-color-surface-soft));
}

.suggestion-card img,
.poster-fallback {
  width: 100%;
  min-height: 16rem;
  border-radius: 20px;
  object-fit: cover;
  background: var(--base-color-surface-soft);
}

.poster-fallback {
  display: grid;
  place-items: center;
}

.suggestion-copy {
  display: grid;
  gap: 8px;
}

.suggestion-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: start;
}

.original-title {
  font-size: 0.9rem;
}

.overview {
  line-height: 1.6;
}

.suggestion-button {
  justify-self: start;
}

@media (max-width: 960px) {
  .suggestions-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .lookup-form,
  .manual-form,
  .suggestions-grid {
    grid-template-columns: 1fr;
  }

  .field-wide,
  .manual-actions {
    grid-column: auto;
  }

  .suggestions-head,
  .manual-head,
  .actions {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
