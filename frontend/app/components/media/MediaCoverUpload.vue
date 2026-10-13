<template>
  <details class="cover-upload" @toggle="handleToggle">
    <summary>Alterar capa</summary>
    <form @submit.prevent="upload">
      <label>
        <span>Escolher capa</span>
        <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/webp" :disabled="busy" @change="choose" />
      </label>
      <p class="hint">JPEG, PNG ou WebP · até 10 MB</p>
      <MediaImage v-if="preview" :src="preview" alt="Prévia da nova capa" class="preview" />
      <button type="submit" :disabled="!file || busy">{{ busy ? 'Enviando…' : 'Salvar capa' }}</button>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <p v-if="message" role="status">{{ message }}</p>
    </form>
  </details>
</template>
<script setup lang="ts">
const props = defineProps<{ mediaType: 'books' | 'games'; entityId: number }>()
const emit = defineEmits<{ saved: [coverUrl: string] }>()
const config = useRuntimeConfig()
const fileInput = ref<HTMLInputElement | null>(null)
const file = ref<File | null>(null)
const preview = ref('')
const busy = ref(false)
const error = ref('')
const message = ref('')
function clearPreview() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
}
onBeforeUnmount(clearPreview)
function handleToggle(event: Event) {
  if ((event.target as HTMLDetailsElement).open) fileInput.value?.focus()
}
function choose(event: Event) {
  clearPreview()
  file.value = (event.target as HTMLInputElement).files?.[0] || null
  error.value = ''
  message.value = ''
  if (file.value && file.value.size > 10 * 1024 * 1024) {
    error.value = 'Escolha uma imagem de até 10 MB.'
    file.value = null
    return
  }
  if (file.value) preview.value = URL.createObjectURL(file.value)
}
async function upload() {
  if (!file.value || busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    const body = new FormData()
    body.append('cover', file.value)
    const result = await $fetch<{ coverUrl: string }>(`/api/${props.mediaType}/${props.entityId}/cover`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body,
    })
    emit('saved', result.coverUrl)
    file.value = null
    if (fileInput.value) fileInput.value.value = ''
    clearPreview()
    message.value = 'Capa salva.'
  } catch (cause) {
    const response = cause as { data?: { message?: string; detail?: string } }
    error.value = response.data?.detail || response.data?.message || 'Não foi possível salvar a capa. Tente novamente.'
  } finally {
    busy.value = false
  }
}
</script>
<style scoped>
.cover-upload {
  color: var(--base-color-text-primary);
}
summary {
  cursor: pointer;
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
}
form {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}
label {
  display: grid;
  gap: 8px;
}
p {
  margin: 0;
}
.hint {
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
.preview {
  max-width: 200px;
  max-height: 280px;
  object-fit: contain;
  border-radius: 16px;
}
button {
  width: fit-content;
  padding: 10px 16px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-brand-red);
  color: var(--base-color-text-primary);
  font: inherit;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
.error {
  color: var(--base-color-error, #9e0a0a);
}
summary:focus-visible,
input:focus-visible,
button:focus-visible {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 4px;
}
</style>
