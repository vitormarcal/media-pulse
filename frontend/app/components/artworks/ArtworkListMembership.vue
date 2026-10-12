<template>
  <section class="art-panel">
    <h2>Listas</h2>
    <div v-for="list in selected" :key="list.id" class="art-actions art-section">
      <NuxtLink :to="`/artworks/lists/${list.id}`">{{ list.name }}</NuxtLink>
      <button class="art-button" :disabled="busy" @click="remove(list.id)">Remover da lista</button>
    </div>
    <form @submit.prevent="add">
      <fieldset :disabled="busy">
        <div class="art-fields">
          <label
            >Lista existente
            <select v-model="listId" aria-label="Lista existente">
              <option :value="null">Escolha uma lista</option>
              <option v-for="list in available" :key="list.id" :value="list.id">{{ list.name }}</option>
            </select></label
          >
          <label>Ou criar lista <input v-model="name" maxlength="200" /></label>
        </div>
        <button class="art-button" :disabled="!listId && !name.trim()">Adicionar à lista</button>
      </fieldset>
    </form>
    <p v-if="error" class="art-error" role="alert">{{ error }}</p>
    <p v-if="success" class="art-success" role="status">{{ success }}</p>
  </section>
</template>
<script setup lang="ts">
import type { ArtworkList } from '~/types/artworks'
const props = defineProps<{ artworkId: number; selected: ArtworkList[] }>()
const emit = defineEmits<{ changed: [] }>()
const { request, run, busy, error, success } = useArtworkApi()
const { data: lists, refresh } = await useAsyncData('artwork-membership-lists', () => request<ArtworkList[]>('/lists'))
const available = computed(() =>
  (lists.value || []).filter((list) => !props.selected.some((selected) => selected.id === list.id)),
)
const listId = ref<number | null>(null)
const name = ref('')
async function add() {
  await run(async () => {
    let id = listId.value
    if (name.value.trim()) {
      const created = await request<ArtworkList>('/lists', { method: 'POST', body: { name: name.value } })
      id = created.id
      listId.value = id
      name.value = ''
      await refresh()
    }
    await request(`/${props.artworkId}/lists/${id}`, { method: 'PUT' })
    listId.value = null
    emit('changed')
  })
}
async function remove(id: number) {
  await run(async () => {
    await request(`/${props.artworkId}/lists/${id}`, { method: 'DELETE' })
    emit('changed')
  })
}
</script>
