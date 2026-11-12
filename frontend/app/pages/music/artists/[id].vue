<template>
  <main class="artist-page">
    <NuxtLink class="back-link" to="/music">← Música</NuxtLink>
    <div v-if="status === 'pending' && !data" class="panel muted"><p>Carregando artista...</p></div>
    <div v-else-if="error && !data" class="panel muted" role="alert">
      <p>Não foi possível carregar este artista.</p>
    </div>
    <template v-else-if="data">
      <ArtistPageHero
        :title="data.title"
        :cover-url="data.coverUrl"
        :profile="data.profile"
        :biography="data.biography"
      />
      <ArtistAlbumsPanel class="panel" :albums="data.albums" :artist-id="Number(data.id)" :artist-name="data.title">
        <div v-if="owner.authenticated" class="actions">
          <button
            class="section-action"
            type="button"
            :disabled="!data.musicBrainz"
            :aria-expanded="discographyOpen"
            aria-controls="artist-discography-editor"
            @click="discographyOpen = !discographyOpen"
          >
            {{ discographyOpen ? 'Fechar exploração' : 'Explorar discografia' }}
          </button>
          <NuxtLink
            class="section-action"
            :to="{ path: '/music/admin/album-duplicates', query: { artist: data.title, artistId: data.id } }"
            >Revisar duplicatas</NuxtLink
          >
        </div>
        <template #maintenance>
          <div v-if="owner.authenticated" v-show="discographyOpen" id="artist-discography-editor">
            <ArtistMusicBrainzDiscography
              :artist-id="Number(data.id)"
              :linked="Boolean(data.musicBrainz)"
              @imported="refresh"
            />
          </div>
        </template>
      </ArtistAlbumsPanel>
      <ArtistTrackList class="panel" :tracks="data.topTracks" />
      <ArtistContextPanel class="panel" :stats="data.stats" :recent-days="data.recentDays" />
      <section
        v-if="
          owner.authenticated ||
          data.genres.some((genre) => genre.active) ||
          data.profile?.links.length ||
          data.profile?.aliases.length
        "
        class="panel about-panel"
        aria-labelledby="about-artist"
      >
        <div class="section-head">
          <h2 id="about-artist">Sobre o artista</h2>
          <button
            v-owner
            class="section-action"
            type="button"
            :aria-expanded="metadataOpen"
            aria-controls="artist-metadata-editor"
            @click="metadataOpen = !metadataOpen"
          >
            {{ metadataOpen ? 'Fechar atualização' : 'Atualizar dados' }}
          </button>
        </div>
        <div v-if="owner.authenticated" v-show="metadataOpen" id="artist-metadata-editor">
          <ArtistMusicBrainzPanel
            :artist-id="Number(data.id)"
            :link="data.musicBrainz"
            :profile="data.profile"
            @applied="refresh"
          />
        </div>
        <ArtistGenresPanel
          v-if="owner.authenticated || data.genres.some((genre) => genre.active)"
          :artist-id="Number(data.id)"
          :genres="data.genres"
          @changed="refresh"
        />
        <details v-if="data.profile?.aliases.length" class="aliases">
          <summary>Outros nomes</summary>
          <p>{{ [...new Set(data.profile.aliases.map((alias) => alias.name))].join(' · ') }}</p>
        </details>
        <nav v-if="data.profile?.links.length" class="external-links" aria-label="Links do artista">
          <a
            v-for="link in data.profile.links"
            :key="link.type"
            :href="link.url"
            target="_blank"
            rel="noopener noreferrer"
            >{{ linkLabels[link.type] }} ↗</a
          >
        </nav>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import ArtistContextPanel from '~/components/music/ArtistContextPanel.vue'
import ArtistPageHero from '~/components/music/ArtistPageHero.vue'
import ArtistTrackList from '~/components/music/ArtistTrackList.vue'
import ArtistGenresPanel from '~/components/music/ArtistGenresPanel.vue'
import ArtistMusicBrainzPanel from '~/components/music/ArtistMusicBrainzPanel.vue'
import ArtistMusicBrainzDiscography from '~/components/music/ArtistMusicBrainzDiscography.vue'
import ArtistAlbumsPanel from '~/components/music/ArtistAlbumsPanel.vue'
import { useArtistPageData } from '~/composables/useArtistPageData'

const owner = useOwnerAuth()

const metadataOpen = ref(false)
const discographyOpen = ref(false)
const linkLabels = { OFFICIAL: 'Site oficial', WIKIPEDIA: 'Wikipedia', DISCOGS: 'Discogs', BANDCAMP: 'Bandcamp' }
const route = useRoute()
const id = computed(() => String(route.params.id))

const { data, error, status, refresh } = await useArtistPageData(id.value)

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
  gap: 32px;
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0 80px;
}
.panel {
  min-width: 0;
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
}
.back-link,
.muted {
  color: var(--base-color-text-secondary);
}
.back-link {
  width: fit-content;
}
.section-head,
.actions,
.external-links {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.section-head {
  justify-content: space-between;
}
h2 {
  margin: 0;
  font-size: 1.35rem;
  letter-spacing: -0.03em;
}
.section-action {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 14px;
  border: 0;
  border-radius: 16px;
  font: inherit;
  font-size: 0.82rem;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-primary);
  cursor: pointer;
}
.section-action:disabled {
  opacity: 0.6;
  cursor: default;
}
button:focus-visible,
a:focus-visible,
summary:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
.about-panel {
  display: grid;
  gap: 24px;
}
.aliases {
  border-top: 1px solid var(--base-color-border);
  padding-top: 12px;
}
summary {
  padding: 10px 0;
  cursor: pointer;
  font-size: 0.9rem;
}
.aliases p {
  margin: 8px 0 0;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.external-links {
  gap: 16px;
  font-size: 0.85rem;
}
.external-links a {
  color: inherit;
  text-decoration: underline;
  text-underline-offset: 4px;
}
@media (max-width: 600px) {
  .artist-page {
    width: calc(100% - 20px);
    gap: 24px;
    padding-top: 20px;
  }
}
</style>
