<template>
  <section class="artist-create">
    <div>
      <p class="eyebrow">MusicBrainz</p>
      <h2>Adicionar artista</h2>
      <p class="muted">Encontre a identidade correta antes de criar o artista na biblioteca.</p>
    </div>
    <form class="search" @submit.prevent="search">
      <input v-model="query" type="search" minlength="2" placeholder="Nome do artista" />
      <button type="submit" :disabled="loading || query.trim().length < 2">Buscar</button>
    </form>
    <p v-if="message" class="muted">{{ message }}</p>
    <div v-if="candidates.length" class="candidates">
      <button
        v-for="candidate in candidates"
        :key="candidate.artistMbid"
        type="button"
        class="candidate"
        :disabled="loading"
        @click="create(candidate)"
      >
        <strong>{{ candidate.name }}</strong>
        <span>{{
          [candidate.country, candidate.disambiguation].filter(Boolean).join(' · ') || 'Sem desambiguação'
        }}</span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { MusicBrainzArtistCandidate, MusicBrainzArtistCreateResult } from '~/types/music'

const config = useRuntimeConfig()
const query = ref('')
const candidates = ref<MusicBrainzArtistCandidate[]>([])
const loading = ref(false)
const message = ref('')

async function search() {
  loading.value = true
  message.value = ''
  candidates.value = []
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
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
}
.eyebrow {
  margin: 0 0 6px;
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
.muted,
.candidate span {
  color: var(--base-color-text-secondary);
}
.search {
  display: flex;
  gap: 10px;
}
input {
  min-width: 0;
  flex: 1;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  padding: 11px 14px;
  font: inherit;
}
button {
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  font: inherit;
  cursor: pointer;
}
.search button {
  background: var(--base-color-brand-red);
  color: white;
}
.candidates {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}
.candidate {
  display: grid;
  gap: 4px;
  background: white;
  text-align: left;
}
button:disabled {
  cursor: wait;
  opacity: 0.6;
}
@media (max-width: 640px) {
  .search {
    flex-direction: column;
  }
}
</style>
