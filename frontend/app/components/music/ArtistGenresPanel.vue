<template>
  <section class="genres-panel">
    <div class="heading">
      <div>
        <p class="eyebrow">Curadoria</p>
        <h2>Gêneros do artista</h2>
      </div>
      <button type="button" class="secondary" @click="editing = !editing">
        {{ editing ? 'Fechar' : 'Editar gêneros' }}
      </button>
    </div>
    <div v-if="genres.length" class="chips">
      <article v-for="genre in genres" :key="genre.id" class="chip" :class="{ hidden: !genre.active }">
        <span>{{ genre.name }}</span
        ><small>{{ genre.source === 'MUSICBRAINZ' ? 'MusicBrainz' : 'Manual' }}</small>
        <button v-if="editing" type="button" @click="restoreOrHide(genre)">
          {{ genre.active ? 'Ocultar aqui' : 'Restaurar' }}
        </button>
        <button v-if="editing && genre.active" type="button" @click="setGlobalVisibility(genre.id)">
          Ocultar geral
        </button>
      </article>
    </div>
    <p v-else class="muted">Nenhum gênero associado ao artista.</p>
    <form v-if="editing" class="add-form" @submit.prevent="addGenre">
      <input v-model="draft" placeholder="Ex.: Alternative rock" />
      <button type="submit" class="primary" :disabled="busy || !draft.trim()">Adicionar</button>
    </form>
    <p v-if="feedback" class="muted">{{ feedback }}</p>
  </section>
</template>

<script setup lang="ts">
import type { AlbumTermDto } from '~/types/music'
const props = defineProps<{ artistId: number; genres: AlbumTermDto[] }>()
const emit = defineEmits<{ changed: [] }>()
const config = useRuntimeConfig()
const editing = ref(false)
const draft = ref('')
const busy = ref(false)
const feedback = ref('')
async function addGenre() {
  busy.value = true
  try {
    await $fetch(`/api/music/artists/${props.artistId}/genres`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { name: draft.value, kind: 'GENRE' },
    })
    draft.value = ''
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível adicionar o gênero.'
  } finally {
    busy.value = false
  }
}
async function setVisibility(termId: number, hidden: boolean) {
  await $fetch(`/api/music/artists/${props.artistId}/genres/${termId}/visibility`, {
    baseURL: config.public.apiBase,
    method: 'POST',
    body: { hidden },
  })
  emit('changed')
}
async function restoreOrHide(genre: AlbumTermDto) {
  if (genre.active) return setVisibility(genre.id, true)
  if (genre.hiddenGlobally) {
    await $fetch(`/api/music/terms/${genre.id}/visibility`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { hidden: false },
    })
    emit('changed')
    return
  }
  return setVisibility(genre.id, false)
}
async function setGlobalVisibility(termId: number) {
  await $fetch(`/api/music/terms/${termId}/visibility`, {
    baseURL: config.public.apiBase,
    method: 'POST',
    body: { hidden: true },
  })
  emit('changed')
}
</script>

<style scoped>
.genres-panel {
  display: grid;
  gap: 18px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}
.heading {
  display: flex;
  align-items: end;
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
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.78);
}
.chip.hidden {
  opacity: 0.58;
}
.chip small,
.muted {
  color: var(--base-color-text-secondary);
}
.chip button {
  border: 0;
  background: transparent;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
}
.secondary,
.primary {
  border: 0;
  border-radius: 16px;
  padding: 9px 14px;
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
.add-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
input {
  min-width: min(22rem, 100%);
  padding: 11px 15px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  font: inherit;
}
</style>
