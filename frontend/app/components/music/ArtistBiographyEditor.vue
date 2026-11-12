<template>
  <section class="biography-editor" aria-labelledby="artist-biography-heading">
    <div class="heading">
      <h3 id="artist-biography-heading">Biografia</h3>
      <button type="button" :disabled="loading || !linked" @click="load">
        {{ loading ? 'Consultando...' : 'Consultar biografia' }}
      </button>
    </div>
    <p v-if="!linked">Vincule o artista ao MusicBrainz para consultar a biografia.</p>
    <p v-if="message" role="status">{{ message }}</p>
    <div v-if="preview" class="preview">
      <h4>Prévia da biografia</h4>
      <WikipediaIntroduction v-if="preview.proposed" :introduction="preview.proposed.introduction" />
      <p v-else>Nenhuma introdução disponível pelos vínculos deste artista. O texto salvo foi preservado.</p>
      <details v-if="preview.current">
        <summary>Comparar com a biografia salva</summary>
        <p v-if="preview.proposed && preview.current.artistMbid !== preview.proposed.artistMbid">
          Este texto pertence ao vínculo anterior.
        </p>
        <WikipediaIntroduction :introduction="preview.current.introduction" />
      </details>
      <div class="actions">
        <button
          v-if="preview.proposed && preview.token"
          class="primary"
          type="button"
          :disabled="loading"
          @click="apply"
        >
          Salvar biografia</button
        ><button type="button" :disabled="loading" @click="preview = null">Cancelar</button>
      </div>
    </div>
  </section>
</template>
<script setup lang="ts">
import WikipediaIntroduction from '~/components/music/WikipediaIntroduction.vue'
import type { ArtistBiographyPreview } from '~/types/music'
const props = defineProps<{ artistId: number; linked: boolean }>()
const emit = defineEmits<{ applied: [] }>()
const config = useRuntimeConfig()
const preview = ref<ArtistBiographyPreview | null>(null)
const loading = ref(false)
const message = ref('')
async function load() {
  loading.value = true
  message.value = ''
  preview.value = null
  try {
    preview.value = await $fetch(`/api/music/artists/${props.artistId}/biography/preview`, {
      method: 'POST',
      baseURL: config.public.apiBase,
    })
  } catch {
    message.value = 'Não foi possível consultar a biografia. O texto salvo foi preservado. Tente novamente.'
  } finally {
    loading.value = false
  }
}
async function apply() {
  if (!preview.value?.token) return
  loading.value = true
  message.value = ''
  try {
    await $fetch(`/api/music/artists/${props.artistId}/biography`, {
      method: 'POST',
      baseURL: config.public.apiBase,
      body: { token: preview.value.token },
    })
    preview.value = null
    message.value = 'Biografia salva.'
    emit('applied')
  } catch {
    message.value = 'Não foi possível salvar. Se o artista mudou ou a prévia expirou, consulte novamente.'
  } finally {
    loading.value = false
  }
}
</script>
<style scoped>
.biography-editor,
.preview {
  display: grid;
  gap: 16px;
}
.biography-editor {
  padding-top: 24px;
  border-top: 1px solid var(--base-color-border);
}
.heading,
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.actions {
  justify-content: flex-start;
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
p {
  font-size: 0.85rem;
  line-height: 1.6;
  color: var(--base-color-text-secondary);
}
button {
  padding: 10px 14px;
  border: 0;
  border-radius: 16px;
  min-height: 44px;
  font: inherit;
  font-size: 0.85rem;
  color: var(--base-color-text-primary);
  background: var(--base-color-surface-soft);
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
summary {
  cursor: pointer;
  padding: 10px 0;
  font-size: 0.85rem;
}
button:focus-visible,
summary:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
</style>
