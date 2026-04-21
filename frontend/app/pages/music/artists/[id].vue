<template>
  <main class="artist-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página do artista...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este artista.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <ArtistPageHero :title="data.title" :cover-url="data.coverUrl" :hero-meta="data.heroMeta" />

      <ArtistContextPanel :stats="data.stats" :recent-days="data.recentDays" />

      <MusicLibraryGrid
        eyebrow="Álbuns"
        title="Os discos que realmente carregaram esse nome"
        description="A discografia que apareceu de fato no seu arquivo, com peso de repetição e cobertura."
        summary="Álbum continua sendo o eixo principal de leitura mesmo dentro da página de artista."
        :items="data.albums"
        empty-message="Nenhum álbum apareceu para esse artista."
      />

      <ArtistTrackList :tracks="data.topTracks" />
    </template>
  </main>
</template>

<script setup lang="ts">
import ArtistContextPanel from '~/components/music/ArtistContextPanel.vue'
import ArtistPageHero from '~/components/music/ArtistPageHero.vue'
import ArtistTrackList from '~/components/music/ArtistTrackList.vue'
import MusicLibraryGrid from '~/components/music/MusicLibraryGrid.vue'
import { useArtistPageData } from '~/composables/useArtistPageData'

const route = useRoute()
const id = computed(() => String(route.params.id))

const { data, error, status } = await useArtistPageData(id.value)

useHead(() => ({
  title: data.value ? `${data.value.title} · Media Pulse` : 'Artista · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `${data.value.title} dentro do seu arquivo de música.`
        : 'Página interna de artista no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.artist-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.state-card {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
}

.state-card.error {
  color: #7a1414;
}

pre {
  margin: 12px 0 0;
  white-space: pre-wrap;
}

@media (max-width: 720px) {
  .artist-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
