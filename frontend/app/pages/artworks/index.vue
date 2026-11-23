<template>
  <main class="art-page art-browse">
    <ArtworkEditorial
      v-if="showEditorial"
      :items="recent?.items || []"
      :pending="recentStatus === 'pending'"
      :error="Boolean(recentError)"
      :search-disabled="!library || library.busy"
      @retry="refreshRecent()"
      @search="search"
    >
      <template #actions>
        <ArtworkNav />
        <button v-if="isOwner" data-art-add class="art-button art-primary" @click="toggleAdd">Adicionar obra</button>
      </template>
    </ArtworkEditorial>
    <template v-else>
      <header class="art-header">
        <h1>Artes visuais</h1>
        <div class="art-actions">
          <button v-if="filtered" class="art-button" :disabled="library?.busy" @click="library?.clear()">
            Limpar filtros
          </button>
          <button v-if="isOwner" data-art-add class="art-button art-primary" :aria-expanded="adding" @click="toggleAdd">
            {{ adding ? 'Fechar cadastro' : 'Adicionar obra' }}
          </button>
        </div>
      </header>
      <ArtworkNav />
    </template>
    <div v-if="isOwner && openedOnce" v-show="adding" ref="capture">
      <ArtworkForm @saved="saved" @cancel="toggleAdd" />
    </div>
    <ArtworkLibrary ref="library" browse :hide-search="showEditorial" @filter-change="filterChanged" />
  </main>
</template>
<script setup lang="ts">
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import ArtworkForm from '~/components/artworks/ArtworkForm.vue'
import ArtworkLibrary from '~/components/artworks/ArtworkLibrary.vue'
import ArtworkEditorial from '~/components/artworks/ArtworkEditorial.vue'
import type { ArtworkDetails, ArtworkLibrary as ArtworkLibraryData } from '~/types/artworks'
const owner = useOwnerAuth()
const isOwner = computed(() => owner.value.authenticated)
const adding = ref(false)
const openedOnce = ref(false)
const filtered = ref(false)
const showEditorial = computed(() => !adding.value && !filtered.value)
const library = ref<InstanceType<typeof ArtworkLibrary>>()
const capture = ref<HTMLElement>()
const { request } = useArtworkApi()
const {
  data: recent,
  status: recentStatus,
  error: recentError,
  refresh: refreshRecent,
} = await useAsyncData('artworks-editorial-recent', () =>
  request<ArtworkLibraryData>('', { query: { sort: 'recent', limit: 5 } }),
)
async function toggleAdd() {
  if (!isOwner.value) return
  adding.value = !adding.value
  if (adding.value) openedOnce.value = true
  await nextTick()
  if (adding.value) {
    capture.value?.querySelector('input')?.focus({ preventScroll: true })
    capture.value?.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      block: 'start',
    })
  } else {
    document.querySelector<HTMLElement>('[data-art-add]')?.focus()
  }
}
async function filterChanged(active: boolean) {
  const clearing = filtered.value && !active
  filtered.value = active
  if (clearing && !adding.value) {
    await nextTick()
    document.querySelector<HTMLInputElement>('#art-editorial-query')?.focus()
  }
}
async function search(query: string) {
  await library.value?.search(query)
  await nextTick()
  if (filtered.value) document.querySelector<HTMLInputElement>('.art-browse .art-filters input[type="search"]')?.focus()
}
async function saved(details: ArtworkDetails) {
  await navigateTo(`/artworks/${details.artwork.id}`)
}
</script>
<style src="../../assets/css/artworks.css"></style>
<style scoped>
.art-browse {
  display: grid;
  gap: 32px;
}
.art-browse > .art-header,
.art-browse > .art-nav {
  margin: 0;
}
.art-browse > :deep(.art-section) {
  margin: 0;
  min-width: 0;
}
.art-browse :deep(.art-library-filters) {
  padding: clamp(20px, 4vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
}
.art-browse :deep(.art-library-filters .art-filters) {
  margin-bottom: 0;
}
</style>
