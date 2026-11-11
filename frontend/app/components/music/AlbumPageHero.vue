<template>
  <section class="album-hero">
    <div class="identification">
      <h1>{{ title }}</h1>
      <p class="artist-credits">
        <template v-for="artist in artists" :key="artist.position"
          ><NuxtLink :to="`/music/artists/${artist.artistId}`">{{ artist.creditedName }}</NuxtLink
          ><span class="join-phrase">{{ artist.joinPhrase }}</span></template
        >
      </p>
      <p v-if="heroMeta.length" class="year">{{ heroMeta.join(' · ') }}</p>
    </div>
    <div class="cover-frame">
      <img v-if="resolvedCoverUrl" :src="resolvedCoverUrl" :alt="`Capa de ${title}`" @error="coverFailed = true" />
      <div v-else class="cover-fallback" role="img" :aria-label="`Álbum sem capa: ${title}`">
        {{ title.slice(0, 1) }}
      </div>
    </div>
    <div class="memory"><slot /></div>
  </section>
</template>

<script setup lang="ts">
import type { AlbumPageData } from '~/types/music'
const props = defineProps<{
  title: string
  artists: AlbumPageData['artists']
  coverUrl: string | null
  heroMeta: string[]
}>()
const { resolveMediaUrl } = useMediaUrl()
const coverFailed = ref(false)
watch(
  () => props.coverUrl,
  () => {
    coverFailed.value = false
  },
)
const resolvedCoverUrl = computed(() => (coverFailed.value ? null : resolveMediaUrl(props.coverUrl)))
</script>

<style scoped>
.album-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 360px);
  grid-template-rows: auto 1fr;
  gap: 28px 40px;
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
  align-items: start;
}
.identification,
.memory {
  min-width: 0;
}
h1 {
  margin: 0;
  font-size: clamp(2rem, 4.5vw, 3.5rem);
  line-height: 1.06;
  letter-spacing: -0.045em;
  overflow-wrap: anywhere;
}
.artist-credits {
  margin: 16px 0 0;
  font-size: 1.1rem;
  line-height: 1.6;
  overflow-wrap: anywhere;
}
.artist-credits a {
  color: inherit;
  text-decoration: underline;
  text-decoration-color: var(--base-color-border);
  text-underline-offset: 4px;
}
.artist-credits a:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
.join-phrase {
  white-space: pre-wrap;
}
.year {
  margin: 12px 0 0;
  color: var(--base-color-text-secondary);
  font-size: 0.9rem;
}
.cover-frame {
  grid-column: 2;
  grid-row: 1 / span 2;
  width: 100%;
}
.cover-frame img {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 20px;
}
.cover-fallback {
  aspect-ratio: 1;
  display: grid;
  place-items: center;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
  font-size: 5rem;
  color: var(--base-color-text-secondary);
}
.memory:empty {
  display: none;
}
@media (max-width: 720px) {
  .album-hero {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .cover-frame {
    grid-column: 1;
    grid-row: 2;
    max-width: 360px;
    justify-self: center;
  }
  .memory {
    grid-row: 3;
  }
}
</style>
