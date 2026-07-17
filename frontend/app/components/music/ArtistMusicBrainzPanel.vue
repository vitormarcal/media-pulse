<template>
  <section class="mb-panel">
    <div>
      <p class="eyebrow">MusicBrainz</p>
      <h2>{{ link ? 'Artista vinculado' : 'Vincular artista' }}</h2>
    </div>
    <p v-if="link" class="muted">Artist MBID: {{ link.mbid }}</p>
    <button class="secondary" type="button" :disabled="loading" @click="search">
      {{ link ? 'Revisar vínculo' : 'Buscar artista' }}
    </button>
    <p v-if="message" class="muted">{{ message }}</p>
    <div v-if="candidates.length" class="candidates">
      <button
        v-for="candidate in candidates"
        :key="candidate.artistMbid"
        class="candidate"
        type="button"
        @click="apply(candidate)"
      >
        <strong>{{ candidate.name }}</strong
        ><span>{{ [candidate.country, candidate.disambiguation].filter(Boolean).join(' · ') }}</span>
      </button>
    </div>
  </section>
</template>
<script setup lang="ts">
import type { MusicBrainzArtistCandidate, MusicBrainzLink } from '~/types/music'
const props = defineProps<{ artistId: number; link: MusicBrainzLink | null }>()
const emit = defineEmits<{ applied: [] }>()
const config = useRuntimeConfig()
const candidates = ref<MusicBrainzArtistCandidate[]>([])
const loading = ref(false)
const message = ref('')
async function search() {
  loading.value = true
  message.value = ''
  try {
    candidates.value = await $fetch(`/api/music/artists/${props.artistId}/musicbrainz/candidates`, {
      baseURL: config.public.apiBase,
    })
  } catch {
    message.value = 'Não foi possível consultar o MusicBrainz.'
  } finally {
    loading.value = false
  }
}
async function apply(candidate: MusicBrainzArtistCandidate) {
  loading.value = true
  try {
    await $fetch(`/api/music/artists/${props.artistId}/musicbrainz`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { artistMbid: candidate.artistMbid },
    })
    candidates.value = []
    message.value = 'Artista vinculado com sucesso.'
    emit('applied')
  } catch {
    message.value = 'Não foi possível salvar o vínculo.'
  } finally {
    loading.value = false
  }
}
</script>
<style scoped>
.mb-panel {
  display: grid;
  gap: 12px;
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
button {
  width: fit-content;
  border: 0;
  border-radius: 16px;
  padding: 9px 14px;
  font: inherit;
  cursor: pointer;
}
.secondary {
  background: var(--base-color-surface-warm);
}
.candidates {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.candidate {
  display: grid;
  gap: 4px;
  background: white;
  text-align: left;
}
button:disabled {
  opacity: 0.6;
}
</style>
