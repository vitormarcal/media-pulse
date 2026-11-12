<template>
  <section class="genres-panel">
    <div class="heading">
      <h3>Gêneros</h3>
      <button v-owner class="secondary" type="button" :aria-expanded="editing" @click="editing = !editing">
        {{ editing ? 'Concluir edição' : 'Editar gêneros' }}
      </button>
    </div>
    <div v-if="shownGenres.length" class="chips">
      <component
        :is="editing ? 'article' : NuxtLink"
        v-for="genre in shownGenres"
        :key="genre.id"
        :to="editing ? undefined : `/music/terms/genre/${genre.slug}`"
        class="chip"
        :class="{ hidden: !genre.active }"
      >
        <span>{{ genre.name }}</span
        ><small v-if="editing">{{ genre.source === 'MUSICBRAINZ' ? 'MusicBrainz' : 'Manual' }}</small>
        <button v-if="editing" v-owner type="button" :disabled="busy" @click="restoreOrHide(genre)">
          {{ genre.active ? 'Ocultar aqui' : genre.hiddenGlobally ? 'Restaurar globalmente' : 'Restaurar' }}
        </button>
        <button
          v-if="editing && genre.active"
          v-owner
          type="button"
          :disabled="busy"
          @click="setGlobalVisibility(genre.id)"
        >
          Ocultar geral
        </button>
      </component>
    </div>
    <p v-else class="muted">Nenhum gênero adicionado.</p>
    <form v-if="editing" v-owner class="add-form" @submit.prevent="addGenre">
      <label>Adicionar gênero<input v-model="draft" placeholder="Ex.: Rock alternativo" :disabled="busy" /></label
      ><button type="submit" class="primary" :disabled="busy || !draft.trim()">Adicionar</button>
    </form>
    <p v-if="feedback" class="muted" role="status">{{ feedback }}</p>
  </section>
</template>
<script setup lang="ts">
import { NuxtLink } from '#components'
import type { AlbumTermDto } from '~/types/music'
const props = defineProps<{ artistId: number; genres: AlbumTermDto[] }>()
const emit = defineEmits<{ changed: [] }>()
const config = useRuntimeConfig()
const owner = useOwnerAuth()
const shownGenres = computed(() =>
  props.genres.filter((genre) => genre.active || (owner.value.authenticated && editing.value)),
)
const editing = ref(false)
watch(
  () => owner.value.authenticated,
  (authenticated) => {
    if (!authenticated) editing.value = false
  },
)
const draft = ref('')
const busy = ref(false)
const feedback = ref('')
async function addGenre() {
  busy.value = true
  feedback.value = ''
  try {
    await $fetch(`/api/music/artists/${props.artistId}/genres`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { name: draft.value, kind: 'GENRE' },
    })
    draft.value = ''
    feedback.value = 'Gênero adicionado.'
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível adicionar o gênero.'
  } finally {
    busy.value = false
  }
}
async function performVisibility(termId: number, hidden: boolean) {
  await $fetch(`/api/music/artists/${props.artistId}/genres/${termId}/visibility`, {
    baseURL: config.public.apiBase,
    method: 'POST',
    body: { hidden },
  })
  emit('changed')
}
async function performRestoreOrHide(genre: AlbumTermDto) {
  if (genre.active) return performVisibility(genre.id, true)
  if (genre.hiddenGlobally) {
    await $fetch(`/api/music/terms/${genre.id}/visibility`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { hidden: false },
    })
    if (genre.hiddenForAlbum) await performVisibility(genre.id, false)
    else emit('changed')
    return
  }
  return performVisibility(genre.id, false)
}
async function performGlobalVisibility(termId: number) {
  await $fetch(`/api/music/terms/${termId}/visibility`, {
    baseURL: config.public.apiBase,
    method: 'POST',
    body: { hidden: true },
  })
  emit('changed')
}
async function updateVisibility(action: () => Promise<unknown>) {
  if (busy.value) return
  busy.value = true
  feedback.value = ''
  try {
    await action()
    feedback.value = 'Gêneros atualizados.'
  } catch {
    feedback.value = 'Não foi possível atualizar o gênero. Tente novamente.'
  } finally {
    busy.value = false
  }
}
function restoreOrHide(genre: AlbumTermDto) {
  return updateVisibility(() => performRestoreOrHide(genre))
}
function setGlobalVisibility(termId: number) {
  return updateVisibility(() => performGlobalVisibility(termId))
}
</script>

<style scoped>
.genres-panel {
  display: grid;
  gap: 16px;
  padding-top: 24px;
  border-top: 1px solid var(--base-color-border);
}
.heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
h3,
p {
  margin: 0;
}
h3 {
  font-size: 1rem;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.chip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 16px;
  background: var(--base-color-surface-soft);
  font-size: 0.85rem;
  color: inherit;
}
.chip.hidden {
  opacity: 0.65;
}
.chip small,
.muted {
  color: var(--base-color-text-secondary);
  font-size: 0.85rem;
}
button {
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  min-height: 44px;
  font: inherit;
  font-size: 0.82rem;
  cursor: pointer;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-primary);
}
.primary {
  background: var(--base-color-brand-red);
  color: white;
}
.add-form {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 12px;
}
label {
  display: grid;
  gap: 8px;
  font-size: 0.85rem;
}
input {
  min-width: 0;
  width: 100%;
  padding: 11px 15px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  font: inherit;
  color: var(--base-color-text-primary);
  background: var(--base-color-surface-strong);
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
button:focus-visible,
a:focus-visible,
input:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
</style>
