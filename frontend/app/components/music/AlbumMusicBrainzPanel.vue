<template>
  <section class="mb-panel">
    <div class="panel-heading">
      <div>
        <p class="eyebrow">MusicBrainz</p>
        <h2>{{ link ? 'Dados vinculados' : 'Enriquecer dados' }}</h2>
        <p>{{ link ? `Release group ${link.mbid}` : 'Encontre o disco correto e revise tudo antes de salvar.' }}</p>
      </div>
      <button class="secondary" type="button" :disabled="loading" @click="search">
        {{ link ? 'Revisar vínculo' : 'Buscar correspondência' }}
      </button>
    </div>

    <p v-if="message" class="message">{{ message }}</p>

    <div v-if="candidates.length" class="candidates">
      <button
        v-for="candidate in candidates"
        :key="candidate.releaseGroupMbid"
        type="button"
        class="candidate"
        @click="previewCandidate(candidate)"
      >
        <strong>{{ candidate.title }}</strong>
        <span>{{ candidate.artistName }} · {{ candidate.firstReleaseYear || 'ano desconhecido' }}</span>
        <small>{{ [candidate.primaryType, candidate.disambiguation].filter(Boolean).join(' · ') }}</small>
      </button>
    </div>

    <div v-if="preview" class="preview">
      <h3>Prévia</h3>
      <ul>
        <li v-for="change in preview.changes" :key="change">{{ change }}</li>
      </ul>
      <p><strong>Preservados:</strong> título, artista, capa e tracklist.</p>
      <button class="primary" type="button" :disabled="loading" @click="apply">Confirmar enriquecimento</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { MusicBrainzAlbumCandidate, MusicBrainzAlbumPreview, MusicBrainzLink } from '~/types/music'

const props = defineProps<{ albumId: number; link: MusicBrainzLink | null }>()
const emit = defineEmits<{ applied: [] }>()
const config = useRuntimeConfig()
const candidates = ref<MusicBrainzAlbumCandidate[]>([])
const preview = ref<MusicBrainzAlbumPreview | null>(null)
const loading = ref(false)
const message = ref('')

async function search() {
  loading.value = true
  message.value = ''
  preview.value = null
  try {
    candidates.value = await $fetch(`/api/music/albums/${props.albumId}/musicbrainz/candidates`, {
      baseURL: config.public.apiBase,
    })
    if (!candidates.value.length) message.value = 'Nenhuma correspondência encontrada.'
  } catch {
    message.value = 'Não foi possível consultar o MusicBrainz agora.'
  } finally {
    loading.value = false
  }
}

async function previewCandidate(candidate: MusicBrainzAlbumCandidate) {
  loading.value = true
  message.value = ''
  try {
    preview.value = await $fetch(`/api/music/albums/${props.albumId}/musicbrainz/preview`, {
      baseURL: config.public.apiBase,
      query: { releaseGroupMbid: candidate.releaseGroupMbid },
    })
  } catch {
    message.value = 'Não foi possível montar a prévia.'
  } finally {
    loading.value = false
  }
}

async function apply() {
  if (!preview.value) return
  loading.value = true
  try {
    await $fetch(`/api/music/albums/${props.albumId}/musicbrainz`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { releaseGroupMbid: preview.value.candidate.releaseGroupMbid },
    })
    candidates.value = []
    preview.value = null
    message.value = 'Dados enriquecidos com sucesso.'
    emit('applied')
  } catch {
    message.value = 'Não foi possível aplicar o enriquecimento.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.mb-panel {
  display: grid;
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
}
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
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
h3,
p {
  margin: 0;
}
.panel-heading p,
.candidate span,
.candidate small,
.preview p,
.message {
  color: var(--base-color-text-secondary);
}
.candidates {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}
.candidate {
  display: grid;
  gap: 5px;
  padding: 16px;
  border: 0;
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
  text-align: left;
  cursor: pointer;
}
.candidate:hover {
  background: var(--base-color-surface-warm);
}
.preview {
  display: grid;
  gap: 12px;
  padding: 18px;
  border-radius: 20px;
  background: white;
}
.preview ul {
  margin: 0;
  padding-left: 20px;
}
button {
  font: inherit;
  cursor: pointer;
}
.secondary,
.primary {
  border: 0;
  border-radius: 16px;
  padding: 9px 14px;
}
.secondary {
  background: var(--base-color-surface-warm);
}
.primary {
  width: fit-content;
  background: var(--base-color-brand-red);
  color: white;
}
button:disabled {
  cursor: wait;
  opacity: 0.6;
}
@media (max-width: 640px) {
  .panel-heading {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
