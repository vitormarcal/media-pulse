<template>
  <main class="mag-page">
    <p v-if="pending && !data" class="mag-state" role="status">Carregando publicações…</p>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      Não foi possível carregar as publicações. <button class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <MagazineHeader title="Publicações" back-link="/magazines" back-label="Voltar para revistas"
        ><div v-if="isOwner" class="mag-actions mag-header-actions">
          <NuxtLink class="mag-button primary" to="/magazines?add=1">Adicionar número</NuxtLink>
        </div></MagazineHeader
      >
      <MagazineCollectionNav />
      <MagazineGrid :items="cards" empty-message="Nenhuma publicação cadastrada." />
    </template>
  </main>
</template>
<script setup lang="ts">
import MagazineHeader from '~/components/magazines/MagazineHeader.vue'
import MagazineGrid from '~/components/magazines/MagazineGrid.vue'
import MagazineCollectionNav from '~/components/magazines/MagazineCollectionNav.vue'
import type { MagazinePublication } from '~/types/magazines'
const owner = useOwnerAuth()
const isOwner = computed(() => owner.value.authenticated)
const config = useRuntimeConfig()
const { data, pending, error, refresh } = await useAsyncData('magazine-publications', () =>
  $fetch<MagazinePublication[]>('/api/magazines/publications', { baseURL: config.public.apiBase }),
)
const cards = computed(() =>
  (data.value || []).map((publication) => ({
    id: String(publication.id),
    title: publication.name,
    subtitle: `${publication.numbersCount} ${publication.numbersCount === 1 ? 'número' : 'números'}`,
    href: `/magazines/publications/${publication.id}`,
    imageUrl: publication.coverUrl,
    progressLabel: '',
    activityLabel: '',
    aside: '',
  })),
)
</script>
<style scoped src="../../../assets/css/magazines.css"></style>
