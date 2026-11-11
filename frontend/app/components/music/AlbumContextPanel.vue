<template>
  <section class="context-panel" aria-labelledby="album-listening">
    <h2 id="album-listening">Histórico de escuta</h2>
    <p v-if="!stats.totalPlays" class="empty">Nenhuma reprodução registrada.</p>
    <template v-else
      ><dl class="stats">
        <div>
          <dt>Reproduções de faixas</dt>
          <dd>{{ stats.totalPlays.toLocaleString('pt-BR') }}</dd>
        </div>
        <div v-if="stats.firstPlay">
          <dt>Primeiro registro</dt>
          <dd>{{ stats.firstPlay }}</dd>
        </div>
        <div v-if="stats.latestPlayAbsolute">
          <dt>Última escuta</dt>
          <dd>{{ stats.latestPlayAbsolute }}</dd>
        </div>
      </dl>
      <details v-if="recentDays.length">
        <summary>Ver dias recentes</summary>
        <ul>
          <li v-for="day in [...recentDays].reverse()" :key="day.id">
            <time :datetime="day.id">{{ day.label }}</time
            ><span>{{ day.plays }} {{ day.plays === 1 ? 'reprodução' : 'reproduções' }}</span>
          </li>
        </ul>
        <p v-if="recentDays.length === 12" class="note">Até 12 dias com reproduções mais recentes.</p>
      </details>
    </template>
  </section>
</template>
<script setup lang="ts">
import type { AlbumPageData } from '~/types/music'

defineProps<{
  stats: AlbumPageData['stats']
  recentDays: AlbumPageData['recentDays']
}>()
</script>

<style scoped>
.context-panel {
  display: grid;
  gap: 24px;
}
h2 {
  margin: 0;
  font-size: 1.35rem;
  letter-spacing: -0.03em;
}
.stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  margin: 0;
}
dt,
.empty,
.note {
  color: var(--base-color-text-secondary);
  font-size: 0.85rem;
}
dd {
  margin: 8px 0 0;
}
summary {
  width: fit-content;
  cursor: pointer;
  padding: 10px 0;
  font-size: 0.85rem;
}
summary:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
ul {
  list-style: none;
  padding: 0;
  margin: 12px 0 0;
}
li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-top: 1px solid var(--base-color-border);
  font-size: 0.85rem;
}
li span {
  color: var(--base-color-text-secondary);
}
.empty {
  margin: 0;
}
@media (max-width: 600px) {
  .stats {
    grid-template-columns: 1fr;
    gap: 20px;
  }
}
</style>
