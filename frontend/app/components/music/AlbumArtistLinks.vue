<template>
  <span class="artist-credits">
    <template v-if="artists?.length">
      <template v-for="artist in artists" :key="artist.position">
        <NuxtLink :to="`/music/artists/${artist.artistId}`">{{ artist.creditedName }}</NuxtLink
        ><span class="join-phrase">{{ artist.joinPhrase }}</span>
      </template>
    </template>
    <NuxtLink v-else-if="fallbackId" :to="`/music/artists/${fallbackId}`">{{ fallbackName }}</NuxtLink>
    <template v-else>{{ fallbackName }}</template>
  </span>
</template>
<script setup lang="ts">
import type { AlbumArtistCredit } from '~/types/music'
defineProps<{ artists?: AlbumArtistCredit[]; fallbackId?: number; fallbackName?: string }>()
</script>
<style scoped>
.artist-credits {
  overflow-wrap: anywhere;
}
.join-phrase {
  white-space: pre-wrap;
}
a {
  color: inherit;
  text-decoration: none;
}
a:hover {
  text-decoration: underline;
}
a:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}
</style>
