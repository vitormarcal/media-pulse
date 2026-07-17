<template>
  <section class="lists-panel">
    <SectionHeading
      eyebrow="Listas de álbuns"
      :title="lists.length ? 'Recortes que incluem este álbum' : 'Este álbum ainda está fora das listas'"
      :description="
        lists.length
          ? 'Listas manuais criam caminhos próprios pela coleção, com contexto e ordem definidos por você.'
          : 'Adicione o álbum a uma lista para conectá-lo a um recorte editorial da coleção.'
      "
      :summary="lists.length ? `${lists.length} ${lists.length === 1 ? 'lista' : 'listas'}` : ''"
    />

    <div v-if="lists.length" class="lists-grid">
      <article v-for="list in lists" :key="list.listId" class="list-card">
        <NuxtLink :to="`/music/lists/${list.slug}`" class="list-link">
          <div class="cover-mosaic" :class="`covers-${Math.min(list.coverUrls.length, 3)}`">
            <template v-if="list.coverUrls.length">
              <img
                v-for="(cover, index) in list.coverUrls.slice(0, 3)"
                :key="`${list.listId}-${index}`"
                :src="resolveMediaUrl(cover) || undefined"
                :alt="`${list.name}, capa ${index + 1}`"
              />
            </template>
            <div v-else class="cover-fallback">{{ list.name.slice(0, 1) }}</div>
          </div>

          <div class="list-copy">
            <p>Lista manual</p>
            <h3>{{ list.name }}</h3>
            <span>{{ list.description || `${list.itemCount} álbuns em ordem manual.` }}</span>
          </div>

          <div class="list-meta">
            <span>{{ list.itemCount }} álbuns</span>
            <span>{{ list.listenedCount }} ouvidos</span>
          </div>
        </NuxtLink>

        <button
          v-if="editing"
          type="button"
          class="remove-button"
          :disabled="busyListId === list.listId"
          @click="removeFromList(list)"
        >
          Tirar desta lista
        </button>
      </article>
    </div>

    <div v-if="editing" class="editor-panel">
      <div>
        <p class="editor-eyebrow">Ajustes</p>
        <h3>Organizar este álbum em listas</h3>
      </div>

      <div v-if="availableLists.length" class="available-lists">
        <button
          v-for="list in availableLists"
          :key="list.listId"
          type="button"
          class="list-chip"
          :disabled="busyListId === list.listId"
          @click="addToList(list.listId)"
        >
          <strong>{{ list.name }}</strong>
          <span>{{ list.itemCount }} álbuns</span>
        </button>
      </div>
      <p v-else class="editor-note">O álbum já está em todas as listas disponíveis.</p>

      <form class="create-form" @submit.prevent="createList">
        <label>
          <span>Nova lista</span>
          <input v-model="draftName" type="text" placeholder="Favoritos, essenciais, para ouvir..." />
        </label>
        <label>
          <span>Descrição</span>
          <input v-model="draftDescription" type="text" placeholder="Opcional" />
        </label>
        <button type="submit" :disabled="creating || !draftName.trim()">
          {{ creating ? 'Criando...' : 'Criar e adicionar' }}
        </button>
      </form>
    </div>

    <p v-if="feedback" class="feedback">{{ feedback }}</p>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { AlbumListDetailsResponse, AlbumListSummaryDto } from '~/types/music'

const props = defineProps<{
  albumId: number
  lists: AlbumListSummaryDto[]
  editing: boolean
}>()

const emit = defineEmits<{ changed: [] }>()
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const allLists = ref<AlbumListSummaryDto[]>([])
const busyListId = ref<number | null>(null)
const creating = ref(false)
const draftName = ref('')
const draftDescription = ref('')
const feedback = ref<string | null>(null)

const currentListIds = computed(() => new Set(props.lists.map((list) => list.listId)))
const availableLists = computed(() => allLists.value.filter((list) => !currentListIds.value.has(list.listId)))

