<template>
  <main class="artist-review-page">
    <section class="admin-subheader">
      <div>
        <p class="kicker">Painel administrativo</p>
        <h2>Duplicatas de música</h2>
      </div>
      <nav aria-label="Navegação administrativa de música">
        <NuxtLink to="/music">Música</NuxtLink>
        <NuxtLink to="/music/admin/duplicates">Faixas</NuxtLink>
        <NuxtLink to="/music/admin/album-duplicates">Álbuns</NuxtLink>
        <span>Artistas</span>
      </nav>
    </section>

    <section class="hero">
      <div>
        <p class="kicker">Administração de música</p>
        <h1>Mesclar artistas</h1>
        <p>Consolide identidades sem perder álbuns, faixas ou histórico.</p>
      </div>
    </section>

    <section class="filters panel">
      <label
        ><span>Artista</span
        ><input v-model="artistFilter" type="search" placeholder="Ex.: Björk" @keyup.enter="loadSuggestions"
      /></label>
      <button class="secondary" type="button" :disabled="suggestionsPending" @click="loadSuggestions">
        {{ suggestionsPending ? 'Buscando...' : 'Atualizar sugestões' }}
      </button>
    </section>
    <p v-if="error" class="error panel">{{ error }}</p>

    <section class="section-block">
      <div class="section-heading">
        <div>
          <p class="kicker">Fila sugerida</p>
          <h2>Possíveis convergências</h2>
        </div>
        <span>{{ suggestions.length }} grupo(s)</span>
      </div>
      <div v-if="suggestions.length" class="suggestion-grid">
        <article
          v-for="group in suggestions"
          :key="group.candidates.map((item) => item.artistId).join('-')"
          class="suggestion-card"
        >
          <header>
            <div>
              <h3>{{ group.candidates.map((item) => item.name).join(' × ') }}</h3>
              <p>{{ group.reason }}</p>
            </div>
            <span class="badge">{{ group.confidence === 'HIGH' ? 'Alta confiança' : 'Revisar' }}</span>
          </header>
          <div class="candidate-row">
            <ArtistMergeCard v-for="candidate in group.candidates" :key="candidate.artistId" :artist="candidate" />
          </div>
          <button class="primary" type="button" @click="prepareMerge(group.candidates, group.suggestedTargetArtistId)">
            Revisar mesclagem
          </button>
        </article>
      </div>
      <p v-else class="empty panel">Nenhuma sugestão encontrada para o recorte atual.</p>
    </section>

    <section class="manual panel">
      <div>
        <p class="kicker">Controle manual</p>
        <h2>Mesclar fora das sugestões</h2>
        <p>Busque e selecione ao menos dois artistas.</p>
      </div>
      <div class="manual-search">
        <input v-model="catalogQuery" type="search" placeholder="Buscar artista" @keyup.enter="searchCatalog" /><button
          class="secondary"
          type="button"
          @click="searchCatalog"
        >
          Buscar
        </button>
      </div>
      <div class="catalog-grid">
        <label v-for="artist in catalog" :key="artist.artistId" class="catalog-row"
          ><input v-model="manualSelection" type="checkbox" :value="artist.artistId" /><span
            >{{ artist.name }}<small>{{ artist.albumCount }} álbuns · {{ artist.trackCount }} faixas</small></span
          ></label
        >
      </div>
      <button class="primary" type="button" :disabled="manualSelection.length < 2" @click="prepareManualMerge">
        Revisar seleção
      </button>
    </section>

    <section v-if="preview" class="merge-editor panel" aria-live="polite">
      <div class="section-heading">
        <div>
          <p class="kicker">Confirmação</p>
          <h2>Como ficará o artista canônico</h2>
        </div>
        <button class="ghost" type="button" @click="preview = null">Fechar</button>
      </div>
      <p>
        {{ preview.totalAlbums }} álbuns · {{ preview.totalTracks }} faixas · {{ preview.totalPlaybacks }} reproduções
      </p>
      <div class="field-grid">
        <label
          ><span>Artista principal</span
          ><select v-model.number="form.targetArtistId" @change="refreshPreview">
            <option v-for="artist in preview.candidates" :key="artist.artistId" :value="artist.artistId">
              {{ artist.name }} (#{{ artist.artistId }})
            </option>
          </select></label
        >
        <label
          ><span>Nome</span
          ><select v-model.number="form.nameFromArtistId" @change="removeCanonicalNameAlias">
            <option v-for="artist in preview.candidates" :key="artist.artistId" :value="artist.artistId">
              {{ artist.name }}
            </option>
          </select></label
        >
        <label
          ><span>Foto</span
          ><select v-model.number="form.imageFromArtistId">
            <option v-for="artist in preview.candidates" :key="artist.artistId" :value="artist.artistId">
              {{ artist.name }}{{ artist.profileImageUrl ? '' : ' (sem foto)' }}
            </option>
          </select></label
        >
        <label
          ><span>Perfil MusicBrainz</span
          ><select v-model.number="form.musicBrainzFromArtistId">
            <option v-for="artist in preview.candidates" :key="artist.artistId" :value="artist.artistId">
              {{ artist.name }}{{ artist.musicBrainzArtistId ? '' : ' (sem vínculo)' }}
            </option>
          </select></label
        >
        <label
          ><span>Avaliação</span
          ><select v-model="ratingChoice">
            <option value="">Sem avaliação</option>
            <option v-for="artist in preview.candidates" :key="artist.artistId" :value="String(artist.artistId)">
              {{ artist.name }} · {{ artist.rating ? `${artist.rating}/5` : 'sem avaliação' }}
            </option>
          </select></label
        >
      </div>
      <fieldset>
        <legend>Preservar nomes como aliases</legend>
        <label v-for="artist in aliasCandidates" :key="artist.artistId" class="alias-row"
          ><input v-model="form.preserveAliasArtistIds" type="checkbox" :value="artist.artistId" /><span>{{
            artist.name
          }}</span></label
        >
      </fieldset>
      <ul>
        <li v-for="warning in preview.warnings" :key="warning">{{ warning }}</li>
      </ul>
      <button
        class="primary danger"
        type="button"
        :disabled="mergePending || hasExternalConflict"
        @click="confirmMerge"
      >
        {{ mergePending ? 'Mesclando...' : 'Confirmar mesclagem definitiva' }}
      </button>
    </section>
    <p v-if="notice" class="notice panel">{{ notice }}</p>
  </main>
</template>

<script setup lang="ts">
import type {
  ArtistMergeCandidateResponse,
  ArtistMergeCatalogResponse,
  ArtistMergePreviewResponse,
  ArtistMergeResponse,
  DuplicateArtistReviewResponse,
  DuplicateArtistSuggestionResponse,
} from '~/types/music'

const artistFilter = ref('')
const catalogQuery = ref('')
const suggestions = ref<DuplicateArtistSuggestionResponse[]>([])
const catalog = ref<ArtistMergeCandidateResponse[]>([])
const manualSelection = ref<number[]>([])
const preview = ref<ArtistMergePreviewResponse | null>(null)
const suggestionsPending = ref(false)
const mergePending = ref(false)
const error = ref('')
const notice = ref('')
const ratingChoice = ref('')
const form = reactive({
  targetArtistId: 0,
  nameFromArtistId: 0,
  imageFromArtistId: 0,
  musicBrainzFromArtistId: 0,
  preserveAliasArtistIds: [] as number[],
})
const aliasCandidates = computed(
  () => preview.value?.candidates.filter((artist) => artist.artistId !== form.nameFromArtistId) ?? [],
)
const hasExternalConflict = computed(() => {
  const candidates = preview.value?.candidates ?? []
  const spotifyIds = new Set(candidates.map((artist) => artist.spotifyId).filter(Boolean))
  const musicBrainzIds = new Set(candidates.map((artist) => artist.musicBrainzArtistId).filter(Boolean))
  return spotifyIds.size > 1 || musicBrainzIds.size > 1
})

async function loadSuggestions() {
  suggestionsPending.value = true
  error.value = ''
  try {
    suggestions.value = (
      await $fetch<DuplicateArtistReviewResponse>('/api/music/admin/artist-duplicates', {
        params: { artist: artistFilter.value || undefined },
      })
    ).items
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Não foi possível carregar as sugestões.'
  } finally {
    suggestionsPending.value = false
  }
}
async function searchCatalog() {
  if (catalogQuery.value.trim().length < 2) {
    error.value = 'Digite ao menos dois caracteres para buscar.'
    return
  }
  error.value = ''
  try {
    catalog.value = (
      await $fetch<ArtistMergeCatalogResponse>('/api/music/admin/artist-duplicates/catalog', {
        params: { q: catalogQuery.value },
      })
    ).artists
  } catch (cause) {
    error.value = errorMessage(cause, 'Não foi possível buscar o catálogo de artistas.')
  }
}
async function prepareMerge(candidates: ArtistMergeCandidateResponse[], targetId: number) {
  await safelyOpenPreview(
    candidates.map((item) => item.artistId),
    targetId,
  )
}
async function prepareManualMerge() {
  const targetId = manualSelection.value[0]
  if (targetId !== undefined) await safelyOpenPreview(manualSelection.value, targetId)
}
async function safelyOpenPreview(ids: number[], targetId: number, resetChoices = true) {
  error.value = ''
  try {
    await openPreview(ids, targetId, resetChoices)
  } catch (cause) {
    error.value = errorMessage(cause, 'Não foi possível preparar a prévia da mesclagem.')
  }
}
async function openPreview(ids: number[], targetId: number, resetChoices: boolean) {
  const data = await $fetch<ArtistMergePreviewResponse>('/api/music/admin/artist-duplicates/preview', {
    method: 'POST',
    body: { targetArtistId: targetId, sourceArtistIds: ids.filter((id) => id !== targetId) },
  })
  preview.value = data
  if (resetChoices) {
    Object.assign(form, {
      targetArtistId: targetId,
      nameFromArtistId: targetId,
      imageFromArtistId: targetId,
      musicBrainzFromArtistId: targetId,
      preserveAliasArtistIds: data.candidates.filter((item) => item.artistId !== targetId).map((item) => item.artistId),
    })
    ratingChoice.value = data.candidates.find((item) => item.artistId === targetId)?.rating ? String(targetId) : ''
    return
  }
  form.targetArtistId = targetId
  const candidateIds = new Set(data.candidates.map((item) => item.artistId))
  form.preserveAliasArtistIds = form.preserveAliasArtistIds.filter(
    (id) => candidateIds.has(id) && id !== form.nameFromArtistId,
  )
}
function removeCanonicalNameAlias() {
  form.preserveAliasArtistIds = form.preserveAliasArtistIds.filter((id) => id !== form.nameFromArtistId)
}
async function refreshPreview() {
  if (!preview.value) return
  await safelyOpenPreview(
    preview.value.candidates.map((item) => item.artistId),
    form.targetArtistId,
    false,
  )
}
async function confirmMerge() {
  if (!preview.value) return
  mergePending.value = true
  error.value = ''
  try {
    const response = await $fetch<ArtistMergeResponse>('/api/music/admin/artist-duplicates/merge', {
      method: 'POST',
      body: {
        ...form,
        preserveAliasArtistIds: form.preserveAliasArtistIds.filter((id) => id !== form.nameFromArtistId),
        sourceArtistIds: preview.value.candidates
          .map((item) => item.artistId)
          .filter((id) => id !== form.targetArtistId),
        ratingFromArtistId: ratingChoice.value ? Number(ratingChoice.value) : null,
      },
    })
    notice.value = `Mesclagem concluída no artista #${response.artistId}: ${response.mergedArtistIds.length} artista(s), ${response.movedAlbums} álbum(ns), ${response.movedTracks} faixa(s) e ${response.storedNameAliases} alias(es).`
    preview.value = null
    manualSelection.value = []
    await loadSuggestions()
  } catch (cause) {
    error.value = errorMessage(cause, 'Não foi possível concluir a mesclagem.')
  } finally {
    mergePending.value = false
  }
}
function errorMessage(cause: unknown, fallback: string) {
  return cause instanceof Error ? cause.message : fallback
}
onMounted(loadSuggestions)
</script>

<style scoped>
.artist-review-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px 20px 80px;
  color: #211922;
}
.admin-subheader,
.section-heading,
.suggestion-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.admin-subheader nav {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}
.admin-subheader a {
  color: #2b48d4;
}
.hero {
  padding: 64px 0 36px;
}
.hero h1 {
  margin: 4px 0;
  font-size: clamp(2.8rem, 7vw, 4.4rem);
  letter-spacing: -2px;
}
.kicker {
  margin: 0;
  color: #62625b;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
}
.panel,
.suggestion-card {
  border-radius: 20px;
  background: #f6f6f3;
  padding: 20px;
}
.filters,
.manual-search,
.candidate-row {
  display: flex;
  gap: 12px;
  align-items: end;
}
.filters label {
  flex: 1;
}
.filters span,
.field-grid span {
  display: block;
  margin-bottom: 6px;
  font-size: 0.8rem;
  font-weight: 700;
}
input,
select {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #91918c;
  border-radius: 16px;
  background: white;
  padding: 11px 15px;
  color: #211922;
}
button {
  border: 0;
  border-radius: 16px;
  padding: 10px 16px;
  cursor: pointer;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
button:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 3px solid #435ee5;
  outline-offset: 2px;
}
.primary {
  background: #e60023;
  color: #000;
}
.secondary {
  background: #e5e5e0;
  color: #211922;
}
.ghost {
  background: transparent;
}
.section-block,
.manual,
.merge-editor {
  margin-top: 32px;
}
.suggestion-grid {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}
.suggestion-card h3 {
  margin: 0;
}
.candidate-row {
  margin: 18px 0;
  align-items: stretch;
}
.badge {
  border-radius: 12px;
  background: #e5e5e0;
  padding: 6px 10px;
  font-size: 0.75rem;
}
.manual-search {
  margin: 16px 0;
}
.manual-search input {
  flex: 1;
}
.catalog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 16px;
}
.catalog-row,
.alias-row {
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 16px;
  background: white;
  padding: 10px;
}
.catalog-row input,
.alias-row input {
  width: auto;
}
.catalog-row small {
  display: block;
  color: #62625b;
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin: 20px 0;
}
fieldset {
  border: 1px solid #e0e0d9;
  border-radius: 16px;
  margin: 20px 0;
  padding: 14px;
}
.danger {
  margin-top: 8px;
}
.error {
  color: #9e0a0a;
}
.notice {
  margin-top: 20px;
  color: #103c25;
}
.empty {
  margin-top: 16px;
}
@media (max-width: 700px) {
  .admin-subheader,
  .section-heading,
  .suggestion-card header,
  .filters,
  .candidate-row {
    align-items: stretch;
    flex-direction: column;
  }
  .catalog-grid,
  .field-grid {
    grid-template-columns: 1fr;
  }
  .hero {
    padding-top: 40px;
  }
}
</style>
