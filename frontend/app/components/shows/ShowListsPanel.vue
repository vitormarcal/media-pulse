<template>
  <section class="lists-panel">
    <SectionHeading
      eyebrow="Listas manuais"
      :title="lists.length ? 'Listas com esta série' : 'Listas'"
      :summary="summary"
    />
    <div v-if="lists.length" class="list-grid">
      <article v-for="list in lists" :key="list.listId" class="list-card">
        <NuxtLink :to="`/shows/lists/${list.slug}`"
          ><strong>{{ list.name }}</strong
          ><small>{{ list.itemCount }} séries</small>
          <p>{{ list.description || 'Curadoria pessoal em ordem manual.' }}</p></NuxtLink
        >
        <button v-if="editing" type="button" :disabled="busy === list.listId" @click="remove(list)">
          Remover desta lista
        </button>
      </article>
    </div>
    <p v-else-if="!editing" class="quiet">Nenhuma lista vinculada.</p>

    <div v-if="editing" class="editor">
      <div v-if="available.length" class="chips">
        <button
          v-for="list in available"
          :key="list.listId"
          type="button"
          :disabled="busy === list.listId"
          @click="attach(list.listId)"
        >
          + {{ list.name }} <small>{{ list.itemCount }}</small>
        </button>
      </div>
      <p v-else class="quiet">Esta série já está em todas as listas disponíveis.</p>
      <form @submit.prevent="createAndAttach">
        <label><span>Nova lista</span><input v-model="name" placeholder="Favoritas, Para rever…" required /></label>
        <label><span>Descrição</span><input v-model="description" placeholder="Opcional" /></label>
        <button class="primary" type="submit" :disabled="creating || !name.trim()">
          {{ creating ? 'Criando…' : 'Criar e adicionar' }}
        </button>
      </form>
    </div>
    <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
  </section>
</template>
<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ShowListAttachRequest, ShowListSummaryDto } from '~/types/shows'
const props = defineProps<{ showId: number; lists: ShowListSummaryDto[]; editing: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const config = useRuntimeConfig()
const all = ref<ShowListSummaryDto[]>([])
const busy = ref<number | null>(null)
const creating = ref(false)
const name = ref('')
const description = ref('')
const feedback = ref<string | null>(null)
const summary = computed(() =>
  props.lists.length ? `${props.lists.length} ${props.lists.length === 1 ? 'lista' : 'listas'}` : '',
)
const current = computed(() => new Set(props.lists.map((list) => list.listId)))
const available = computed(() => all.value.filter((list) => !current.value.has(list.listId)))
watch(
  () => props.editing,
  async (editing) => {
    if (!editing) return
    feedback.value = null
    try {
      all.value = await $fetch('/api/shows/lists', { baseURL: config.public.apiBase })
    } catch (error) {
      feedback.value = resolveError(error)
    }
  },
  { immediate: true },
)
async function attach(listId: number) {
  busy.value = listId
  feedback.value = null
  try {
    await $fetch(`/api/shows/${props.showId}/lists`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { listId } satisfies ShowListAttachRequest,
    })
    feedback.value = 'Série adicionada.'
    emit('changed')
  } catch (error) {
    feedback.value = resolveError(error)
  } finally {
    busy.value = null
  }
}
async function remove(list: ShowListSummaryDto) {
  busy.value = list.listId
  feedback.value = null
  try {
    await $fetch(`/api/shows/${props.showId}/lists/${list.listId}`, {
      baseURL: config.public.apiBase,
      method: 'DELETE',
    })
    feedback.value = `Removida de ${list.name}.`
    emit('changed')
  } catch (error) {
    feedback.value = resolveError(error)
  } finally {
    busy.value = null
  }
}
async function createAndAttach() {
  creating.value = true
  feedback.value = null
  try {
    await $fetch(`/api/shows/${props.showId}/lists`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { name: name.value, description: description.value || null } satisfies ShowListAttachRequest,
    })
    name.value = ''
    description.value = ''
    feedback.value = 'Lista criada.'
    emit('changed')
    all.value = await $fetch('/api/shows/lists', { baseURL: config.public.apiBase })
  } catch (error) {
    feedback.value = resolveError(error)
  } finally {
    creating.value = false
  }
}
function resolveError(error: unknown) {
  if (error && typeof error === 'object' && 'data' in error) {
    const data = (error as { data?: { detail?: string; message?: string } }).data
    return data?.detail || data?.message || 'Não foi possível atualizar as listas.'
  }
  return error instanceof Error ? error.message : 'Não foi possível atualizar as listas.'
}
</script>
<style scoped>
.lists-panel {
  display: grid;
  gap: 20px;
}
.list-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.list-card {
  display: grid;
  gap: 10px;
  padding: 18px;
  border: 1px solid var(--base-color-border);
  border-radius: 20px;
  background: var(--base-color-surface-strong);
}
.list-card a {
  display: grid;
  gap: 5px;
}
.list-card p,
.quiet,
.feedback {
  margin: 0;
  color: var(--base-color-text-secondary);
}
.list-card small {
  font-size: 0.75rem;
  color: var(--base-color-text-secondary);
}
button {
  padding: 7px 14px;
  border: 0;
  border-radius: var(--comp-radius-button);
  background: var(--base-color-surface-warm);
  cursor: pointer;
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.editor {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.editor form {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 10px;
  align-items: end;
}
.editor label {
  display: grid;
  gap: 5px;
  font-size: 0.75rem;
}
.editor input {
  padding: 10px 12px;
  border: 1px solid var(--base-color-text-muted);
  border-radius: 16px;
  background: white;
}
.editor input:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--base-color-focus) 66%, white);
  outline-offset: 3px;
}
.primary {
  background: var(--base-color-brand-red);
  color: #000;
  font-weight: 700;
}
@media (max-width: 800px) {
  .list-grid,
  .editor form {
    grid-template-columns: 1fr;
  }
}
</style>
