<template>
  <div v-if="loadError || (sync?.hasRemoteCovers && (sync.pending || sync.message))" class="series-tag-status">
    <p v-if="loadError" role="alert">Não foi possível consultar as tags das capas.</p>
    <p v-else-if="sync?.pending" role="status">
      {{ sync.message || 'Atualização das tags de séries nas capas pendente.' }}
    </p>
    <p v-else-if="sync?.message" role="status">{{ sync.message }}</p>
    <button v-if="loadError || sync?.pending" v-owner type="button" :disabled="busy" @click="retry">
      {{ busy ? 'Agendando...' : 'Tentar atualizar tags das capas' }}
    </button>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </div>
</template>
<script setup lang="ts">
import { bookGroupError, useBookGroupsApi } from '~/composables/useBookGroupsApi'
interface SeriesTagSync {
  revision: number
  pending: boolean
  message: string | null
  hasRemoteCovers: boolean
}
const props = defineProps<{ bookId: number }>()
const api = useBookGroupsApi()
defineExpose({ refresh })
const {
  data: sync,
  error: loadError,
  refresh: reload,
} = await useAsyncData(`book-series-tags-${props.bookId}`, () =>
  api<SeriesTagSync>(`/${props.bookId}/series-tags/sync`),
)
const busy = ref(false)
const error = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
let disposed = false
function schedule() {
  if (timer) clearTimeout(timer)
  if (!disposed && sync.value?.pending)
    timer = setTimeout(
      () => {
        void refresh()
      },
      sync.value.message ? 30000 : 3000,
    )
}
async function refresh() {
  await reload()
  schedule()
}
async function retry() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    sync.value = await api<SeriesTagSync>(`/${props.bookId}/series-tags/sync`, 'POST')
    await refresh()
  } catch (cause) {
    error.value = bookGroupError(cause)
  } finally {
    busy.value = false
  }
}
onMounted(schedule)
onBeforeUnmount(() => {
  disposed = true
  if (timer) clearTimeout(timer)
})
</script>
<style scoped>
.series-tag-status {
  display: grid;
  gap: 12px;
  padding: 16px 20px;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
p {
  margin: 0;
  line-height: 1.5;
}
button {
  width: fit-content;
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
button:focus-visible {
  outline: 2px solid #435ee5;
  outline-offset: 3px;
}
.error {
  color: #9e0a0a;
}
</style>
