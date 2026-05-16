<template>
  <section class="manual-add-card">
    <SectionHeading
      eyebrow="Entrada manual"
      title="Adicionar game ao catálogo"
      description="A criação continua manual, mas a busca inicial usa IGDB para preencher metadados e SteamGridDB para trazer imagem."
      summary="Se a sugestão não servir, crie uma entrada mínima com título e ano."
    />

    <article class="form-card">
      <form class="lookup-form" @submit.prevent="handleSuggest">
        <label class="field">
          <span>Nome do game</span>
          <input v-model="title" type="text" placeholder="Ex.: Hollow Knight" />
        </label>

        <div class="actions">
          <button type="submit" class="primary-button" :disabled="loadingSuggestions || !title.trim()">
            {{ loadingSuggestions ? 'Buscando...' : 'Buscar no IGDB' }}
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
            <p class="suggestions-label">Sugestões IGDB</p>
            <h3>Escolha a melhor correspondência</h3>
          </div>
          <p class="suggestions-summary">{{ suggestions.length }} opções para "{{ searchedQuery }}"</p>
        </div>

        <div class="suggestions-grid">
          <article v-for="suggestion in suggestions" :key="suggestion.igdbId" class="suggestion-card">
            <img v-if="suggestion.coverUrl" :src="suggestion.coverUrl" :alt="suggestion.title" />
            <div v-else class="poster-fallback">Sem imagem</div>

            <div class="suggestion-copy">
              <div class="suggestion-head">
                <strong>{{ suggestion.title }}</strong>
                <span v-if="suggestion.year">{{ suggestion.year }}</span>
              </div>
              <p class="overview">{{ suggestion.overview || 'Sem descrição curta disponível no provider.' }}</p>
            </div>

            <button
              type="button"
              class="primary-button suggestion-button"
              :disabled="submitting"
              @click="handleUseSuggestion(suggestion.igdbId)"
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
          <p class="suggestions-summary">Use quando a busca externa não ajudar ou quando você já souber o IGDB ID.</p>
        </div>

        <form class="manual-form" @submit.prevent="handleManualSubmit">
          <label class="field field-wide">
            <span>Título</span>
            <input v-model="manualTitle" type="text" placeholder="Nome do game" />
          </label>

          <label class="field">
            <span>Ano</span>
            <input v-model="year" type="number" inputmode="numeric" placeholder="2024" />
          </label>

          <label class="field">
            <span>IGDB ID</span>
            <input v-model="igdbId" type="text" placeholder="Opcional" />
          </label>

          <div class="actions manual-actions">
            <button type="submit" class="primary-button" :disabled="submitting || !manualTitle.trim()">
              {{ submitting ? 'Registrando...' : 'Registrar game' }}
            </button>
          </div>
        </form>
      </section>
    </article>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { GameCatalogSuggestionsResponse, ManualGameCatalogCreateResponse } from '~/types/games'

const props = defineProps<{ initialTitle: string }>()

const title = ref(props.initialTitle)
const manualTitle = ref(props.initialTitle)
const year = ref('')
const igdbId = ref('')
const showManual = ref(false)
const submitting = ref(false)
const loadingSuggestions = ref(false)
const searchedQuery = ref('')
const suggestions = ref<GameCatalogSuggestionsResponse['suggestions']>([])
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
    const response = await $fetch<GameCatalogSuggestionsResponse>('/api/games/catalog/suggestions', {
      baseURL: config.public.apiBase,
      query: { q: title.value.trim() },
    })
    suggestions.value = response.suggestions
    searchedQuery.value = response.query
  } catch (error) {
    feedback.value = extractErrorMessage(error, 'Não foi possível buscar sugestões agora.')
    feedbackError.value = true
    suggestions.value = []
  } finally {
    loadingSuggestions.value = false
  }
}

async function handleUseSuggestion(selectedIgdbId: string) {
  await createCatalogEntry({
    title: title.value.trim(),
    year: year.value.trim() ? Number(year.value) : null,
    igdbId: selectedIgdbId,
  })
}

async function handleManualSubmit() {
  await createCatalogEntry({
    title: manualTitle.value.trim(),
    year: year.value.trim() ? Number(year.value) : null,
    igdbId: igdbId.value.trim() || null,
  })
}

async function createCatalogEntry(body: { title: string; year: number | null; igdbId: string | null }) {
  submitting.value = true
  feedback.value = null
  feedbackError.value = false

  try {
    const response = await $fetch<ManualGameCatalogCreateResponse>('/api/games/catalog', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body,
    })

    feedback.value = response.createdGame ? 'Game adicionado ao catálogo.' : 'Game existente reaproveitado.'
    await router.push(response.slug ? `/games/${response.slug}` : `/games?q=${encodeURIComponent(body.title)}`)
  } catch (error) {
    feedback.value = extractErrorMessage(error, 'Não foi possível adicionar o game agora.')
    feedbackError.value = true
  } finally {
    submitting.value = false
  }
}

function extractErrorMessage(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null && 'data' in error) {
    const data = (error as { data?: { message?: string; error?: string } }).data
    return data?.message || data?.error || fallback
  }

  return error instanceof Error ? error.message : fallback
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
  grid-template-columns: minmax(0, 1fr) 9rem 12rem auto;
}

.field {
  display: grid;
  gap: 8px;
}

.field span,
.suggestions-label {
  color: var(--base-color-text-secondary);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field input {
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
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

.suggestions-head,
.manual-head,
.suggestion-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.suggestions-label,
.suggestions-summary,
.overview,
.feedback,
.empty-suggestions p {
  margin: 0;
}

h3 {
  margin: 4px 0 0;
}

.suggestions-summary,
.overview,
.feedback {
  color: var(--base-color-text-secondary);
}

.suggestions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.suggestion-card {
  align-content: start;
}

.suggestion-card img,
.poster-fallback {
  width: 100%;
  aspect-ratio: 2 / 3;
  border-radius: 20px;
  object-fit: cover;
  background: var(--base-color-surface-warm);
}

.poster-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
}

.feedback.error {
  color: #7a1414;
}

@media (max-width: 760px) {
  .lookup-form,
  .manual-form {
    grid-template-columns: 1fr;
  }
}
</style>
