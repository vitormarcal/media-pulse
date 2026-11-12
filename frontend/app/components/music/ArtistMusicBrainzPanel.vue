<template>
  <section v-owner class="mb-panel">
    <div class="heading">
      <h3>Dados do artista</h3>
      <button class="secondary" type="button" :disabled="loading" @click="search">
        {{ link ? 'Revisar vínculo' : 'Buscar artista' }}
      </button>
    </div>
    <p v-if="link" class="muted">
      <a :href="`https://musicbrainz.org/artist/${link.mbid}`" target="_blank" rel="noopener noreferrer"
        >Ver artista no MusicBrainz ↗</a
      >
    </p>
    <form class="manual-form" @submit.prevent="previewManual">
      <label for="artist-reference">Link ou identificador do MusicBrainz</label>
      <div class="manual-controls">
        <input
          id="artist-reference"
          v-model="reference"
          :disabled="loading"
          placeholder="https://musicbrainz.org/artist/..."
        /><button type="submit" :disabled="loading || !reference.trim()">Consultar</button>
      </div>
    </form>
    <p v-if="message" class="muted" role="status">{{ message }}</p>
    <div v-if="candidates.length" class="candidates">
      <button
        v-for="candidate in candidates"
        :key="candidate.artistMbid"
        type="button"
        class="candidate"
        :disabled="loading"
        @click="selected = candidate"
      >
        <strong>{{ candidate.name }}</strong
        ><span>{{ [candidate.country, candidate.disambiguation].filter(Boolean).join(' · ') }}</span>
      </button>
    </div>
    <div v-if="selected" class="preview">
      <h4>Confirmar correspondência</h4>
      <strong>{{ selected.name }}</strong>
      <p>{{ [selected.country, selected.disambiguation].filter(Boolean).join(' · ') }}</p>
      <a :href="`https://musicbrainz.org/artist/${selected.artistMbid}`" target="_blank" rel="noopener noreferrer"
        >Ver no MusicBrainz ↗</a
      >
      <p>O vínculo atualizará identidade, gêneros e links. A biografia é consultada separadamente.</p>
      <div class="actions">
        <button class="primary" type="button" :disabled="loading" @click="apply">Confirmar vínculo</button
        ><button type="button" :disabled="loading" @click="selected = null">Cancelar</button>
      </div>
    </div>
    <button v-if="link" type="button" :disabled="loading" @click="refreshProfile">Atualizar perfil</button>
    <p v-if="profile?.syncedAt" class="muted">
      Perfil atualizado em {{ new Date(profile.syncedAt).toLocaleString('pt-BR') }}
    </p>
    <p v-if="profile?.syncError" class="error-copy" role="status">{{ profile.syncError }}</p>
    <p v-if="profile?.image?.syncError" class="muted">{{ profile.image.syncError }}</p>
    <ArtistBiographyEditor
      :key="`${artistId}-${link?.mbid}`"
      :artist-id="artistId"
      :linked="!!link"
      @applied="$emit('applied')"
    />
  </section>
</template>
<script setup lang="ts">
import ArtistBiographyEditor from '~/components/music/ArtistBiographyEditor.vue'
import { parseMusicBrainzArtistReference } from '~/utils/musicBrainzReference'
import type { ArtistMusicBrainzProfile, MusicBrainzArtistCandidate, MusicBrainzLink } from '~/types/music'
const props = defineProps<{
  artistId: number
  link: MusicBrainzLink | null
  profile: ArtistMusicBrainzProfile | null
}>()
const emit = defineEmits<{ applied: [] }>()
const config = useRuntimeConfig()
const candidates = ref<MusicBrainzArtistCandidate[]>([])
const loading = ref(false)
const message = ref('')
const reference = ref('')
const selected = ref<MusicBrainzArtistCandidate | null>(null)
watch(reference, () => {
  selected.value = null
  message.value = ''
})
async function previewManual() {
  const mbid = parseMusicBrainzArtistReference(reference.value)
  selected.value = null
  if (!mbid) {
    message.value = 'Informe um link de artista do MusicBrainz ou um identificador válido.'
    return
  }
  loading.value = true
  message.value = ''
  try {
    selected.value = await $fetch(`/api/music/artists/${props.artistId}/musicbrainz/preview`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      query: { artistMbid: mbid },
    })
  } catch (error) {
    message.value =
      (error as { statusCode?: number }).statusCode === 404
        ? 'Artista não encontrado no MusicBrainz.'
        : 'Não foi possível consultar o artista. Confira o valor ou tente novamente.'
  } finally {
    loading.value = false
  }
}
async function search() {
  loading.value = true
  message.value = ''
  selected.value = null
  try {
    candidates.value = await $fetch(`/api/music/artists/${props.artistId}/musicbrainz/candidates`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
  } catch {
    message.value = 'Não foi possível consultar o MusicBrainz.'
  } finally {
    loading.value = false
  }
}
async function apply() {
  if (!selected.value) return
  loading.value = true
  message.value = ''
  try {
    await $fetch(`/api/music/artists/${props.artistId}/musicbrainz`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { artistMbid: selected.value.artistMbid },
    })
    candidates.value = []
    selected.value = null
    message.value = 'Artista vinculado com sucesso.'
    emit('applied')
  } catch {
    message.value = 'Não foi possível salvar o vínculo.'
  } finally {
    loading.value = false
  }
}
async function refreshProfile() {
  loading.value = true
  message.value = ''
  try {
    await $fetch(`/api/music/artists/${props.artistId}/musicbrainz/refresh`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
    message.value = 'Dados atualizados com sucesso.'
    emit('applied')
  } catch {
    message.value = 'Não foi possível atualizar os dados.'
    emit('applied')
  } finally {
    loading.value = false
  }
}
</script>
<style scoped>
.mb-panel,
.manual-form,
.preview {
  display: grid;
  gap: 16px;
}
.mb-panel {
  padding: 20px;
  border: 1px solid var(--base-color-border);
  border-radius: 20px;
  background: var(--base-color-surface-strong);
}
.heading,
.manual-controls,
.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.heading {
  justify-content: space-between;
}
h3,
h4,
p {
  margin: 0;
}
h3,
h4 {
  font-size: 1rem;
}
.muted,
.candidate span,
.preview p {
  color: var(--base-color-text-secondary);
  font-size: 0.85rem;
  line-height: 1.6;
}
.error-copy {
  color: #9e0a0a;
  font-size: 0.85rem;
}
label {
  font-size: 0.85rem;
}
.manual-controls input {
  flex: 1;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
  font: inherit;
}
button {
  width: fit-content;
  min-height: 44px;
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
.primary {
  background: var(--base-color-brand-red);
  color: white;
}
.candidates {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr));
  gap: 12px;
}
.candidate {
  display: grid;
  gap: 6px;
  text-align: left;
  width: 100%;
}
a {
  color: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
  font-size: 0.85rem;
}
.preview {
  padding-top: 20px;
  border-top: 1px solid var(--base-color-border);
}
button:focus-visible,
input:focus-visible,
a:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
@media (max-width: 600px) {
  .manual-controls {
    flex-direction: column;
    align-items: stretch;
  }
  .manual-controls button {
    width: 100%;
  }
}
</style>
