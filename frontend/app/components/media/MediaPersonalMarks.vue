<template>
  <div class="personal-marks">
    <div class="mark-actions" role="group" aria-label="Marcações pessoais" :aria-busy="pending">
      <button
        v-for="mark in marks"
        :key="mark.key"
        type="button"
        :aria-pressed="mark.active"
        :disabled="pending"
        @click="toggle(mark.key, mark.active)"
      >
        <span aria-hidden="true">{{ mark.active ? '✓' : '+' }}</span>
        {{ mark.label }}
      </button>
    </div>
    <p v-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  mediaType: 'movies' | 'shows'
  entityId: number
}>()
const emit = defineEmits<{ saved: [] }>()
const favorite = defineModel<boolean>('favorite', { required: true })
const abandoned = defineModel<boolean>('abandoned', { required: true })
const config = useRuntimeConfig()
const pending = ref(false)
const errorMessage = ref('')
const marks = computed(() => [
  { key: 'favorite' as const, label: 'Favorito', active: favorite.value },
  { key: 'abandoned' as const, label: 'Abandonado', active: abandoned.value },
])

async function toggle(mark: 'favorite' | 'abandoned', active: boolean) {
  if (pending.value) return
  pending.value = true
  errorMessage.value = ''
  try {
    await $fetch(`/api/${props.mediaType}/${props.entityId}/${mark}`, {
      baseURL: config.public.apiBase,
      method: active ? 'DELETE' : 'POST',
    })
    if (mark === 'favorite') favorite.value = !active
    else abandoned.value = !active
    emit('saved')
  } catch {
    errorMessage.value = 'Não foi possível salvar a marcação. Tente novamente.'
  } finally {
    pending.value = false
  }
}
</script>

<style scoped>
.personal-marks {
  display: grid;
  gap: 8px;
}

.mark-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border: 1px solid transparent;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}

button[aria-pressed='true'] {
  border-color: var(--base-color-text-primary);
  font-weight: 600;
}

button:focus-visible {
  outline: 2px solid #435ee5;
  outline-offset: 3px;
}

button:disabled {
  opacity: 0.6;
  cursor: wait;
}

.error-message {
  margin: 0;
  color: #9e0a0a;
  font-size: 0.85rem;
}
</style>
