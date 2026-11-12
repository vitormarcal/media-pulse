<template>
  <section class="mag-grid-section">
    <h2 v-if="title">{{ title }}</h2>
    <p v-if="!items.length" class="mag-muted">{{ emptyMessage || 'Nenhum número encontrado.' }}</p>
    <div v-else class="mag-grid" :class="{ single: items.length === 1 }">
      <article v-for="item in items" :key="item.id" class="mag-card">
        <NuxtLink
          class="mag-card-cover"
          :to="item.href || '/magazines'"
          :aria-label="item.subtitle ? `${item.title} · ${item.subtitle}` : item.title"
          ><MediaImage
            :src="resolveMediaUrl(item.imageUrl)"
            :alt="`Capa de ${item.title}`"
            fallback="Capa indisponível"
            loading="lazy"
        /></NuxtLink>
        <div class="mag-card-copy">
          <NuxtLink :to="item.href || '/magazines'"
            ><h3>{{ item.title }}</h3></NuxtLink
          >
          <p v-if="item.subtitle">{{ item.subtitle }}</p>
          <p v-if="item.progressLabel || item.aside">
            {{ [item.progressLabel, item.aside].filter(Boolean).join(' · ') }}
          </p>
          <p v-if="item.activityLabel">{{ item.activityLabel }}</p>
        </div>
      </article>
    </div>
  </section>
</template>
<script setup lang="ts">
import type { BookLibraryCardModel } from '~/types/books'
defineProps<{ items: BookLibraryCardModel[]; title?: string; emptyMessage?: string }>()
const { resolveMediaUrl } = useMediaUrl()
</script>
<style scoped>
.mag-grid-section {
  display: grid;
  gap: 24px;
  min-width: 0;
}
h2 {
  margin: 0;
  font-size: 1.3rem;
}
.mag-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 28px 24px;
}
.mag-card {
  display: grid;
  align-content: start;
  gap: 16px;
  min-width: 0;
}
.mag-card-cover {
  height: 240px;
  display: flex;
  align-items: end;
  justify-content: center;
}
.mag-card-cover :deep(img) {
  max-width: 100%;
  max-height: 100%;
  height: auto;
  width: auto;
  object-fit: contain;
  border-radius: 4px;
}
.mag-card-cover :deep(.media-image-fallback) {
  height: 100%;
  width: 100%;
  box-sizing: border-box;
}
.mag-card-copy {
  display: grid;
  gap: 8px;
  align-content: start;
  min-width: 0;
}
h3 {
  margin: 0;
  font-size: 1rem;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
p {
  margin: 0;
  font-size: 0.85rem;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}
.single {
  grid-template-columns: minmax(0, 1fr);
}
.single .mag-card {
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 24px;
  align-items: center;
}
.single .mag-card-copy {
  max-width: 60ch;
}
@media (max-width: 800px) {
  .mag-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .single {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 560px) {
  .mag-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px 16px;
  }
  .mag-card-cover {
    height: 180px;
  }
  .single {
    grid-template-columns: minmax(0, 1fr);
  }
  .single .mag-card {
    grid-template-columns: minmax(0, 1fr);
  }
  .single .mag-card-cover {
    height: 240px;
  }
}
</style>
