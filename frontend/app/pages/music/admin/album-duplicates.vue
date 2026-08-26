<template>
  <main class="album-review-page">
    <section class="admin-subheader">
      <div>
        <p class="kicker">Painel administrativo</p>
        <h2>Duplicatas de música</h2>
      </div>
      <nav aria-label="Navegação administrativa de música">
        <NuxtLink :to="artistReturnUrl">{{ contextualArtistId ? 'Voltar ao artista' : 'Música' }}</NuxtLink>
        <NuxtLink to="/music/admin/duplicates">Faixas duplicadas</NuxtLink>
        <span>Álbuns duplicados</span>
      </nav>
    </section>

    <section class="hero">
      <div>
        <p class="kicker">Administração de música</p>
        <h1>Mesclar álbuns</h1>
        <p>A mesclagem exige confirmação e preserva histórico e identificadores externos.</p>
      </div>
    </section>

    <section class="filters panel">
      <label
        ><span>Artista</span><input v-model="artistFilter" type="search" placeholder="Ex.: Oswaldo Montenegro"
      /></label>
      <label><span>Álbum</span><input v-model="albumFilter" type="search" placeholder="Ex.: Seu Francisco" /></label>
      <button class="secondary" type="button" :disabled="suggestionsPending" @click="loadSuggestions">
        {{ suggestionsPending ? 'Buscando...' : 'Atualizar sugestões' }}
      </button>
    </section>

    <p v-if="suggestionsError" class="error panel">{{ suggestionsError }}</p>
    <section v-else class="section-block">
      <div class="section-heading">
        <div>
          <p class="kicker">Fila sugerida</p>
          <h2>
            {{
              contextualArtistName ? `Possíveis convergências de ${contextualArtistName}` : 'Possíveis convergências'
            }}
          </h2>
        </div>
        <span>{{ suggestions.length }} grupo(s)</span>
      </div>
      <div v-if="suggestions.length" class="suggestion-grid">
        <article v-for="group in suggestions" :key="suggestionKey(group)" class="suggestion-card">
          <header>
            <div class="suggestion-identity">
              <p v-if="!contextualArtistId" class="artist">{{ group.artistName }}</p>
              <div class="album-titles" aria-label="Álbuns desta revisão">
                <div v-for="album in group.candidates" :key="album.albumId">
                  <h3>{{ album.title }}</h3>
                  <span>{{ album.year ?? 'Ano desconhecido' }} · álbum #{{ album.albumId }}</span>
                </div>
              </div>
              <p>{{ group.reason }}</p>
            </div>
            <span class="badge">{{ confidenceLabel(group.confidence) }}</span>
          </header>
          <div class="albums">
            <AlbumChoice
              v-for="album in group.candidates"
              :key="album.albumId"
              :album="album"
              :suggested="album.albumId === group.suggestedTargetAlbumId"
            />
          </div>
          <button class="primary" type="button" @click="prepareMerge(group.candidates, group.suggestedTargetAlbumId)">
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
        <p>Busque um artista ou álbum e selecione ao menos dois álbuns do mesmo artista.</p>
      </div>
      <div class="manual-search">
        <input
          v-model="catalogQuery"
          type="search"
          placeholder="Buscar artista ou álbum"
          @keyup.enter="searchCatalog"
        /><button class="secondary" type="button" @click="searchCatalog">Buscar</button>
      </div>
      <div v-for="artist in catalog" :key="artist.artistId" class="catalog-artist">
        <h3>{{ artist.artistName }}</h3>
        <label v-for="album in artist.albums" :key="album.albumId" class="catalog-row">
          <input
            v-model="manualSelection"
            type="checkbox"
            :value="album.albumId"
            @change="selectManualArtist(artist.artistId)"
          />
          <span
            >{{ album.title }}
            <small>{{ album.year ?? 'Ano desconhecido' }} · {{ album.trackCount }} faixas</small></span
          >
        </label>
      </div>
      <button class="primary" type="button" :disabled="manualSelection.length < 2" @click="prepareManualMerge">
        Revisar seleção
      </button>
    </section>

    <section v-if="preview" class="merge-editor panel" aria-live="polite">
      <div class="section-heading">
        <div>
          <p class="kicker">Confirmação</p>
          <h2>Como ficará o álbum canônico</h2>
        </div>
        <button class="ghost" type="button" @click="preview = null">Fechar</button>
      </div>
      <p>
        {{ preview.artistName }} · {{ preview.totalTracks }} vínculos de faixa ·
        {{ preview.totalPlaybacks }} reproduções
      </p>
      <div class="field-grid">
        <label
          ><span>Álbum principal</span
          ><select v-model.number="mergeForm.targetAlbumId" @change="refreshPreview">
            <option v-for="album in preview.candidates" :key="album.albumId" :value="album.albumId">
              {{ album.title }} (#{{ album.albumId }})
            </option>
          </select></label
        >
        <label
          ><span>Título</span
          ><select v-model.number="mergeForm.titleFromAlbumId">
            <option v-for="album in preview.candidates" :key="album.albumId" :value="album.albumId">
              {{ album.title }}
            </option>
          </select></label
        >
        <label
          ><span>Capa</span
          ><select v-model.number="mergeForm.coverFromAlbumId">
            <option v-for="album in preview.candidates" :key="album.albumId" :value="album.albumId">
              {{ album.title }}{{ album.coverUrl ? '' : ' (sem capa)' }}
            </option>
          </select></label
        >
        <label
          ><span>Ano</span
          ><select v-model.number="mergeForm.yearFromAlbumId">
            <option v-for="album in preview.candidates" :key="album.albumId" :value="album.albumId">
              {{ album.title }} · {{ album.year ?? 'sem ano' }}
            </option>
          </select></label
        >
        <label
          ><span>Avaliação</span
          ><select v-model="ratingChoice">
            <option value="">Sem avaliação</option>
            <option v-for="album in preview.candidates" :key="album.albumId" :value="String(album.albumId)">
              {{ album.title }} · {{ album.rating ? `${album.rating}/5` : 'sem avaliação' }}
            </option>
          </select></label
        >
        <label
          ><span>Usar ordem das faixas de</span
          ><select v-model.number="mergeForm.trackOrderFromAlbumId" @change="refreshPreview">
            <option v-for="album in preview.candidates" :key="album.albumId" :value="album.albumId">
              {{ album.title }} (#{{ album.albumId }})
            </option>
          </select></label
        >
      </div>
      <div class="track-order-summary">
        <strong>{{ preview.trackOrder.positionedTrackCount }} faixa(s) manterão uma posição</strong>
        <span>{{ preview.trackOrder.unpositionedTrackCount }} ficarão sem número</span>
        <span v-if="preview.trackOrder.conflictedTrackCount">
          {{ preview.trackOrder.conflictedTrackCount }} por conflito com a tracklist escolhida
        </span>
      </div>
      <ul>
        <li v-for="warning in preview.warnings" :key="warning">{{ warning }}</li>
      </ul>
      <button class="primary danger" type="button" :disabled="mergePending" @click="confirmMerge">
        {{ mergePending ? 'Mesclando...' : 'Confirmar mesclagem definitiva' }}
      </button>
    </section>

    <p v-if="notice" class="notice panel">{{ notice }}</p>
  </main>
</template>

<script setup lang="ts">
import type {
  AlbumMergeCandidateResponse,
  AlbumMergeCatalogResponse,
  AlbumMergePreviewResponse,
  AlbumMergeResponse,
  DuplicateAlbumReviewResponse,
  DuplicateAlbumSuggestionResponse,
} from '~/types/music'

const config = useRuntimeConfig()
const route = useRoute()
const contextualArtistName = routeQueryValue(route.query.artist)
const contextualArtistId = routeQueryValue(route.query.artistId)
const artistReturnUrl = computed(() =>
  contextualArtistId ? `/music/artists/${encodeURIComponent(contextualArtistId)}` : '/music',
)
const artistFilter = ref(contextualArtistName)
const albumFilter = ref('')
const suggestions = ref<DuplicateAlbumSuggestionResponse[]>([])
const suggestionsPending = ref(false)
const suggestionsError = ref('')
const catalogQuery = ref(contextualArtistName)
const catalog = ref<AlbumMergeCatalogResponse['artists']>([])
const manualSelection = ref<number[]>([])
const manualArtistId = ref<number | null>(null)
const preview = ref<AlbumMergePreviewResponse | null>(null)
const mergePending = ref(false)
const notice = ref('')
const ratingChoice = ref('')
const mergeForm = reactive({
  targetAlbumId: 0,
  titleFromAlbumId: 0,
  coverFromAlbumId: 0,
  yearFromAlbumId: 0,
  trackOrderFromAlbumId: 0,
})

async function loadSuggestions() {
  suggestionsPending.value = true
  suggestionsError.value = ''
  try {
    const response = await $fetch<DuplicateAlbumReviewResponse>('/api/music/admin/album-duplicates', {
      baseURL: config.public.apiBase,
      query: { artist: artistFilter.value || undefined, album: albumFilter.value || undefined },
    })
    suggestions.value = response.items
  } catch (error) {
    suggestionsError.value = error instanceof Error ? error.message : 'Não foi possível carregar as sugestões.'
  } finally {
    suggestionsPending.value = false
  }
}

async function searchCatalog() {
  if (catalogQuery.value.trim().length < 2) return
  const response = await $fetch<AlbumMergeCatalogResponse>('/api/music/admin/album-duplicates/catalog', {
    baseURL: config.public.apiBase,
    query: { q: catalogQuery.value.trim() },
  })
  catalog.value = response.artists
  manualSelection.value = []
  manualArtistId.value = null
}

function selectManualArtist(artistId: number) {
  manualArtistId.value = artistId
  const allowed = new Set(
    catalog.value.find((item) => item.artistId === artistId)?.albums.map((album) => album.albumId) ?? [],
  )
  manualSelection.value = manualSelection.value.filter((id) => allowed.has(id))
}

function prepareManualMerge() {
  const artist = catalog.value.find((item) => item.artistId === manualArtistId.value)
  const albums = artist?.albums.filter((album) => manualSelection.value.includes(album.albumId)) ?? []
  if (albums.length >= 2) prepareMerge(albums, albums[0]!.albumId)
}

async function prepareMerge(albums: AlbumMergeCandidateResponse[], targetId: number) {
  mergeForm.targetAlbumId = targetId
  const suggested = albums.find((album) => album.albumId === targetId) ?? albums[0]!
  mergeForm.titleFromAlbumId = suggested.albumId
  mergeForm.coverFromAlbumId = suggested.albumId
  mergeForm.yearFromAlbumId = suggested.albumId
  mergeForm.trackOrderFromAlbumId = suggested.albumId
  ratingChoice.value = suggested.rating ? String(suggested.albumId) : ''
  await fetchPreview(albums.map((album) => album.albumId))
  nextTick(() => document.querySelector('.merge-editor')?.scrollIntoView({ behavior: 'smooth' }))
}

async function fetchPreview(allIds: number[]) {
  preview.value = await $fetch<AlbumMergePreviewResponse>('/api/music/admin/album-duplicates/preview', {
    baseURL: config.public.apiBase,
    method: 'POST',
    body: {
      targetAlbumId: mergeForm.targetAlbumId,
      sourceAlbumIds: allIds.filter((id) => id !== mergeForm.targetAlbumId),
      trackOrderFromAlbumId: mergeForm.trackOrderFromAlbumId,
    },
  })
}

async function refreshPreview() {
  if (preview.value) await fetchPreview(preview.value.candidates.map((album) => album.albumId))
}

async function confirmMerge() {
  if (!preview.value || !window.confirm('Esta mesclagem é definitiva. Deseja continuar?')) return
  mergePending.value = true
  try {
    const allIds = preview.value.candidates.map((album) => album.albumId)
    const response = await $fetch<AlbumMergeResponse>('/api/music/admin/album-duplicates/merge', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        ...mergeForm,
        ratingFromAlbumId: ratingChoice.value ? Number(ratingChoice.value) : null,
        sourceAlbumIds: allIds.filter((id) => id !== mergeForm.targetAlbumId),
      },
    })
    notice.value = `Mesclagem concluída no álbum #${response.albumId}: ${response.mergedAlbumIds.length} álbum(ns) absorvido(s), ${response.movedPlaybacks} reprodução(ões) migrada(s) e ${response.storedTitleAliases} alias(es) preservado(s).`
    preview.value = null
    manualSelection.value = []
    await loadSuggestions()
    if (contextualArtistName) await searchCatalog()
  } catch (error) {
    notice.value = error instanceof Error ? error.message : 'Não foi possível concluir a mesclagem.'
  } finally {
    mergePending.value = false
  }
}

