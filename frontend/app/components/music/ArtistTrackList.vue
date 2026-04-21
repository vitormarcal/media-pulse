<template>
  <section class="track-list">
    <SectionHeading
      eyebrow="Faixas"
      title="O que mais voltou dentro desse nome"
      description="As músicas que puxaram esse artista com mais força no arquivo."
      summary="Faixa como detalhe recorrente, não como ruído."
    />

    <p v-if="!tracks.length" class="empty-state">Nenhuma faixa apareceu com peso suficiente para destacar aqui.</p>

    <div v-else class="tracks">
      <article v-for="track in tracks" :key="track.id" class="track-row">
        <div class="content">
          <h3>{{ track.title }}</h3>
          <p>
            <NuxtLink v-if="track.albumHref && track.albumTitle" :to="track.albumHref" class="album-link">
              {{ track.albumTitle }}
            </NuxtLink>
            <span v-else>{{ track.albumTitle || 'Sem álbum dominante visível' }}</span>
          </p>
        </div>
        <div class="meta">
          <strong>{{ track.meta }}</strong>
          <span>{{ track.lastPlayed }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ArtistTrackModel } from '~/types/music'

defineProps<{
  tracks: ArtistTrackModel[]
}>()
</script>

<style scoped>
.track-list,
.tracks {
  display: grid;
  gap: 24px;
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
  padding: 16px 18px;
  border-radius: 22px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
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
  line-height: 1.03;
}

p {
  margin-top: 6px;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}

.album-link {
  color: inherit;
  text-decoration: none;
}

.meta {
  display: grid;
  gap: 6px;
  justify-items: end;
  text-align: right;
}

.meta strong {
  color: var(--base-color-text-primary);
  font-size: 0.85rem;
}

.meta span {
  color: var(--base-color-text-secondary);
  font-size: 0.84rem;
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
