<template>
  <article class="artist-card">
    <img v-if="artist.profileImageUrl" :src="artist.profileImageUrl" :alt="`Foto de ${artist.name}`" />
    <div v-else class="placeholder" aria-hidden="true">{{ artist.name.slice(0, 1).toUpperCase() }}</div>
    <div>
      <strong>{{ artist.name }}</strong
      ><span>artista #{{ artist.artistId }}</span>
    </div>
    <dl>
      <div>
        <dt>Álbuns</dt>
        <dd>{{ artist.albumCount }}</dd>
      </div>
      <div>
        <dt>Faixas</dt>
        <dd>{{ artist.trackCount }}</dd>
      </div>
      <div>
        <dt>Reproduções</dt>
        <dd>{{ artist.playbackCount }}</dd>
      </div>
    </dl>
    <p>
      {{ artist.musicBrainzArtistId ? 'MusicBrainz vinculado' : 'Sem MusicBrainz' }} ·
      {{ artist.spotifyId ? 'Spotify vinculado' : 'Sem Spotify' }}
    </p>
  </article>
</template>

<script setup lang="ts">
import type { ArtistMergeCandidateResponse } from '~/types/music'
defineProps<{ artist: ArtistMergeCandidateResponse }>()
</script>

<style scoped>
.artist-card {
  flex: 1;
  min-width: 0;
  border-radius: 20px;
  background: #fff;
  padding: 14px;
}
.artist-card img,
.placeholder {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  background: #e0e0d9;
  display: grid;
  place-items: center;
  font-size: 1.7rem;
  font-weight: 700;
}
.artist-card strong,
.artist-card span {
  display: block;
  margin-top: 8px;
}
.artist-card span,
.artist-card p,
dt {
  color: #62625b;
  font-size: 0.75rem;
}
dl {
  display: flex;
  gap: 14px;
  margin: 14px 0;
}
dd {
  margin: 2px 0 0;
  font-weight: 700;
}
.artist-card p {
  margin: 0;
}
</style>
