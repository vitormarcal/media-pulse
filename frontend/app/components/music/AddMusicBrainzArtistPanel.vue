<template>
  <section class="artist-create" :class="{ expanded }">
    <div class="summary">
      <div class="summary-copy">
        <p class="eyebrow">Catálogo</p>
        <div>
          <h2>Amplie sua biblioteca</h2>
          <p class="muted">Adicione um artista e continue pela discografia que você quiser guardar.</p>
        </div>
      </div>
      <button type="button" class="secondary-action" @click="toggle">
        {{ expanded ? 'Fechar' : 'Adicionar artista' }}
      </button>
    </div>

    <div v-if="expanded" class="workspace">
      <form class="search" @submit.prevent="search">
        <label for="new-music-artist">Buscar no catálogo do MusicBrainz</label>
        <div class="search-row">
          <input id="new-music-artist" v-model="query" type="search" minlength="2" placeholder="Nome do artista" />
          <button type="submit" class="search-action" :disabled="loading || query.trim().length < 2">Buscar</button>
        </div>
      </form>

      <p v-if="message" class="muted">{{ message }}</p>

      <div v-if="candidates.length" class="results">
        <p class="group-label">Escolha a identidade correta</p>
        <div class="candidates">
          <button
            v-for="candidate in candidates"
            :key="candidate.artistMbid"
            type="button"
            class="candidate"
            :class="{ selected: selected?.artistMbid === candidate.artistMbid }"
            :disabled="loading"
            @click="selected = candidate"
          >
            <span class="monogram">{{ candidate.name.slice(0, 1) }}</span>
            <span class="candidate-copy">
              <strong>{{ candidate.name }}</strong>
              <small>{{
                [candidate.country, candidate.disambiguation].filter(Boolean).join(' · ') || 'Sem desambiguação'
              }}</small>
            </span>
          </button>
        </div>
      </div>

      <div v-if="selected" class="confirmation">
        <div>
          <p class="group-label">Pronto para adicionar</p>
          <strong>{{ selected.name }}</strong>
          <p class="muted">O artista será criado já vinculado à identidade escolhida.</p>
        </div>
        <button type="button" class="primary-action" :disabled="loading" @click="create(selected)">
          {{ loading ? 'Adicionando...' : 'Adicionar à biblioteca' }}
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { MusicBrainzArtistCandidate, MusicBrainzArtistCreateResult } from '~/types/music'

const config = useRuntimeConfig()
const query = ref('')
const candidates = ref<MusicBrainzArtistCandidate[]>([])
const selected = ref<MusicBrainzArtistCandidate | null>(null)
const expanded = ref(false)
const loading = ref(false)
const message = ref('')

function toggle() {
  expanded.value = !expanded.value
  if (!expanded.value) {
    candidates.value = []
    selected.value = null
    message.value = ''
  }
}

async function search() {
  loading.value = true
  message.value = ''
  candidates.value = []
  selected.value = null
  try {
    candidates.value = await $fetch('/api/music/musicbrainz/artists/candidates', {
      baseURL: config.public.apiBase,
      query: { query: query.value.trim() },
    })
    if (!candidates.value.length) message.value = 'Nenhum artista encontrado.'
  } catch {
    message.value = 'Não foi possível consultar o MusicBrainz agora.'
  } finally {
    loading.value = false
  }
}

async function create(candidate: MusicBrainzArtistCandidate) {
  loading.value = true
  message.value = ''
  try {
    const result = await $fetch<MusicBrainzArtistCreateResult>('/api/music/musicbrainz/artists', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { artistMbid: candidate.artistMbid },
    })
    await navigateTo(`/music/artists/${result.artistId}`)
  } catch {
    message.value = 'Não foi possível adicionar este artista.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.artist-create {
  display: grid;
  gap: 18px;
  padding: 18px 20px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 48%, white);
  border-radius: 32px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 243, 238, 0.98));
}
.artist-create.expanded {
  padding: clamp(20px, 3vw, 28px);
}
.summary,
.summary-copy,
.confirmation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}
.summary-copy {
  justify-content: flex-start;
}
.eyebrow {
  margin: 0;
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 82%, white);
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}
h2,
p {
  margin: 0;
}
h2 {
  font-size: clamp(1.15rem, 2vw, 1.45rem);
  line-height: 1.05;
  letter-spacing: -0.035em;
}
.muted,
.candidate small {
  color: var(--base-color-text-secondary);
}
.muted {
  line-height: 1.45;
}
.workspace,
.results,
.search {
  display: grid;
  gap: 10px;
}
.workspace {
  gap: 18px;
  padding-top: 18px;
  border-top: 1px solid color-mix(in srgb, var(--base-color-border) 44%, white);
}
.search label,
.group-label {
  margin: 0;
  color: var(--base-color-text-muted);
  font-size: 0.76rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}
input {
  width: 100%;
  border: 1px solid var(--base-color-text-muted);
  border-radius: 16px;
  padding: 12px 16px;
  background: #fff;
  color: var(--base-color-text-primary);
  font: inherit;
}
button {
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  font: inherit;
  cursor: pointer;
}
.secondary-action {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}
.search-action,
.primary-action {
  background: var(--base-color-brand-red);
  color: #000;
}
.candidates {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 10px;
}
.candidate {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 72px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 74%, white);
  color: var(--base-color-text-primary);
  text-align: left;
}
.candidate:hover,
.candidate.selected {
  background: color-mix(in srgb, var(--base-color-brand-red) 12%, white);
}
.candidate.selected {
  outline: 2px solid color-mix(in srgb, var(--base-color-brand-red) 64%, white);
  outline-offset: 1px;
}
.monogram {
  display: grid;
  flex: 0 0 42px;
  width: 42px;
  aspect-ratio: 1;
  place-items: center;
  border-radius: 50%;
  background: var(--base-color-surface-warm);
  font-weight: 700;
}
.candidate-copy {
  display: grid;
  gap: 3px;
}
.candidate small {
  line-height: 1.35;
}
.confirmation {
  padding: 16px 18px;
  border-radius: 20px;
  background: #fff;
}
.confirmation > div {
  display: grid;
  gap: 4px;
}
button:disabled {
  cursor: wait;
  opacity: 0.6;
}
@media (max-width: 640px) {
  .summary,
  .summary-copy,
  .confirmation {
    align-items: stretch;
    flex-direction: column;
  }
  .search-row {
    grid-template-columns: 1fr;
  }
  .secondary-action,
  .primary-action {
    width: 100%;
  }
}
</style>