async function loadLists() {
  try {
    allLists.value = await $fetch<AlbumListSummaryDto[]>('/api/music/lists', { baseURL: config.public.apiBase })
  } catch {
    allLists.value = []
  }
}

async function addToList(listId: number) {
  busyListId.value = listId
  feedback.value = null
  try {
    await $fetch(`/api/music/lists/${listId}/albums/${props.albumId}`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
    feedback.value = 'Álbum adicionado à lista.'
    await loadLists()
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível adicionar o álbum à lista.'
  } finally {
    busyListId.value = null
  }
}

async function removeFromList(list: AlbumListSummaryDto) {
  busyListId.value = list.listId
  feedback.value = null
  try {
    await $fetch(`/api/music/lists/${list.listId}/albums/${props.albumId}`, {
      baseURL: config.public.apiBase,
      method: 'DELETE',
    })
    feedback.value = `Álbum removido de "${list.name}".`
    await loadLists()
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível remover o álbum da lista.'
  } finally {
    busyListId.value = null
  }
}

async function createList() {
  if (!draftName.value.trim()) return
  creating.value = true
  feedback.value = null
  try {
    const created = await $fetch<AlbumListDetailsResponse>('/api/music/lists', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { name: draftName.value.trim(), description: draftDescription.value.trim() || null },
    })
    await addToList(created.listId)
    draftName.value = ''
    draftDescription.value = ''
  } catch {
    feedback.value = 'Não foi possível criar a lista.'
  } finally {
    creating.value = false
  }
}

watch(
  () => props.editing,
  (editing) => {
    if (editing) void loadLists()
  },
  { immediate: true },
)
</script>

<style scoped>
.lists-panel,
.editor-panel,
.create-form,
.create-form label {
  display: grid;
  gap: 24px;
}

.lists-grid {
  columns: 3;
  column-gap: 20px;
}

.list-card,
.editor-panel {
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  border-radius: 32px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 243, 238, 0.98));
}

.list-card {
  overflow: hidden;
  break-inside: avoid;
  margin-bottom: 20px;
}

.list-link {
  display: grid;
  gap: 16px;
  color: inherit;
}

.cover-mosaic {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  min-height: 14rem;
  padding: 10px;
  background: var(--base-color-surface-soft);
}

.cover-mosaic img,
.cover-fallback {
  width: 100%;
  height: 100%;
  min-height: 0;
  object-fit: cover;
  border: 6px solid #fff;
  border-radius: 20px;
}

.covers-1 {
  grid-template-columns: 1fr;
}

.covers-3 img:first-child {
  grid-row: span 2;
}

.cover-fallback {
  display: grid;
  place-items: center;
  font-size: 4rem;
  color: var(--base-color-text-secondary);
}

.list-copy,
.list-meta {
  padding: 0 20px;
}

.list-copy p,
.editor-eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.list-copy h3,
.editor-panel h3 {
  margin: 5px 0 8px;
}

.list-copy span,
.list-meta,
.editor-note,
.feedback {
  color: var(--base-color-text-secondary);
}

.list-meta {
  display: flex;
  justify-content: space-between;
  padding-bottom: 20px;
  font-size: 0.8rem;
}

.remove-button {
  width: calc(100% - 40px);
  margin: 0 20px 20px;
}

.editor-panel {
  padding: 24px;
}

.available-lists {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.list-chip {
  display: grid;
  gap: 3px;
  justify-items: start;
}

.list-chip span {
  font-size: 0.75rem;
  color: var(--base-color-text-secondary);
}

.create-form {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  align-items: end;
  gap: 12px;
}

.create-form label {
  gap: 7px;
}

.create-form input {
  width: 100%;
  padding: 12px 15px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: #fff;
}

.create-form button,
.list-chip,
.remove-button {
  padding: 10px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  cursor: pointer;
}

.feedback {
  margin: 0;
}

@media (max-width: 980px) {
  .lists-grid {
    columns: 2;
  }

  .create-form {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .lists-grid {
    columns: 1;
  }
}
</style>
