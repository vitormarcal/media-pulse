<template>
  <main class="backfill-page">
    <section class="admin-subheader">
      <div>
        <p class="eyebrow">Painel administrativo</p>
        <h2>Administração de música</h2>
      </div>
      <nav aria-label="Navegação administrativa de música">
        <NuxtLink to="/music">Música</NuxtLink>
        <NuxtLink to="/music/admin/duplicates">Faixas duplicadas</NuxtLink>
        <NuxtLink to="/music/admin/album-duplicates">Álbuns duplicados</NuxtLink>
        <NuxtLink to="/music/admin/artist-duplicates">Artistas duplicados</NuxtLink>
        <span aria-current="page">Créditos dos álbuns</span>
      </nav>
    </section>

    <header class="hero">
      <p class="eyebrow">Curadoria do acervo</p>
      <h1>Completar créditos dos álbuns</h1>
      <p>Consulte os artistas no Spotify ou MusicBrainz, revise a prévia e aplique aos álbuns do seu acervo.</p>
    </header>

    <div class="workspace">
      <div class="main-column">
        <section class="panel" aria-labelledby="new-batch-title">
          <h2 id="new-batch-title">Novo lote</h2>
          <p class="hint">Comece com um álbum. Créditos já preenchidos serão preservados.</p>
          <form @submit.prevent="generatePreview">
            <fieldset :disabled="!!busy" class="mode-options">
              <legend class="field-label">Escolher álbuns</legend>
              <label><input v-model="mode" type="radio" value="automatic" /> Seleção automática</label>
              <label><input v-model="mode" type="radio" value="manual" /> Buscar no acervo</label>
            </fieldset>
            <div v-if="mode === 'automatic'" class="automatic-options">
              <label for="batch-limit" class="field-label">Quantidade de álbuns</label>
              <input
                id="batch-limit"
                v-model.number="limit"
                type="number"
                min="1"
                max="10"
                required
                :disabled="!!busy"
              />
              <p class="hint">De 1 a 10 álbuns com correspondência conhecida no Spotify ou MusicBrainz.</p>
            </div>
            <div v-else class="manual-options">
              <label for="album-search" class="field-label">Nome do álbum ou artista</label>
              <div class="search-controls">
                <input
                  id="album-search"
                  v-model="searchText"
                  type="search"
                  placeholder="Ex.: Elis & Tom"
                  :disabled="!!busy"
                  @keydown.enter.prevent="searchAlbums"
                />
                <button
                  type="button"
                  class="secondary"
                  :disabled="!!busy || searching || searchText.trim().length < 2"
                  @click="searchAlbums"
                >
                  Buscar
                </button>
              </div>
              <p class="hint">{{ selectedAlbums.length }} de 10 álbuns selecionados.</p>
              <ul v-if="selectedAlbums.length" class="selected-albums" aria-label="Álbuns selecionados">
                <li v-for="album in selectedAlbums" :key="album.id">
                  <span>{{ album.title }}</span>
                  <button
                    type="button"
                    :disabled="!!busy"
                    :aria-label="`Remover ${album.title} do lote`"
                    @click="toggleAlbum(album)"
                  >
                    ×
                  </button>
                </li>
              </ul>
              <p v-if="searchError" role="alert" class="error">{{ searchError }}</p>
              <p v-else-if="searching" role="status" class="hint">Buscando álbuns...</p>
              <div v-else-if="searchResults.length" class="search-results" aria-label="Resultados da busca">
                <label v-for="album in searchResults" :key="album.id" class="search-result">
                  <input
                    type="checkbox"
                    :checked="selectedIds.has(album.id)"
                    :disabled="!!busy || (!selectedIds.has(album.id) && selectedAlbums.length >= 10)"
                    @change="toggleAlbum(album)"
                  />
                  <span
                    ><strong>{{ album.title }}</strong
                    ><small
                      >{{ albumArtistLabel(album.artists, album.artistName)
                      }}<template v-if="album.year"> · {{ album.year }}</template></small
                    ></span
                  >
                </label>
              </div>
              <p v-else-if="searchText.trim().length >= 2" class="hint">Nenhum álbum encontrado para esta busca.</p>
            </div>
            <button type="submit" class="primary" :disabled="!!busy || (mode === 'manual' && !selectedAlbums.length)">
              {{ busy === 'preview' ? 'Consultando créditos...' : 'Gerar prévia' }}
            </button>
            <p class="hint">Gerar uma prévia ainda não altera os créditos.</p>
          </form>
        </section>

        <p v-if="error" role="alert" class="feedback error">{{ error }}</p>
        <p v-if="notice" role="status" class="feedback">{{ notice }}</p>

        <section
          v-if="batch"
          class="panel batch-panel"
          aria-labelledby="batch-title"
          :aria-busy="busy === 'apply' || busy === 'undo' || busy === 'load'"
        >
          <header class="section-heading">
            <div>
              <p class="eyebrow">Prévia e resultado</p>
              <h2 id="batch-title" ref="batchHeading" tabindex="-1">Lote de {{ formatDate(batch.createdAt) }}</h2>
              <p class="hint">
                {{ batch.items.length }} álbum(ns) · {{ readyCount }} pronto(s) · {{ appliedCount }} aplicado(s)
              </p>
            </div>
            <button type="button" class="secondary" :disabled="!!busy" @click="openBatch(batch.batchId)">
              {{ busy === 'load' ? 'Atualizando...' : 'Atualizar resultado' }}
            </button>
          </header>
          <div v-if="batch.items.length" class="batch-items">
            <AlbumCreditBackfillItem v-for="item in batch.items" :key="item.albumId" :item="item" />
          </div>
          <p v-else class="hint">
            Nenhum álbum elegível neste lote. Os álbuns com créditos completos já estão preservados.
          </p>

          <div v-if="readyCount" class="batch-actions">
            <p>Aplicar todas as propostas prontas deste lote. Álbuns já aplicados não serão repetidos.</p>
            <button type="button" class="primary" :disabled="!!busy" @click="applyBatch">
              {{ busy === 'apply' ? 'Aplicando...' : `Aplicar propostas (${readyCount})` }}
            </button>
          </div>
          <div v-if="pendingIds.length" class="batch-actions quiet-actions">
            <p>Para consultas interrompidas ou conflitos corrigidos, gere uma nova prévia dos álbuns pendentes.</p>
            <button type="button" class="secondary" :disabled="!!busy" @click="retryPreview">
              Gerar nova prévia dos pendentes ({{ pendingIds.length }})
            </button>
          </div>
          <div v-if="appliedCount" class="undo-area">
            <button v-if="!undoPrompt" type="button" class="secondary" :disabled="!!busy" @click="undoPrompt = true">
              Desfazer alterações deste lote
            </button>
            <div v-else class="undo-confirmation" aria-labelledby="undo-title">
              <h3 id="undo-title">Restaurar os créditos anteriores?</h3>
              <p>
                O pedido vale para {{ appliedCount }} álbum(ns) aplicado(s). Álbuns editados depois do lote serão
                preservados. O histórico de reproduções permanece.
              </p>
              <div class="button-row">
                <button type="button" class="primary" :disabled="!!busy" @click="confirmUndo">
                  {{ busy === 'undo' ? 'Desfazendo...' : 'Confirmar desfazimento' }}
                </button>
                <button type="button" class="secondary" :disabled="!!busy" @click="undoPrompt = false">Cancelar</button>
              </div>
            </div>
          </div>
        </section>
      </div>

      <aside class="panel history" aria-labelledby="history-title">
        <header class="section-heading">
          <h2 id="history-title">Lotes recentes</h2>
          <button type="button" class="secondary compact" :disabled="!!busy" @click="loadRecent">
            {{ busy === 'history' ? 'Carregando...' : 'Atualizar' }}
          </button>
        </header>
        <p v-if="historyError" role="alert" class="error">{{ historyError }}</p>
        <div v-if="recent.length" class="history-list">
          <button
            v-for="item in recent"
            :key="item.batchId"
            type="button"
            class="history-item"
            :aria-pressed="batch?.batchId === item.batchId"
            :disabled="!!busy"
            @click="selectBatch(item.batchId)"
          >
            <strong>{{ formatDate(item.createdAt) }}</strong
            ><span>{{ item.items.length }} álbum(ns)</span><small>{{ batchSummary(item) }}</small>
          </button>
        </div>
        <p v-else-if="busy === 'history'" role="status" class="hint">Carregando lotes...</p>
        <p v-else-if="!historyError" class="hint">Nenhum lote registrado. Gere uma prévia para começar.</p>
      </aside>
    </div>
  </main>