function suggestionKey(group: DuplicateAlbumSuggestionResponse) {
  return group.candidates.map((album) => album.albumId).join('-')
}
function confidenceLabel(value: string) {
  return value === 'HIGH' ? 'Alta confiança' : 'Revisar'
}

function routeQueryValue(value: string | string[] | null | undefined) {
  return Array.isArray(value) ? (value[0] ?? '') : (value ?? '')
}

await Promise.all([loadSuggestions(), contextualArtistName ? searchCatalog() : Promise.resolve()])
useHead({
  title: 'Mesclar álbuns · Media Pulse',
  meta: [{ name: 'description', content: 'Revisão e mesclagem de álbuns duplicados da biblioteca musical.' }],
})
</script>

<style scoped>
.album-review-page {
  display: grid;
  gap: 32px;
  width: min(1320px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
  color: #211922;
}
.admin-subheader,
.hero,
.panel {
  border-radius: 28px;
  background: #f6f6f3;
  padding: 24px;
}
.admin-subheader,
.section-heading,
.suggestion-card header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  align-items: flex-start;
}
.admin-subheader h2,
.section-heading h2,
.manual h2 {
  margin: 4px 0 8px;
}
.admin-subheader p,
.hero p,
.manual p,
.suggestion-card header p {
  color: #62625b;
}
.admin-subheader nav {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.admin-subheader nav a,
.admin-subheader nav span {
  padding: 8px 12px;
  border-radius: 16px;
  background: #e5e5e0;
  color: #211922;
  text-decoration: none;
}
.admin-subheader nav span {
  background: #211922;
  color: white;
}
.hero {
  padding: 40px;
}
.hero h1 {
  max-width: 780px;
  margin: 4px 0 12px;
  font-size: clamp(42px, 7vw, 70px);
  line-height: 0.98;
  letter-spacing: -2px;
}
.hero p {
  max-width: 720px;
  font-size: 16px;
}
.kicker {
  margin: 0;
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.filters {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 16px;
  align-items: end;
}
.filters label,
.field-grid label {
  display: grid;
  gap: 6px;
  font-size: 12px;
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
.primary,
.secondary,
.ghost {
  border: 0;
  border-radius: 16px;
  padding: 10px 16px;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}
.primary {
  background: #e60023;
  color: white;
}
.secondary {
  background: #e5e5e0;
  color: #211922;
}
.ghost {
  background: transparent;
}
.suggestion-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 16px;
  margin-top: 16px;
}
.suggestion-card {
  display: grid;
  gap: 18px;
  padding: 20px;
  border-radius: 24px;
  background: #f6f6f3;
}
.suggestion-card header p {
  margin: 2px 0;
}
.suggestion-identity {
  min-width: 0;
}
.album-titles {
  display: grid;
  gap: 8px;
  margin: 4px 0 12px;
}
.album-titles > div {
  padding-left: 12px;
  border-left: 3px solid #e60023;
}
.album-titles h3 {
  overflow: hidden;
  margin: 0;
  color: #211922;
  font-size: 16px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.album-titles span {
  color: #62625b;
  font-size: 12px;
}
.artist {
  font-weight: 700 !important;
  color: #211922 !important;
}
.badge {
  border-radius: 12px;
  background: #e0e0d9;
  padding: 6px 9px;
  font-size: 12px;
}
.albums {
  display: grid;
  gap: 10px;
}
.manual {
  display: grid;
  gap: 18px;
}
.manual-search {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
}
.catalog-artist {
  display: grid;
  gap: 8px;
  border-top: 1px solid #e5e5e0;
  padding-top: 16px;
}
.catalog-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 16px;
  background: white;
}
.catalog-row input {
  width: auto;
}
.catalog-row small {
  display: block;
  color: #62625b;
}
.merge-editor {
  display: grid;
  gap: 20px;
  border: 2px solid #e60023;
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 14px;
}
.track-order-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  border-radius: 16px;
  background: #e5e5e0;
  padding: 12px 16px;
  color: #62625b;
  font-size: 14px;
}
.track-order-summary strong {
  color: #211922;
}
.danger {
  justify-self: start;
}
.notice {
  background: #e7f1ea;
  color: #103c25;
}
.error {
  color: #9e0a0a;
}
.empty {
  text-align: center;
}
@media (max-width: 720px) {
  .album-review-page {
    width: min(100vw - 20px, 1320px);
    padding-top: 20px;
  }
  .admin-subheader,
  .section-heading,
  .suggestion-card header {
    display: grid;
  }
  .filters {
    grid-template-columns: 1fr;
  }
  .suggestion-grid {
    grid-template-columns: 1fr;
  }
  .hero {
    padding: 28px 20px;
  }
}
</style>
