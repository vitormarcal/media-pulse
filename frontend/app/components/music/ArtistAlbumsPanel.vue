<template>
  <section class="artist-albums" aria-labelledby="artist-albums-heading">
    <div class="heading">
      <h2 id="artist-albums-heading">
        Álbuns no acervo <span>{{ albums.length }}</span>
      </h2>
      <slot />
    </div>
    <label v-if="albums.length > 1" class="sort"
      >Ordenar por
      <select v-model="order">
        <option value="plays">Mais ouvidos</option>
        <option value="year">Ano de lançamento</option>
      </select></label
    >
    <p v-if="!albums.length" class="muted">Nenhum álbum no acervo.</p>
    <div v-else class="album-grid">
      <article v-for="album in orderedAlbums" :key="album.albumId" class="album">
        <NuxtLink :to="`/music/albums/${album.albumId}`" class="cover-link"
          ><img
            v-if="album.coverUrl"
            :src="resolveMediaUrl(album.coverUrl) || undefined"
            :alt="`Capa de ${album.albumTitle}`"
            loading="lazy"
          /><span v-else class="fallback">{{ album.albumTitle.slice(0, 1) }}</span></NuxtLink
        >
        <h3>
          <NuxtLink :to="`/music/albums/${album.albumId}`">{{ album.albumTitle }}</NuxtLink>
        </h3>
        <AlbumArtistLinks :artists="album.artists" :fallback-id="artistId" :fallback-name="artistName" />
        <p class="meta">
          <span v-if="album.year">{{ album.year }} · </span>{{ album.playCount.toLocaleString('pt-BR') }}
          {{ album.playCount === 1 ? 'reprodução' : 'reproduções' }}
        </p>
      </article>
    </div>
    <slot name="maintenance" />
  </section>
</template>
<script setup lang="ts">
import AlbumArtistLinks from '~/components/music/AlbumArtistLinks.vue'
import type { ArtistPageResponse } from '~/types/music'
const props = defineProps<{ albums: ArtistPageResponse['albums']; artistId: number; artistName: string }>()
const { resolveMediaUrl } = useMediaUrl()
const order = ref('plays')
const orderedAlbums = computed(() =>
  [...props.albums].sort((a, b) =>
    order.value === 'year'
      ? (a.year ?? Infinity) - (b.year ?? Infinity) || a.albumTitle.localeCompare(b.albumTitle)
      : b.playCount - a.playCount ||
        (b.lastPlayed || '').localeCompare(a.lastPlayed || '') ||
        (b.year ?? -Infinity) - (a.year ?? -Infinity) ||
        a.albumTitle.localeCompare(b.albumTitle),
  ),
)
</script>
<style scoped>
.artist-albums {
  display: grid;
  gap: 24px;
}
.heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
h2 {
  margin: 0;
  font-size: 1.35rem;
  letter-spacing: -0.03em;
}
h2 span {
  margin-left: 8px;
  font-size: 0.85rem;
  font-weight: 400;
  color: var(--base-color-text-secondary);
}
.sort {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  font-size: 0.85rem;
  color: var(--base-color-text-secondary);
}
select {
  padding: 10px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
  font: inherit;
}
.album-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 28px 20px;
}
.album {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8px;
}
.cover-link,
.fallback {
  display: grid;
  width: 100%;
  aspect-ratio: 1;
  border-radius: 20px;
}
img {
  width: 100%;
  height: auto;
  aspect-ratio: 1;
  object-fit: contain;
  border-radius: 20px;
}
.fallback {
  place-items: center;
  background: var(--base-color-surface-soft);
  font-size: 3rem;
  color: var(--base-color-text-secondary);
}
h3 {
  margin: 0;
  font-size: 1rem;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
a {
  color: inherit;
}
.album :deep(.artist-credits),
.meta {
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
  line-height: 1.5;
}
.meta {
  margin: 0;
}
.muted {
  margin: 0;
  color: var(--base-color-text-secondary);
}
a:focus-visible,
select:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
@media (max-width: 820px) {
  .album-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .album-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px 16px;
  }
}
</style>