</template>

<script setup lang="ts">
import AlbumCreditBackfillItem from '~/components/music/AlbumCreditBackfillItem.vue'
import { useAlbumCreditBackfill } from '~/composables/useAlbumCreditBackfill'
import {
  backfillStatusLabels,
  type AlbumCreditBackfillBatch,
  type AlbumCreditBackfillStatus,
} from '~/types/musicBackfill'
import { albumArtistLabel } from '~/utils/musicCredits'

useHead({ title: 'Créditos dos álbuns · Administração · Media Pulse' })
const {
  mode,
  limit,
  selectedAlbums,
  selectedIds,
  searchText,
  searchResults,
  searching,
  searchError,
  recent,
  batch,
  busy,
  error,
  notice,
  historyError,
  readyCount,
  appliedCount,
  pendingIds,
  loadRecent,
  preview,
  openBatch,
  apply,
  undo,
  toggleAlbum,
  searchAlbums,
} = useAlbumCreditBackfill()
const batchHeading = ref<HTMLElement | null>(null)
const undoPrompt = ref(false)
watch(
  () => batch.value?.batchId,
  () => {
    undoPrompt.value = false
  },
)
onMounted(loadRecent)

function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
}
function batchSummary(value: AlbumCreditBackfillBatch) {
  const counts = new Map<AlbumCreditBackfillStatus, number>()
  for (const item of value.items) counts.set(item.status, (counts.get(item.status) ?? 0) + 1)
  return (
    [...counts].map(([status, count]) => `${count} ${backfillStatusLabels[status].toLowerCase()}`).join(' · ') ||
    'Sem álbuns'
  )
}
async function focusBatch(success: boolean) {
  if (!success) return
  await nextTick()
  batchHeading.value?.focus()
}
async function generatePreview() {
  await focusBatch(await preview())
}
async function retryPreview() {
  await focusBatch(await preview(pendingIds.value))
}
async function selectBatch(id: string) {
  await focusBatch(await openBatch(id))
}
async function applyBatch() {
  await focusBatch(await apply())
}
async function confirmUndo() {
  if (await undo()) {
    undoPrompt.value = false
    await focusBatch(true)
  }
}
</script>

