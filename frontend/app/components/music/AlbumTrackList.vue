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
          <h3>
            {{ track.title }}
            <span
              v-if="track.listeningHighlight"
              class="listening-highlight"
              tabindex="0"
              role="img"
              aria-label="Destaque de escuta"
              :aria-describedby="`listening-highlight-${track.id}`"
            >
              <span aria-hidden="true">🔥</span>
              <span :id="`listening-highlight-${track.id}`" class="highlight-tooltip" role="tooltip"
                >Entre as mais ouvidas por você neste álbum</span
              >
            </span>
          </h3>
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

.listening-highlight {
  display: inline-block;
  margin-left: 4px;
  padding: 2px 4px;
  font-size: 0.95rem;
  font-weight: 400;
  cursor: help;
  border-radius: 6px;
}
.listening-highlight:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}
h3 {
  position: relative;
}
.highlight-tooltip {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 2;
  width: max-content;
  max-width: min(260px, 100%);
  padding: 10px 12px;
  border: 1px solid var(--base-color-border);
  border-radius: 12px;
  background: var(--base-color-text-primary);
  color: var(--base-color-surface-strong);
  font-size: 0.78rem;
  line-height: 1.5;
  visibility: hidden;
  pointer-events: none;
}
.listening-highlight:hover .highlight-tooltip,
.listening-highlight:focus .highlight-tooltip {
  visibility: visible;
}
</style>
