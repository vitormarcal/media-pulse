<template>
  <MediaImageGallery
    ref="viewer"
    :images="images"
    :title="title"
    viewer-only
    :busy="imageBusy"
    @image-change="handleGalleryImageChanged"
    @closed="handleGalleryClosed"
  >
    <template v-if="owner.authenticated" #actions="{ image, index }">
      <div class="image-actions" :aria-label="`Ações da imagem ${index + 1}`">
        <span v-if="image === coverUrl" class="cover-label">Capa atual</span>
        <button v-else type="button" :disabled="imageBusy" @click="manageImage(image, false)">Definir como capa</button>
        <button type="button" :disabled="imageBusy" @click="confirmRemoval = image">Remover imagem</button>
        <div v-if="confirmRemoval === image" class="remove-confirmation">
          <p>
            Remover esta imagem {{ mediaType === 'movies' ? 'do filme' : 'da série' }}?{{
              image === coverUrl ? ' Outra imagem será usada como capa, se disponível.' : ''
            }}
          </p>
          <button type="button" :disabled="imageBusy" @click="manageImage(image, true)">Confirmar remoção</button>
          <button type="button" :disabled="imageBusy" @click="confirmRemoval = ''">Cancelar</button>
        </div>
        <p v-if="imageBusy" class="image-feedback" role="status">Salvando alteração…</p>
        <p v-if="imageError" class="image-feedback image-error" role="alert">{{ imageError }}</p>
        <p v-if="imageMessage" class="image-feedback" role="status">{{ imageMessage }}</p>
      </div>
    </template>
  </MediaImageGallery>
  <p v-if="imageMessage && !images.length" role="status">{{ imageMessage }}</p>
</template>

<script setup lang="ts">
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'

const props = defineProps<{
  mediaType: 'movies' | 'shows'
  entityId: number
  title: string
  refresh: () => Promise<unknown>
}>()
const images = defineModel<string[]>('images', { required: true })
const coverUrl = defineModel<string | null>('coverUrl', { required: true })
const emit = defineEmits<{ closed: [needsFocusFallback: boolean] }>()
const owner = useOwnerAuth()
const config = useRuntimeConfig()
const viewer = ref<InstanceType<typeof MediaImageGallery> | null>(null)
const imageBusy = ref(false)
const imageError = ref('')
const imageMessage = ref('')
const confirmRemoval = ref('')

defineExpose({ open: (index: number) => viewer.value?.open(index) })

function handleGalleryImageChanged() {
  confirmRemoval.value = ''
  imageError.value = ''
}

async function handleGalleryClosed(needsFocusFallback: boolean) {
  confirmRemoval.value = ''
  imageError.value = ''
  await nextTick()
  emit('closed', needsFocusFallback)
}

async function manageImage(url: string, remove: boolean) {
  if (!owner.value.authenticated || imageBusy.value) return
  imageBusy.value = true
  imageError.value = ''
  imageMessage.value = ''
  try {
    const result = await $fetch<{ coverUrl: string | null }>(
      `/api/${props.mediaType}/${props.entityId}/images${remove ? '' : '/primary'}`,
      { baseURL: config.public.apiBase, method: remove ? 'DELETE' : 'POST', body: { url } },
    )
    coverUrl.value = result.coverUrl
    const remaining = images.value.filter((image) => !remove || image !== url)
    images.value = result.coverUrl
      ? [result.coverUrl, ...remaining.filter((image) => image !== result.coverUrl)]
      : remaining
    confirmRemoval.value = ''
    imageMessage.value = remove ? 'Imagem removida da galeria.' : 'Capa atualizada.'
    await props.refresh()
  } catch {
    imageError.value = 'Não foi possível concluir a alteração. Tente novamente.'
  } finally {
    imageBusy.value = false
  }
}
</script>

<style scoped>
.image-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  justify-content: center;
  color: #e5e5e0;
  padding: 8px 0;
}
.image-actions button {
  min-height: 44px;
  padding: 8px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.75rem;
  cursor: pointer;
}
.image-actions button:disabled {
  opacity: 0.5;
  cursor: default;
}
.image-actions button:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
.cover-label {
  font-size: 0.75rem;
  font-weight: 700;
  color: #e5e5e0;
}
.image-feedback {
  flex-basis: 100%;
  margin: 4px 0;
  text-align: center;
  font-size: 0.88rem;
}
.remove-confirmation {
  width: 100%;
  text-align: center;
}
.remove-confirmation button + button {
  margin-left: 8px;
}
.remove-confirmation p {
  font-size: 0.88rem;
}
.image-error {
  color: #ffb4b4;
}
</style>
