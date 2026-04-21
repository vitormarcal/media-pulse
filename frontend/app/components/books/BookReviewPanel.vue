<template>
  <section class="review-panel">
    <SectionHeading
      eyebrow="Review"
      title="O que ficou escrito sobre esse livro"
      description="A leitura crítica entra como camada editorial da página, sem competir com o restante do contexto."
      summary="Texto direto, superfície quente e ritmo de leitura antes de qualquer chrome extra."
    />

    <article class="review-card">
      <p v-if="reviewedLabel" class="review-meta">{{ reviewedLabel }}</p>

      <p v-for="(paragraph, index) in paragraphs" :key="`paragraph-${index}`" class="review-paragraph">
        {{ paragraph }}
      </p>
    </article>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import { formatAbsoluteDate, formatRelativeDate } from '~/utils/formatting'

const props = defineProps<{
  review: string
  reviewedAt?: string | null
}>()

const paragraphs = computed(() =>
  props.review
    .split(/\n\s*\n/g)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean),
)

const reviewedLabel = computed(() => {
  if (!props.reviewedAt) return null
  return `Escrita ${formatRelativeDate(props.reviewedAt)} · ${formatAbsoluteDate(props.reviewedAt)}`
})
</script>

<style scoped>
.review-panel {
  display: grid;
  gap: 24px;
}

.review-card {
  display: grid;
  gap: 18px;
  padding: clamp(22px, 3vw, 32px);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 243, 238, 0.96));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
}

.review-meta {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.review-paragraph {
  max-width: 54rem;
  margin: 0;
  color: var(--base-color-text-primary);
  font-size: 1rem;
  line-height: 1.72;
}
</style>
