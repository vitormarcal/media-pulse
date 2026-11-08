<template>
  <section
    v-if="(state?.hasRemoteImages && (state.pending || state.message)) || error"
    class="collection-tags"
    aria-label="Tags das imagens"
  >
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-else role="status">{{ state?.message || 'Atualizando as tags de coleção das imagens…' }}</p>
    <button v-if="error || state?.pending" type="button" :disabled="busy" @click="retry">
      {{ busy ? 'Solicitando…' : 'Tentar novamente' }}
    </button>
  </section>
</template>
<script setup lang="ts">
interface TagState {
  revision: number
  pending: boolean
  message: string | null
  hasRemoteImages: boolean
}
const props = defineProps<{ movieId: number }>()
const config = useRuntimeConfig()
const state = ref<TagState | null>(null)
const error = ref('')
const busy = ref(false)
let timer: ReturnType<typeof setInterval> | undefined
const endpoint = computed(() => `/api/movies/${props.movieId}/collection-tags/sync`)
async function load() {
  if (busy.value) return
  try {
    state.value = await $fetch<TagState>(endpoint.value, { baseURL: config.public.apiBase })
    error.value = ''
  } catch {
    error.value = 'Não foi possível consultar as tags das imagens.'
  }
}
async function retry() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    state.value = await $fetch<TagState>(endpoint.value, { baseURL: config.public.apiBase, method: 'POST' })
  } catch {
    error.value = 'Não foi possível solicitar a atualização das tags. Tente novamente.'
  } finally {
    busy.value = false
  }
}
onMounted(() => {
  void load()
  timer = setInterval(() => {
    if (state.value?.pending) void load()
  }, 5000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>
<style scoped>
.collection-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 20px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-secondary);
}
p {
  margin: 0;
  font-size: 0.88rem;
}
button {
  min-height: 44px;
  padding: 10px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-primary);
  font: inherit;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
button:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
</style>
