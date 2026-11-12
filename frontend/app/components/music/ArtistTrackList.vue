<template>
  <section class="track-list">
    <h2>Mais ouvidas por você</h2>

    <p v-if="!tracks.length" class="empty-state">Nenhuma reprodução registrada.</p>

    <div v-else class="tracks">
      <article v-for="track in tracks" :key="track.id" class="track-row">
        <div class="content">
          <h3>{{ track.title }}</h3>
          <p>
            <NuxtLink v-if="track.albumHref && track.albumTitle" :to="track.albumHref" class="album-link">
              {{ track.albumTitle }}
            </NuxtLink>
            <span v-else>{{ track.albumTitle || 'Sem álbum de referência' }}</span>
          </p>
        </div>
        <div class="meta">
          <strong>{{ track.meta }}</strong>
          <span>Última escuta: {{ track.lastPlayed }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ArtistTrackModel } from '~/types/music'

defineProps<{
  tracks: ArtistTrackModel[]
}>()
</script>

<style scoped>
.track-list {
  display: grid;
  gap: 24px;
}
.tracks {
  display: grid;
}
h2 {
  margin: 0;
  font-size: 1.35rem;
  letter-spacing: -0.03em;
}
.empty-state {
  margin: 0;
  color: var(--base-color-text-secondary);
}
.track-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 16px 0;
  border-top: 1px solid var(--base-color-border);
}
.content {
  min-width: 0;
}
h3,
p {
  margin: 0;
}
h3 {
  font-size: 1.05rem;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
p {
  margin-top: 6px;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
.album-link {
  color: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
}
.album-link:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
.meta {
  display: grid;
  gap: 6px;
  justify-items: end;
  text-align: right;
}
.meta strong {
  font-size: 0.85rem;
  color: var(--base-color-text-primary);
}
.meta span {
  font-size: 0.84rem;
  color: var(--base-color-text-secondary);
}
@media (max-width: 820px) {
  .track-row {
    grid-template-columns: 1fr;
  }
  .meta {
    justify-items: start;
    text-align: left;
  }
}
</style>
