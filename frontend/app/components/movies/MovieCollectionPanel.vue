<template>
  <section v-if="collection" class="collection-panel">
    <SectionHeading
      eyebrow="Coleção"
      :title="collection.name"
      description="A franquia oficial do TMDb aparece aqui com os filmes que já estão no seu catálogo."
      :summary="collection.progressLabel"
    />

    <div class="collection-grid">
      <div class="collection-poster">
        <img v-if="resolvedPosterUrl" :src="resolvedPosterUrl" :alt="collection.name" />
        <div v-else class="poster-fallback">{{ collection.name.slice(0, 1) }}</div>
      </div>

      <div class="movie-rail">
        <component
          :is="movie.href ? NuxtLink : 'article'"
          v-for="movie in collection.movies"
          :key="movie.id"
          class="movie-card"
          :class="{ current: movie.current, watched: movie.watched }"
          :to="movie.href || undefined"
        >
          <div class="movie-poster">
            <img v-if="resolveMediaUrl(movie.imageUrl)" :src="resolveMediaUrl(movie.imageUrl)" :alt="movie.title" />
            <div v-else class="movie-fallback">{{ movie.title.slice(0, 1) }}</div>
          </div>
          <div class="movie-copy">
            <span>{{ movie.current ? 'Este filme' : movie.watched ? 'Assistido' : 'Na fila' }}</span>
            <strong>{{ movie.title }}</strong>
            <p>{{ movie.subtitle }}</p>
          </div>
        </component>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { MoviePageData } from '~/types/movies'

const NuxtLink = resolveComponent('NuxtLink')

const props = defineProps<{
  collection: MoviePageData['collection']
}>()

const { resolveMediaUrl } = useMediaUrl()
const resolvedPosterUrl = computed(() => resolveMediaUrl(props.collection?.posterUrl ?? props.collection?.backdropUrl))
</script>

<style scoped>
.collection-panel {
  display: grid;
  gap: 24px;
}

.collection-grid {
  display: grid;
  grid-template-columns: minmax(12rem, 0.42fr) minmax(0, 1fr);
  gap: 22px;
  align-items: stretch;
}

.collection-poster {
  overflow: hidden;
  min-height: 320px;
  border: 8px solid #ffffff;
  border-radius: 28px;
  background: var(--base-color-surface-soft);
}

.collection-poster img,
.poster-fallback {
  width: 100%;
  height: 100%;
  min-height: 320px;
  object-fit: cover;
}

.poster-fallback,
.movie-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-primary);
  font-size: 4rem;
  font-weight: 700;
  background: var(--base-color-surface-strong);
}

.movie-rail {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(148px, 1fr));
  gap: 16px;
}

.movie-card {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 2px solid transparent;
  border-radius: 24px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 86%, #ffffff);
  color: inherit;
  text-decoration: none;
}

.movie-card.current {
  border-color: var(--base-color-brand-red);
}

.movie-poster {
  overflow: hidden;
  aspect-ratio: 2 / 3;
  border-radius: 18px;
  background: var(--base-color-surface-soft);
}

.movie-poster img,
.movie-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.movie-fallback {
  font-size: 2.5rem;
}

.movie-copy {
  display: grid;
  gap: 5px;
}

.movie-copy span {
  color: var(--base-color-brand-red);
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.movie-card:not(.watched) .movie-copy span {
  color: var(--base-color-text-secondary);
}

.movie-copy strong {
  color: var(--base-color-text-primary);
  line-height: 1.1;
}

.movie-copy p {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}

@media (max-width: 900px) {
  .collection-grid {
    grid-template-columns: 1fr;
  }

  .collection-poster {
    min-height: 220px;
  }

  .collection-poster img,
  .poster-fallback {
    min-height: 220px;
  }
}
</style>
