<template>
  <div class="album-choice">
    <img v-if="album.coverUrl" :src="resolveMediaUrl(album.coverUrl) ?? undefined" :alt="`Capa de ${album.title}`" />
    <div v-else class="cover-placeholder" aria-hidden="true">♪</div>
    <div>
      <strong>{{ album.title }}</strong
      ><span
        >{{ album.year ?? 'Ano desconhecido' }} · {{ album.trackCount }} faixas · {{ album.playbackCount }} plays</span
      ><small>#{{ album.albumId }} · {{ providerSummary }}</small>
    </div>
    <span v-if="suggested" class="suggested">Principal sugerido</span>
  </div>
</template>
<script setup lang="ts">
import type { AlbumMergeCandidateResponse } from '~/types/music'
const props = defineProps<{ album: AlbumMergeCandidateResponse; suggested?: boolean }>()
const { resolveMediaUrl } = useMediaUrl()
const providerSummary = computed(() => {
  const labels = []
  if (props.album.spotifyIds.length) labels.push(`${props.album.spotifyIds.length} Spotify`)
  if (props.album.musicBrainzReleaseGroupId || props.album.musicBrainzReleaseIds.length) labels.push('MusicBrainz')
  return labels.join(' · ') || 'Sem IDs externos'
})
</script>
<style scoped>
.album-choice {
  position: relative;
  display: grid;
  grid-template-columns: 64px 1fr;
  gap: 12px;
  align-items: center;
  padding: 10px;
  border-radius: 18px;
  background: white;
}
.album-choice img,
.cover-placeholder {
  width: 64px;
  height: 64px;
  border-radius: 14px;
  object-fit: cover;
}
.cover-placeholder {
  display: grid;
  place-items: center;
  background: #e5e5e0;
  font-size: 24px;
}
.album-choice div:nth-child(2) {
  display: grid;
  gap: 3px;
  min-width: 0;
}
.album-choice strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.album-choice span,
.album-choice small {
  color: #62625b;
  font-size: 12px;
}
.suggested {
  position: absolute;
  right: 8px;
  top: 8px;
  border-radius: 12px;
  background: #e7f1ea;
  padding: 4px 7px;
  color: #103c25 !important;
  font-weight: 700;
}
</style>
