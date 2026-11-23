<template>
  <article class="event-card">
    <NuxtLink v-if="image && !failed" :to="event.href" class="cover-link" tabindex="-1" aria-hidden="true">
      <img :src="image" alt="" loading="lazy" @error="failed = true" />
    </NuxtLink>
    <div class="event-body">
      <div class="event-meta">
        <span>{{ timelineDomains[event.domain] }}</span
        ><time v-if="event.occurredAt" :datetime="event.occurredAt">{{ time }}</time>
      </div>
      <p v-if="!isMusic" class="action">{{ event.action }}</p>
      <h3>
        <NuxtLink :to="event.href">{{ event.title }}</NuxtLink>
      </h3>
      <p v-if="event.subtitle" class="subtitle">{{ event.subtitle }}</p>
      <template v-if="isMusic && event.relatedItems.length">
        <ul v-if="albums.length" class="music-links" aria-label="Álbuns ouvidos">
          <li v-for="item in visibleAlbums" :key="item.href">
            <NuxtLink :to="item.href"
              ><span>{{ item.title }}</span
              ><small v-if="item.subtitle">{{ item.subtitle }}</small></NuxtLink
            >
          </li>
        </ul>
        <div v-if="expanded || !albums.length" class="artist-links">
          <span v-if="artists.length">Artistas</span>
          <NuxtLink v-for="artist in artists" :key="artist.href" :to="artist.href">{{ artist.title }}</NuxtLink>
        </div>
        <button
          v-if="albums.length > 3 || (artists.length && albums.length)"
          type="button"
          :aria-expanded="expanded"
          @click="expanded = !expanded"
        >
          {{ expanded ? 'Recolher' : albums.length > 3 ? `Ver os ${albums.length} álbuns e artistas` : 'Ver artistas' }}
        </button>
      </template>
    </div>
  </article>
</template>

<script setup lang="ts">
import { timelineDomains, type TimelineEvent } from '~/types/timeline'
const props = defineProps<{ event: TimelineEvent; timeZone: string }>()
const { resolveMediaUrl } = useMediaUrl()
const failed = ref(false)
const expanded = ref(false)
const image = computed(() => resolveMediaUrl(props.event.imageUrl))
watch(image, () => (failed.value = false))
const isMusic = computed(() => props.event.domain === 'MUSIC')
const albums = computed(() => props.event.relatedItems.filter((item) => item.href.startsWith('/music/albums/')))
const artists = computed(() => props.event.relatedItems.filter((item) => item.href.startsWith('/music/artists/')))
const visibleAlbums = computed(() => (expanded.value ? albums.value : albums.value.slice(0, 3)))
const time = computed(() =>
  props.event.occurredAt
    ? new Intl.DateTimeFormat('pt-BR', { timeZone: props.timeZone, hour: '2-digit', minute: '2-digit' }).format(
        new Date(props.event.occurredAt),
      )
    : '',
)
</script>

<style scoped>
.event-card {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding: 20px;
  background: var(--base-color-surface-strong);
  border: 1px solid var(--base-color-surface-warm);
  border-radius: 28px;
}
.cover-link {
  flex: 0 0 80px;
}
.cover-link img {
  width: 100%;
  max-height: 120px;
  object-fit: contain;
  border-radius: 12px;
}
.event-body {
  flex: 1;
  min-width: 0;
}
.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  font-size: 0.75rem;
  color: var(--base-color-text-secondary);
}
.action {
  margin: 8px 0 4px;
  color: var(--base-color-text-secondary);
  font-size: 0.875rem;
}
h3 {
  margin: 4px 0 0;
  font-size: 1.25rem;
  overflow-wrap: anywhere;
}
.subtitle {
  margin: 8px 0 0;
  color: var(--base-color-text-secondary);
  font-size: 0.875rem;
}
.music-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  list-style: none;
  padding: 0;
  margin: 12px 0 0;
}
.music-links a {
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  border-radius: 16px;
  background: var(--base-color-surface-soft);
  font-size: 0.875rem;
  min-height: 44px;
  align-content: center;
}
small {
  color: var(--base-color-text-secondary);
}
button {
  min-height: 44px;
  margin-top: 8px;
  padding: 10px 12px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  cursor: pointer;
  text-align: left;
  font-size: 0.875rem;
}
.artist-links {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  margin-top: 12px;
  font-size: 0.875rem;
}
.artist-links > span {
  width: 100%;
  color: var(--base-color-text-secondary);
}
.artist-links a {
  padding: 10px 0;
  text-decoration: underline;
}
a:focus-visible,
button:focus-visible {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 4px;
}
@media (max-width: 575px) {
  .event-card {
    padding: 14px;
    gap: 12px;
    border-radius: 20px;
  }
  .cover-link {
    flex: 0 0 56px;
  }
  .cover-link img {
    max-height: 84px;
    width: 56px;
    border-radius: 10px;
  }
  h3 {
    font-size: 1rem;
  }
  .event-meta {
    gap: 4px 8px;
    font-size: 0.6875rem;
  }
  .music-links {
    display: grid;
    gap: 8px;
  }
  .music-links a {
    padding: 0;
    background: none;
    font-size: 0.8125rem;
  }
  button {
    padding: 10px 0;
    background: transparent;
    text-decoration: underline;
    font-size: 0.8125rem;
  }
  .artist-links {
    font-size: 0.8125rem;
  }
}
</style>
