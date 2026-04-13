<template>
  <section class="context-panel">
    <SectionHeading
      eyebrow="Contexto"
      title="Onde essa autoria realmente apareceu"
      description="Uma leitura curta do volume, do fechamento e do que ainda segue aberto."
      summary="O foco está no rastro de leitura, não em biografia."
    />

    <div class="panel-grid">
      <article class="stats-card">
        <p class="stats-label">Registros de leitura</p>
        <div class="stats-value">{{ stats.readsCount }}</div>
        <p class="stats-copy">{{ stats.booksCount }} livros no arquivo desse autor</p>
        <div class="stats-meta">
          <span>{{ stats.finishedCount }} concluídos · {{ stats.currentlyReadingCount }} em leitura</span>
          <span v-if="stats.latestFinishAbsolute">Último fechamento {{ stats.latestFinishAbsolute }}</span>
          <span v-else>{{ stats.latestFinish }}</span>
        </div>
      </article>

      <article class="balance-card">
        <p class="balance-label">Balanço</p>
        <div class="balance-list">
          <div class="balance-row">
            <span>Livros no arquivo</span>
            <strong>{{ stats.booksCount }}</strong>
          </div>
          <div class="balance-row">
            <span>Concluídos</span>
            <strong>{{ stats.finishedCount }}</strong>
          </div>
          <div class="balance-row">
            <span>Em leitura</span>
            <strong>{{ stats.currentlyReadingCount }}</strong>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { AuthorPageData } from '~/types/books'

defineProps<{
  stats: AuthorPageData['stats']
}>()
</script>

<style scoped>
.context-panel {
  display: grid;
  gap: 24px;
}

.panel-grid {
  display: grid;
  grid-template-columns: minmax(18rem, 0.9fr) minmax(0, 1.1fr);
  gap: 22px;
}

.stats-card,
.balance-card {
  display: grid;
  gap: 12px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.stats-label,
.balance-label {
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
.stats-meta {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.stats-meta {
  display: grid;
  gap: 8px;
  font-size: 0.88rem;
}

.balance-list {
  display: grid;
  gap: 10px;
}

.balance-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
}

.balance-row span {
  color: var(--base-color-text-secondary);
}

.balance-row strong {
  color: var(--base-color-text-primary);
}

@media (max-width: 980px) {
  .panel-grid {
    grid-template-columns: 1fr;
  }
}
</style>
