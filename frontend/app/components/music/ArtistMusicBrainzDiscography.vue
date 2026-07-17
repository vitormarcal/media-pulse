<template>
  <section v-if="linked" class="discography">
    <div class="heading">
      <div>
        <p class="eyebrow">Discografia</p>
        <h2>Buscar outros discos deste artista</h2>
        <p class="muted">Revise release groups antes de criar discos locais. Capas e faixas não serão importadas.</p>
      </div>
      <button type="button" class="secondary" :disabled="loading" @click="load">Buscar discografia</button>
    </div>

    <p v-if="message" class="muted">{{ message }}</p>
    <template v-if="preview">
      <div class="filters">
        <label><input v-model="types" type="checkbox" value="Album" /> Álbuns</label>
        <label><input v-model="types" type="checkbox" value="EP" /> EPs</label>
        <label><input v-model="types" type="checkbox" value="Single" /> Singles</label>
      </div>
      <div class="items">
        <label
          v-for="item in visibleItems"
          :key="item.releaseGroupMbid"
          class="item"
          :class="item.status.toLowerCase()"
        >
          <input v-if="item.status === 'MISSING'" v-model="selected" type="checkbox" :value="item.releaseGroupMbid" />
          <span v-else class="status">{{ statusLabel(item.status) }}</span>
          <span>
            <strong>{{ item.title }}</strong>
            <small>{{
              [item.firstReleaseYear, item.primaryType, item.disambiguation].filter(Boolean).join(' · ')
            }}</small>
          </span>
        </label>
      </div>
      <div class="actions">
        <span>{{ selected.length }} selecionado(s)</span>
        <button type="button" class="primary" :disabled="loading || !selected.length" @click="importSelected">
          Confirmar importação
        </button>
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import type {
  MusicBrainzDiscographyImportResult,
  MusicBrainzDiscographyPreview,
  MusicBrainzDiscographyStatus,
} from '~/types/music'

const props = defineProps<{ artistId: number; linked: boolean }>()
const emit = defineEmits<{ imported: [] }>()
const config = useRuntimeConfig()
const preview = ref<MusicBrainzDiscographyPreview | null>(null)
const selected = ref<string[]>([])
const types = ref(['Album', 'EP'])
const loading = ref(false)
const message = ref('')
const visibleItems = computed(
  () => preview.value?.items.filter((item) => types.value.includes(item.primaryType || '')) ?? [],
)

function statusLabel(status: MusicBrainzDiscographyStatus) {
  return status === 'LINKED' ? 'Já vinculado' : 'Possível correspondência'
}

async function load() {
  loading.value = true
  message.value = ''
  selected.value = []
  try {
    preview.value = await $fetch(`/api/music/artists/${props.artistId}/musicbrainz/discography`, {
      baseURL: config.public.apiBase,
    })
    if (!preview.value.items.length) message.value = 'Nenhum release group encontrado.'
  } catch {
    message.value = 'Não foi possível carregar a discografia.'
  } finally {
    loading.value = false
  }
}

async function importSelected() {
  loading.value = true
  message.value = ''
  try {
    const result = await $fetch<MusicBrainzDiscographyImportResult>(
      `/api/music/artists/${props.artistId}/musicbrainz/discography/import`,
      { baseURL: config.public.apiBase, method: 'POST', body: { releaseGroupMbids: selected.value } },
    )
    await load()
    message.value = `${result.createdAlbumIds.length} disco(s) adicionado(s).`
    emit('imported')
  } catch {
    message.value = 'Não foi possível importar os discos selecionados.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.discography {
  display: grid;
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
}
.heading,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
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
small {
  color: var(--base-color-text-secondary);
}
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}
.items {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 10px;
}
.item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px;
  border-radius: 16px;
  background: white;
}
.item > span:last-child {
  display: grid;
  gap: 4px;
}
.status {
  padding: 3px 7px;
  border-radius: 8px;
  background: var(--base-color-surface-warm);
  font-size: 0.72rem;
  white-space: nowrap;
}
button {
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  font: inherit;
  cursor: pointer;
}
.secondary {
  background: var(--base-color-surface-warm);
}
.primary {
  background: var(--base-color-brand-red);
  color: white;
}
button:disabled {
  cursor: wait;
  opacity: 0.6;
}
@media (max-width: 640px) {
  .heading,
  .actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
