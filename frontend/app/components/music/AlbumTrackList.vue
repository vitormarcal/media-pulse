<template>
  <section class="track-list">
    <div class="track-heading">
      <h2>
        Faixas <span>{{ tracks.length }}</span>
      </h2>
      <slot />
    </div>
    <p v-if="!tracks.length">Nenhuma faixa cadastrada.</p>

    <div class="tracks">
      <article v-for="track in tracks" :key="track.id" class="track-row">
        <span class="position">{{ track.position }}</span>
        <div class="content">
          <h3>{{ track.title }}</h3>
          <p>{{ track.meta }}</p>
          <details v-if="owner.authenticated">
            <summary>
              {{ trackRatings[track.trackId] ? `Nota ${trackRatings[track.trackId]} · Alterar` : 'Avaliar faixa' }}
            </summary>
            <MediaRatingPanel
              media-type="tracks"
              :entity-id="track.trackId"
              :initial-rating="track.rating"
              compact
              minimal
              label="Nota da faixa"
              @changed="trackRatings[track.trackId] = $event?.rating ?? null"
            />
          </details>
          <MediaRatingPanel
            v-else-if="track.rating"
            media-type="tracks"
            :entity-id="track.trackId"
            :initial-rating="track.rating"
            compact
            minimal
            label="Nota"
          />
        </div>
        <span class="last-play">Última escuta: {{ track.lastPlayed }}</span>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import type { AlbumTrackModel } from '~/types/music'
const owner = useOwnerAuth()

const props = defineProps<{
  tracks: AlbumTrackModel[]
}>()
const trackRatings = ref<Record<number, number | null>>({})
watch(
  () => props.tracks,
  (tracks) => {
    trackRatings.value = Object.fromEntries(tracks.map((track) => [track.trackId, track.rating?.rating ?? null]))
  },
  { immediate: true },
)
</script>

<style scoped>
.track-list {
  display: grid;
  gap: 20px;
}
.tracks {
  display: grid;
}
.track-heading {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
h2 {
  margin: 0;
  font-size: 1.35rem;
  letter-spacing: -0.03em;
}
h2 span {
  font-size: 0.85rem;
  font-weight: 400;
  color: var(--base-color-text-secondary);
}
.track-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  border-top: 1px solid var(--base-color-border);
  padding: 16px 0;
}
.position {
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--base-color-text-secondary);
}
.content {
  min-width: 0;
}
h3 {
  margin: 0;
  font-size: 1.05rem;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.track-row p {
  margin: 6px 0 0;
  font-size: 0.88rem;
  color: var(--base-color-text-secondary);
}
.last-play {
  font-size: 0.8rem;
  color: var(--base-color-text-secondary);
}
summary {
  padding: 10px 0;
  cursor: pointer;
  font-size: 0.8rem;
  color: var(--base-color-text-secondary);
}
summary:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
.content :deep(.rating-panel.compact) {
  width: fit-content;
  margin-top: 8px;
}
@media (max-width: 820px) {
  .track-row {
    grid-template-columns: 36px minmax(0, 1fr);
    gap: 8px 12px;
  }
  .last-play {
    grid-column: 2;
  }
}
</style>
