<template>
  <section class="context-panel">
    <SectionHeading
      eyebrow="Contexto"
      title="Onde esse livro ficou no seu percurso"
      description="O estado atual da leitura, a presença de edições e a avaliação que veio junto."
      summary="Mais lembrança do ponto em que ele ficou do que estatística pura."
    />

    <div class="panel-grid">
      <article class="stats-card">
        <p class="stats-label">Leituras registradas</p>
        <div class="stats-value">{{ stats.totalReads }}</div>
        <p class="stats-copy">{{ stats.currentStatus }}</p>
        <div class="stats-meta">
          <span>Última atividade {{ stats.latestActivity }}</span>
          <span v-if="stats.ratingText">Avaliação {{ stats.ratingText }}</span>
        </div>
      </article>

      <article class="editions-card">
        <p class="editions-label">Edições</p>
        <div v-if="editions.length" class="edition-list">
          <div v-for="edition in editions" :key="edition.id" class="edition-row">
            <strong>{{ edition.title }}</strong>
            <span>{{ edition.meta.join(' · ') }}</span>
          </div>
        </div>
        <p v-else class="editions-empty">Nenhuma edição específica apareceu por aqui ainda.</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { BookPageData } from '~/types/books'

defineProps<{
  stats: BookPageData['stats']
  editions: BookPageData['editions']
}>()
</script>

<style scoped>
.context-panel {
  display: grid;
  gap: 24px;
}

.panel-grid {
  display: grid;
  grid-template-columns: minmax(18rem, 0.9fr) minmax(0, 1.2fr);
  gap: 22px;
}

.stats-card,
.editions-card {
  display: grid;
  gap: 12px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.stats-label,
.editions-label {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.76rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.stats-value {
  font-size: clamp(3rem, 7vw, 4.6rem);
  line-height: 0.9;
  letter-spacing: -0.07em;
}

.stats-copy,
.stats-meta,
.editions-empty {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.stats-meta {
  display: grid;
  gap: 8px;
  font-size: 0.88rem;
}

.edition-list {
  display: grid;
  gap: 12px;
}

.edition-row {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
}

.edition-row strong {
  color: var(--base-color-text-primary);
}

.edition-row span {
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}

@media (max-width: 980px) {
  .panel-grid {
    grid-template-columns: 1fr;
  }
}
</style>
