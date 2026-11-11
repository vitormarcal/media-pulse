<template>
  <section class="context-editor" aria-labelledby="context-editor-heading">
    <div class="heading">
      <div>
        <h3 id="context-editor-heading">Contexto do álbum</h3>
        <p>Introdução, tipo, primeiro lançamento e links.</p>
      </div>
      <button type="button" :disabled="loading || !linked" @click="load">
        {{ loading ? 'Consultando...' : 'Consultar contexto' }}
      </button>
    </div>
    <p v-if="!linked">Vincule o álbum ao MusicBrainz para consultar seu contexto.</p>
    <p v-if="message" role="status">{{ message }}</p>
    <div v-if="preview" class="preview">
      <h4>Prévia do contexto</h4>
      <p>{{ albumContextMeta(preview.proposed).join(' · ') || 'Sem tipo ou data de lançamento disponíveis.' }}</p>
      <AlbumIntroduction v-if="preview.proposed.introduction" :introduction="preview.proposed.introduction" />
      <p v-else>Nenhuma introdução disponível pelos vínculos deste álbum.</p>
      <nav aria-label="Links propostos">
        <a
          v-for="(url, label) in preview.proposed.links"
          :key="label"
          :href="url"
          target="_blank"
          rel="noopener noreferrer"
          >{{ label }} ↗</a
        >
      </nav>
      <details v-if="preview.current">
        <summary>Comparar com o conteúdo salvo</summary>
        <p>{{ albumContextMeta(preview.current).join(' · ') }}</p>
        <AlbumIntroduction v-if="preview.current.introduction" :introduction="preview.current.introduction" />
        <p v-else>Sem introdução salva.</p>
        <nav aria-label="Links salvos">
          <a
            v-for="(url, label) in preview.current.links"
            :key="label"
            :href="url"
            target="_blank"
            rel="noopener noreferrer"
            >{{ label }} ↗</a
          >
        </nav>
      </details>
      <p>Salvar substitui o contexto pelo conteúdo acima. Capa, faixas, créditos e marcações serão preservados.</p>
      <div class="actions">
        <button class="primary" type="button" :disabled="loading" @click="apply">Salvar contexto</button
        ><button type="button" :disabled="loading" @click="preview = null">Cancelar</button>
      </div>
    </div>
  </section>
</template>
<script setup lang="ts">
import AlbumIntroduction from '~/components/music/AlbumIntroduction.vue'
import { albumContextMeta } from '~/utils/albumContext'
import type { AlbumContextPreview } from '~/types/music'
const props = defineProps<{ albumId: number; linked: boolean }>()
const emit = defineEmits<{ applied: [] }>()
const config = useRuntimeConfig()
const preview = ref<AlbumContextPreview | null>(null)
const loading = ref(false)
const message = ref('')
watch(
  () => [props.albumId, props.linked],
  () => {
    preview.value = null
    message.value = ''
  },
)
async function load() {
  loading.value = true
  message.value = ''
  preview.value = null
  try {
    preview.value = await $fetch<AlbumContextPreview>(`/api/music/albums/${props.albumId}/context/preview`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
  } catch {
    message.value = 'Não foi possível consultar o contexto. O conteúdo salvo foi preservado. Tente novamente.'
  } finally {
    loading.value = false
  }
}
async function apply() {
  if (!preview.value) return
  loading.value = true
  message.value = ''
  try {
    await $fetch(`/api/music/albums/${props.albumId}/context`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { token: preview.value.token },
    })
    preview.value = null
    message.value = 'Contexto salvo.'
    emit('applied')
  } catch {
    message.value = 'Não foi possível salvar. Se a prévia expirou ou o álbum mudou, consulte novamente.'
  } finally {
    loading.value = false
  }
}
</script>
<style scoped>
.context-editor {
  display: grid;
  gap: 16px;
  padding-top: 24px;
  border-top: 1px solid var(--base-color-border);
}
.heading {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
h3,
h4,
p {
  margin: 0;
}
h4 {
  font-size: 1rem;
}
p {
  color: var(--base-color-text-secondary);
  font-size: 0.85rem;
  line-height: 1.6;
}
.heading p {
  margin-top: 8px;
}
.preview {
  display: grid;
  gap: 16px;
}
nav,
.actions {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
a {
  color: inherit;
  font-size: 0.85rem;
  text-decoration: underline;
  text-underline-offset: 4px;
}
button {
  border: 0;
  border-radius: 16px;
  min-height: 44px;
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
button.primary {
  background: var(--base-color-brand-red);
  color: white;
}
summary {
  padding: 10px 0;
  cursor: pointer;
  font-size: 0.85rem;
}
button:focus-visible,
summary:focus-visible,
a:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
</style>
