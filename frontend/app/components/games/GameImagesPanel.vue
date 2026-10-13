<template>
  <section class="images-panel">
    <SectionHeading eyebrow="Catálogo" title="Buscar imagens" />
    <article class="action-card">
      <div class="copy">
        <p class="copy-title">Imagens do jogo</p>
        <p class="copy-body">Busque novas imagens para a galeria. A capa atual será mantida.</p>
      </div>
      <div class="actions">
        <button type="button" class="primary-button" :disabled="busy" @click="importImages">
          {{ busy ? 'Buscando…' : 'Buscar imagens' }}
        </button>
      </div>
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <p v-if="message" class="feedback" role="status">{{ message }}</p>
    </article>
  </section>
</template>
<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
const props = defineProps<{ gameId: number }>()
const emit = defineEmits<{ imported: [] }>()
const config = useRuntimeConfig()
const busy = ref(false)
const error = ref('')
const message = ref('')
async function importImages() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const result = await $fetch<{ savedImagesCount: number; coverAssigned: boolean; failedImagesCount: number }>(
      `/api/games/${props.gameId}/images/import`,
      { baseURL: config.public.apiBase, method: 'POST' },
    )
    message.value = result.savedImagesCount
      ? `${result.savedImagesCount} imagem(ns) adicionada(s).`
      : 'Nenhuma imagem nova encontrada.'
    if (result.failedImagesCount) error.value = 'Algumas imagens não puderam ser trazidas. Tente novamente.'
    emit('imported')
  } catch (cause) {
    const data = (cause as { data?: { detail?: string; message?: string } }).data
    error.value = data?.detail || data?.message || 'Não foi possível trazer as imagens. Tente novamente.'
  } finally {
    busy.value = false
  }
}
</script>
<style scoped>
.images-panel {
  display: grid;
  gap: 18px;
}
.action-card {
  display: grid;
  gap: 16px;
  padding: 24px;
  border-radius: 28px;
  background: var(--base-color-surface-wash);
  border: 1px solid var(--base-color-border);
}
.copy {
  display: grid;
  gap: 6px;
}
p {
  margin: 0;
}
.copy-title {
  font-weight: 700;
  color: var(--base-color-text-primary);
}
.copy-body {
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
.actions {
  display: flex;
  justify-content: flex-end;
}
.primary-button {
  padding: 6px 14px;
  border: 2px solid transparent;
  border-radius: var(--comp-radius-button);
  background: var(--base-color-brand-red);
  color: #000;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 700;
  cursor: pointer;
}
.primary-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.primary-button:focus-visible {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 4px;
}
.feedback {
  padding: 10px 12px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-secondary);
}
.error {
  color: #9e0a0a;
}
@media (max-width: 760px) {
  .primary-button {
    width: 100%;
  }
}
</style>
