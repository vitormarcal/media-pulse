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
      <p v-if="displayMeta.length" class="year">{{ displayMeta.join(' · ') }}</p>
      <AlbumIntroduction v-if="context?.introduction" :introduction="context.introduction" />
    </div>
    <div class="cover-frame">
      <button
        v-if="resolvedCoverUrl"
        class="cover-button"
        type="button"
        :aria-label="`Ampliar capa de ${title}`"
        @click="gallery?.open(0)"
      >
        <img :src="resolvedCoverUrl" :alt="`Capa de ${title}`" @error="coverFailed = true" />
      </button>
      <div v-else class="cover-fallback" role="img" :aria-label="`Álbum sem capa: ${title}`">
        {{ title.slice(0, 1) }}
      </div>
      <button v-if="resolvedCoverUrl" type="button" class="view-image" @click="gallery?.open(0)">Ver imagem</button>
      <MediaImageGallery
        ref="gallery"
        :images="coverUrl && !coverFailed ? [coverUrl] : []"
        :title="title"
        viewer-only
      />
    </div>
    <div class="memory"><slot /></div>
  </section>
</template>

<script setup lang="ts">
import AlbumIntroduction from '~/components/music/AlbumIntroduction.vue'
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
import { albumContextMeta } from '~/utils/albumContext'
import type { AlbumContext, AlbumPageData } from '~/types/music'
const props = defineProps<{
  title: string
  artists: AlbumPageData['artists']
  coverUrl: string | null
  heroMeta: string[]
  context?: AlbumContext | null
}>()
const gallery = ref<InstanceType<typeof MediaImageGallery> | null>(null)
const displayMeta = computed(() =>
  props.context ? albumContextMeta(props.context, Number(props.heroMeta[0]) || null) : props.heroMeta,
)
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

.cover-button {
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  border-radius: 20px;
  background: transparent;
  cursor: zoom-in;
}
.view-image {
  margin-top: 12px;
  min-height: 44px;
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}
.cover-button:focus-visible,
.view-image:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
</style>
