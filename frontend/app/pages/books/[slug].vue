<template>
  <main class="book-page">
    <div v-if="status === 'pending' && !data" class="panel muted"><p>Carregando livro...</p></div>
    <div v-else-if="error && !data" class="panel muted" role="alert"><p>Não foi possível carregar este livro.</p></div>
    <template v-else-if="data">
      <BookPageHero
        detail
        :title="data.title"
        :authors="data.authors"
        :subtitle="data.subtitle"
        :description="data.description"
        :cover-url="data.coverUrl"
        :hero-meta="data.heroMeta"
      >
        <template #cover-actions
          ><MediaCoverUpload
            v-if="owner.authenticated"
            media-type="books"
            :entity-id="data.bookId"
            @saved="handleCoverSaved"
        /></template>
      </BookPageHero>
      <BookReadTimeline
        v-if="data.recentReads.length"
        class="panel"
        :reads="data.recentReads"
        :show-editions="showReadingEditions"
        compact
      />
      <BookEditorialPanel
        :key="data.bookId"
        class="panel"
        :book-id="data.bookId"
        :rating-text="data.stats.ratingText"
        :review-raw="data.reviewRaw"
        :reviewed-at="data.reviewedAt"
        :comments="data.comments"
      />
      <section
        v-show="owner.authenticated || seriesVisible || data.editions.length || data.readingEdition"
        class="panel about-book"
        aria-labelledby="about-book"
      >
        <h2 id="about-book">Sobre o livro</h2>
        <BookSeriesPanel
          :key="data.bookId"
          :book-id="data.bookId"
          @changed="seriesTagStatus?.refresh()"
          @visibility-changed="seriesVisible = $event"
        />
        <BookSeriesTagSyncStatus
          v-if="owner.authenticated"
          :key="`tags-${data.bookId}`"
          ref="seriesTagStatus"
          :book-id="data.bookId"
        />
        <BookContextPanel :editions="data.editions" :reading-edition="data.readingEdition" />
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import BookSeriesTagSyncStatus from '~/components/books/BookSeriesTagSyncStatus.vue'
import BookSeriesPanel from '~/components/books/BookSeriesPanel.vue'
import BookContextPanel from '~/components/books/BookContextPanel.vue'
import BookEditorialPanel from '~/components/books/BookEditorialPanel.vue'
import BookPageHero from '~/components/books/BookPageHero.vue'
import BookReadTimeline from '~/components/books/BookReadTimeline.vue'
import { useBookPageData } from '~/composables/useBookPageData'

import MediaCoverUpload from '~/components/media/MediaCoverUpload.vue'

const owner = useOwnerAuth()

const route = useRoute()
const slug = computed(() => String(route.params.slug))

const { data, error, status } = await useBookPageData(slug.value)

const seriesVisible = ref(false)
const showReadingEditions = computed(
  () => new Set(data.value?.recentReads.map((read) => read.edition?.id ?? 'unknown')).size > 1,
)

const seriesTagStatus = ref<InstanceType<typeof BookSeriesTagSyncStatus> | null>(null)

function handleCoverSaved(coverUrl: string) {
  if (!data.value) return
  data.value = { ...data.value, coverUrl }
  void seriesTagStatus.value?.refresh()
}

useHead(() => ({
  title: data.value ? `${data.value.title} · Media Pulse` : 'Livro · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value?.description || 'Página interna de livro no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.book-page {
  display: grid;
  gap: 32px;
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0 80px;
}
.panel {
  min-width: 0;
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
}
.about-book {
  display: grid;
  gap: 24px;
}
h2 {
  margin: 0;
  font-size: 1.35rem;
  letter-spacing: -0.03em;
}
.muted {
  color: var(--base-color-text-secondary);
}
.book-page :deep(.detail-cover .cover-upload) {
  min-width: 0;
  max-width: 100%;
}
.book-page :deep(.detail-cover .cover-upload form),
.book-page :deep(.detail-cover .cover-upload input) {
  min-width: 0;
  max-width: 100%;
}
.book-page :deep(.detail-cover .cover-upload label) {
  min-width: 0;
}
.book-page :deep(.detail-cover .cover-upload button) {
  color: white;
}
@media (max-width: 720px) {
  .book-page {
    width: calc(100% - 20px);
    gap: 24px;
    padding-top: 20px;
  }
}
</style>
