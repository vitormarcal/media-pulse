<template>
  <section class="track-list">
    <SectionHeading
      eyebrow="Faixas"
      title="As faixas que sustentam o álbum"
      description="Tracklist e sinais de repetição em uma leitura direta, sem sobrecarregar a página."
      summary="Aqui o peso está nas músicas, não no chrome."
    />

    <div class="tracks">
      <article v-for="track in tracks" :key="track.id" class="track-row">
        <span class="position">{{ track.position }}</span>
        <div class="content">
          <h3>{{ track.title }}</h3>
          <p>{{ track.meta }}</p>
        </div>
        <strong>{{ track.lastPlayed }}</strong>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { AlbumTrackModel } from '~/types/music'

defineProps<{
  tracks: AlbumTrackModel[]
}>()
</script>

<style scoped>
.track-list,
.tracks {
  display: grid;
  gap: 24px;
}

.track-row {
  display: grid;
  grid-template-columns: 60px minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 16px 18px;
  border-radius: 22px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.position,
.track-row p {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.position {
  font-size: 0.82rem;
  font-weight: 700;
}

.content {
  min-width: 0;
}

h3 {
  margin: 0;
  font-size: 1.05rem;
  line-height: 1.03;
}

.track-row p {
  margin-top: 6px;
  font-size: 0.88rem;
}

.track-row strong {
  color: var(--base-color-text-primary);
  font-size: 0.85rem;
}

@media (max-width: 820px) {
  .track-row {
    grid-template-columns: 48px 1fr;
  }

  .track-row strong {
    grid-column: 2;
  }
}
</style>
