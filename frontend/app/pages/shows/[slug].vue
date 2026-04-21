<template>
  <main class="show-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página da série...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar esta série.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <ShowPageHero
        :title="data.title"
        :subtitle="heroSubtitle"
        :description="data.description"
        :gallery="data.gallery"
        :hero-meta="data.heroMeta"
      />

      <ShowProgressPanel
        :progress="data.progress"
        :seasons="data.seasons"
      />

      <ShowAddWatchPanel
        :show-id="data.showId"
        @created="handleWatchCreated"
      />

      <ShowWatchTimeline :watches="data.recentWatches" />
    </template>
  </main>
</template>

<script setup lang="ts">
import ShowAddWatchPanel from '~/components/shows/ShowAddWatchPanel.vue'
import ShowPageHero from '~/components/shows/ShowPageHero.vue'
import ShowProgressPanel from '~/components/shows/ShowProgressPanel.vue'
import ShowWatchTimeline from '~/components/shows/ShowWatchTimeline.vue'
import { useShowPageData } from '~/composables/useShowPageData'
import type { ManualShowWatchCreateResponse } from '~/types/shows'

const route = useRoute()
const slug = computed(() => String(route.params.slug))

const { data, error, status, refresh } = await useShowPageData(slug.value)

const heroSubtitle = computed(() => {
  if (!data.value) return null

  if (data.value.originalTitle !== data.value.title && data.value.year) {
    return `${data.value.originalTitle} · ${data.value.year}`
  }

  if (data.value.originalTitle !== data.value.title) {
    return data.value.originalTitle
  }

  return data.value.year ? String(data.value.year) : null
})

useHead(() => ({
  title: data.value ? `${data.value.title} · Media Pulse` : 'Série · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value?.description || 'Página interna de série no Media Pulse.',
    },
  ],
}))

async function handleWatchCreated(_response: ManualShowWatchCreateResponse) {
  await refresh()
}
</script>

<style scoped>
.show-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.state-card {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
}

.state-card.error {
  color: #7a1414;
}

pre {
  margin: 12px 0 0;
  white-space: pre-wrap;
}

@media (max-width: 720px) {
  .show-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
