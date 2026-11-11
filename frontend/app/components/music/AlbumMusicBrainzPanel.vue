<template>
  <section v-owner class="mb-panel">
    <div class="panel-heading">
      <div>
        <p class="eyebrow">MusicBrainz</p>
        <h2>{{ link ? 'Dados vinculados' : 'Enriquecer dados' }}</h2>
        <p>{{ link ? `Release group ${link.mbid}` : 'Encontre o disco correto e revise tudo antes de salvar.' }}</p>
      </div>
      <button v-owner class="secondary" type="button" :disabled="loading" @click="search">
        {{ link ? 'Revisar vínculo' : 'Buscar correspondência' }}
      </button>
    </div>

    <form class="manual-link" @submit.prevent="previewManual">
      <label for="album-musicbrainz-reference">Link ou identificador do MusicBrainz</label>
      <div class="manual-controls">
        <input
          id="album-musicbrainz-reference"
          v-model="manualReference"
          type="text"
          :disabled="loading"
          placeholder="https://musicbrainz.org/release-group/..."
          aria-describedby="album-musicbrainz-help"
        />
        <button class="secondary" type="submit" :disabled="loading || !manualReference.trim()">Consultar</button>
      </div>
      <p id="album-musicbrainz-help">Cole o link do álbum ou de uma edição, ou o identificador do álbum.</p>
    </form>

    <AlbumContextEditor
      :key="`${albumId}-${link?.mbid}`"
      :album-id="albumId"
      :linked="!!link"
      @applied="$emit('applied')"
    />

    <p v-if="message" class="message" role="status">{{ message }}</p>

    <div v-if="candidates.length" class="candidates">
      <button
        v-for="candidate in candidates"
        :key="candidate.releaseGroupMbid"
        v-owner
        type="button"
        class="candidate"
        :disabled="loading"
        @click="previewCandidate(candidate)"
      >
        <strong>{{ candidate.title }}</strong>
        <span>{{ creditLabel(candidate) }} · {{ candidate.firstReleaseYear || 'ano desconhecido' }}</span>
        <small>{{ [candidate.primaryType, candidate.disambiguation].filter(Boolean).join(' · ') }}</small>
      </button>
    </div>

    <div v-if="preview" class="preview">
      <h3>Prévia</h3>
      <p><strong>Álbum:</strong> {{ preview.candidate.title }}</p>
      <a
        :href="`https://musicbrainz.org/release-group/${preview.candidate.releaseGroupMbid}`"
        target="_blank"
        rel="noopener noreferrer"
        >Ver no MusicBrainz ↗</a
      >
      <p><strong>Artistas:</strong> {{ creditLabel(preview.candidate) }}</p>
      <ul>
        <li v-for="change in preview.changes" :key="change">{{ change }}</li>
      </ul>
      <p><strong>Preservados:</strong> título, capa e tracklist.</p>
      <p v-if="preview.candidate.artists?.length">Os créditos do álbum serão substituídos pelos artistas acima.</p>
      <button v-owner class="primary" type="button" :disabled="loading" @click="apply">Confirmar enriquecimento</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { parseMusicBrainzAlbumReference } from '~/utils/musicBrainzReference'
import type { MusicBrainzAlbumReference } from '~/utils/musicBrainzReference'
import AlbumContextEditor from '~/components/music/AlbumContextEditor.vue'
import type { MusicBrainzAlbumCandidate, MusicBrainzAlbumPreview, MusicBrainzLink } from '~/types/music'

const props = defineProps<{ albumId: number; link: MusicBrainzLink | null }>()
const emit = defineEmits<{ applied: [] }>()
const config = useRuntimeConfig()
const candidates = ref<MusicBrainzAlbumCandidate[]>([])
const preview = ref<MusicBrainzAlbumPreview | null>(null)
const loading = ref(false)
const message = ref('')
const manualReference = ref('')
watch(manualReference, () => {
  preview.value = null
  message.value = ''
})

function creditLabel(candidate: MusicBrainzAlbumCandidate) {
  return candidate.artists?.length
    ? candidate.artists.map((artist) => artist.creditedName + artist.joinPhrase).join('')
    : candidate.artistName
}

async function search() {
  loading.value = true
  message.value = ''
  preview.value = null
  try {
    candidates.value = await $fetch(`/api/music/albums/${props.albumId}/musicbrainz/candidates`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
    if (!candidates.value.length) message.value = 'Nenhuma correspondência encontrada.'
  } catch {
    message.value = 'Não foi possível consultar o MusicBrainz agora.'
  } finally {
    loading.value = false
  }
}

async function previewCandidate(candidate: MusicBrainzAlbumCandidate) {
  await loadPreview({ mbid: candidate.releaseGroupMbid, entityType: 'release-group' })
}

async function previewManual() {
  const reference = parseMusicBrainzAlbumReference(manualReference.value)
  preview.value = null
  if (!reference) {
    message.value = 'Informe um link de álbum ou edição do MusicBrainz, ou um identificador de álbum válido.'
    return
  }
  await loadPreview(reference)
}

async function loadPreview(reference: MusicBrainzAlbumReference) {
  loading.value = true
  message.value = ''
  preview.value = null
  try {
    preview.value = await $fetch(`/api/music/albums/${props.albumId}/musicbrainz/preview`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      query: { releaseGroupMbid: reference.mbid, entityType: reference.entityType },
    })
  } catch (error) {
    const status = (error as { statusCode?: number }).statusCode
    message.value =
      status === 404
        ? 'Correspondência não encontrada no MusicBrainz. Confira o link ou identificador.'
        : status === 409
          ? 'Esta correspondência não pode ser vinculada. Confira o álbum e os dados informados.'
          : 'Não foi possível montar a prévia. Confira o valor informado ou tente novamente.'
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

.manual-link {
  display: grid;
  gap: 10px;
}
.manual-link label {
  font-size: 0.9rem;
  font-weight: 600;
}
.manual-controls {
  display: flex;
  gap: 12px;
}
.manual-controls input {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  padding: 12px 14px;
  font: inherit;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
}
.manual-link p {
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
  line-height: 1.5;
}
input:focus-visible,
button:focus-visible,
a:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
.preview a {
  color: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
  font-size: 0.85rem;
}
@media (max-width: 600px) {
  .manual-controls {
    flex-direction: column;
  }
}
</style>