<style scoped>
.backfill-page {
  display: grid;
  gap: 28px;
  width: min(1320px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
  color: var(--base-color-text-primary);
}
.admin-subheader,
.panel,
.hero {
  padding: 24px;
  border-radius: 28px;
  background: var(--base-color-surface-wash);
  border: 1px solid var(--base-color-border);
}
.admin-subheader {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}
.admin-subheader nav,
.button-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.admin-subheader nav a,
.admin-subheader nav span {
  padding: 8px 12px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: inherit;
  font-size: 0.8rem;
  text-decoration: none;
}
.admin-subheader nav span {
  font-weight: 700;
}
h1,
h2,
h3,
p {
  margin: 0;
}
h2 {
  font-size: 1.2rem;
  line-height: 1.35;
}
h3 {
  font-size: 1rem;
}
.hero {
  display: grid;
  gap: 10px;
}
.hero h1 {
  font-size: clamp(2rem, 4.5vw, 3.4rem);
  letter-spacing: -0.04em;
  line-height: 1.05;
}
.hero > p:last-child {
  max-width: 65ch;
  color: var(--base-color-text-secondary);
}
.eyebrow {
  margin-bottom: 6px;
  color: var(--base-color-text-secondary);
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  font-weight: 700;
}
.workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 0.36fr);
  gap: 24px;
  align-items: start;
}
.main-column,
form,
.batch-items {
  display: grid;
  gap: 18px;
  min-width: 0;
}
form {
  margin-top: 20px;
}
.mode-options {
  border: 0;
  padding: 0;
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 12px 22px;
}
.mode-options label,
.search-result {
  display: flex;
  align-items: center;
  gap: 10px;
}
.field-label {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  margin-bottom: 8px;
}
.hint {
  margin-top: 8px;
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
  line-height: 1.5;
}
.automatic-options > input {
  max-width: 120px;
}
input[type='number'],
input[type='search'] {
  width: 100%;
  min-width: 0;
  padding: 11px 14px;
  border-radius: 16px;
  border: 1px solid var(--base-color-border);
  background: white;
  font: inherit;
  color: inherit;
}
input[type='checkbox'],
input[type='radio'] {
  accent-color: var(--base-color-brand-red);
  flex-shrink: 0;
  width: 18px;
  height: 18px;
}
.search-controls {
  display: flex;
  gap: 10px;
}
.search-results {
  display: grid;
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
  margin-top: 12px;
}
.search-result {
  padding: 12px;
  border-radius: 16px;
  background: white;
  cursor: pointer;
}
.search-result span {
  display: grid;
  gap: 3px;
  min-width: 0;
  overflow-wrap: anywhere;
}
.search-result small {
  color: var(--base-color-text-secondary);
}
.selected-albums {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0;
  list-style: none;
  margin: 12px 0;
}
.selected-albums li {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px 4px 12px;
  border-radius: 12px;
  background: var(--base-color-surface-warm);
  max-width: 100%;
  overflow-wrap: anywhere;
  font-size: 0.8rem;
}
.selected-albums button {
  border: 0;
  background: transparent;
  padding: 5px 8px;
  font-size: 1.2rem;
  color: inherit;
}
button {
  font: inherit;
  cursor: pointer;
}
.primary,
.secondary {
  width: fit-content;
  padding: 10px 15px;
  border: 0;
  border-radius: 16px;
  font-size: 0.85rem;
  font-weight: 600;
}
.primary {
  color: white;
  background: var(--base-color-brand-red);
}
.secondary {
  background: var(--base-color-surface-warm);
  color: inherit;
}
button:disabled {
  cursor: default;
  opacity: 0.55;
}
button:focus-visible,
a:focus-visible,
input:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
.section-heading {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}
.section-heading > div {
  min-width: 0;
}
.batch-panel {
  display: grid;
  gap: 20px;
}
#batch-title {
  scroll-margin-top: 100px;
}
#batch-title:focus {
  outline: none;
}
.feedback {
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  padding: 16px 20px;
  font-size: 0.85rem;
}
.error {
  color: #9e0a0a;
  font-size: 0.85rem;
  line-height: 1.5;
}
.batch-actions {
  display: grid;
  gap: 12px;
}
.batch-actions p,
.undo-area p {
  color: var(--base-color-text-secondary);
  font-size: 0.85rem;
  line-height: 1.5;
}
.quiet-actions,
.undo-area {
  border-top: 1px solid var(--base-color-border);
  padding-top: 20px;
}
.undo-confirmation {
  display: grid;
  gap: 12px;
}
.history {
  min-width: 0;
}
.history .section-heading {
  align-items: center;
}
.history-list {
  display: grid;
  gap: 10px;
  margin-top: 18px;
  max-height: 600px;
  overflow-y: auto;
}
.history-item {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 14px;
  text-align: left;
  border-radius: 16px;
  border: 2px solid transparent;
  background: white;
  color: inherit;
}
.history-item[aria-pressed='true'] {
  border-color: var(--base-color-text-secondary);
}
.history-item span,
.history-item small {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
  overflow-wrap: anywhere;
}
.compact {
  font-size: 0.75rem;
  padding: 8px 10px;
}
@media (max-width: 960px) {
  .workspace {
    grid-template-columns: 1fr;
  }
  .admin-subheader {
    flex-direction: column;
  }
}
@media (max-width: 576px) {
  .backfill-page {
    width: calc(100vw - 20px);
    gap: 18px;
    padding-top: 18px;
  }
  .panel,
  .hero,
  .admin-subheader {
    padding: 18px;
    border-radius: 20px;
  }
  .section-heading {
    flex-direction: column;
  }
  .history .section-heading {
    flex-direction: row;
  }
  .mode-options {
    flex-direction: column;
  }
  .search-controls {
    align-items: stretch;
  }
}
</style>
