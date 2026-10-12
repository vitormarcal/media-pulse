<template>
  <NuxtLink :to="`/artworks/${item.id}`" class="art-work-card">
    <MediaImage
      :src="resolveMediaUrl(item.imageUrl)"
      :alt="item.title"
      loading="lazy"
      :fallback="item.type || 'Obra'"
      fallback-class="art-image-fallback"
    />
    <h3>{{ item.title }} <span v-if="item.favorite" aria-label="Favorita">♥</span></h3>
    <p>{{ [hideArtist ? null : item.artist?.name, item.year].filter((part) => part != null).join(' · ') }}</p>
    <p v-if="item.movement" class="art-muted">{{ item.movement }}</p>
  </NuxtLink>
</template>
<script setup lang="ts">
import type { ArtworkCard } from '~/types/artworks'
defineProps<{ item: ArtworkCard; hideArtist?: boolean }>()
const { resolveMediaUrl } = useMediaUrl()
</script>
