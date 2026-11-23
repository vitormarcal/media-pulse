<template>
  <article class="event-card" :class="{ addition: event.kind === 'ADDITION' }">
    <NuxtLink v-if="image && !failed" :to="event.href" class="cover-link" tabindex="-1" aria-hidden="true">
      <img :src="image" alt="" loading="lazy" @error="failed = true" />
    </NuxtLink>
    <div class="event-body">
      <div class="event-meta">
        <span>{{ timelineDomains[event.domain] }}</span>
        <span class="event-kind">{{ event.kind === 'ADDITION' ? 'Adição ao acervo' : 'Atividade' }}</span>
        <time v-if="event.occurredAt" :datetime="event.occurredAt">{{ time }}</time>
      </div>
      <p class="action">{{ event.action }}</p>
      <h3>
        <NuxtLink :to="event.href">{{ event.title }}</NuxtLink>
      </h3>
      <p v-if="event.subtitle" class="subtitle">{{ event.subtitle }}</p>
      <template v-if="event.relatedItems.length">
        <ul class="music-links" aria-label="Álbuns e artistas ouvidos">
          <li v-for="item in visibleLinks" :key="item.href">
            <NuxtLink :to="item.href">
              <span>{{ item.title }}</span
              ><small v-if="item.subtitle">{{ item.subtitle }}</small>
            </NuxtLink>
          </li>
        </ul>
        <button
          v-if="event.relatedItems.length > 6"
          type="button"
          :aria-expanded="expanded"
          @click="expanded = !expanded"
        >
          {{ expanded ? 'Recolher' : `Ver todos os álbuns e artistas (${event.relatedItems.length})` }}
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
const visibleLinks = computed(() => (expanded.value ? props.event.relatedItems : props.event.relatedItems.slice(0, 6)))
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
  gap: 24px;
  padding: 24px;
  background: var(--base-color-surface-strong, white);
  border: 1px solid var(--base-color-surface-warm);
  border-radius: 28px;
}
.cover-link {
  flex: 0 0 100px;
}
.cover-link img {
  width: 100%;
  max-height: 150px;
  object-fit: contain;
  border-radius: 16px;
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
.event-kind {
  padding: 4px 8px;
  border-radius: 12px;
  background: var(--base-color-surface-warm);
}
.addition .event-kind {
  background: var(--base-color-surface-soft);
}
.action {
  margin: 14px 0 4px;
  color: var(--base-color-text-secondary);
  font-size: 0.875rem;
}
h3 {
  margin: 0;
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
  margin: 16px 0 0;
}
.music-links a {
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  border-radius: 16px;
  background: var(--base-color-surface-soft);
  font-size: 0.875rem;
}
small {
  color: var(--base-color-text-secondary);
}
button {
  margin-top: 12px;
  padding: 10px 12px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  cursor: pointer;
  text-align: left;
}
a:focus-visible,
button:focus-visible {
  outline: 2px solid #435ee5;
  outline-offset: 4px;
}
@media (max-width: 575px) {
  .event-card {
    padding: 16px;
    gap: 12px;
    flex-direction: column;
    border-radius: 20px;
  }
  .cover-link {
    flex: none;
    width: 100%;
  }
  .cover-link img {
    max-height: 230px;
    width: auto;
    max-width: 100%;
  }
  .event-body {
    width: 100%;
  }
  h3 {
    font-size: 1.125rem;
  }
}
</style>
