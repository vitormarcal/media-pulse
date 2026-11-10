<template>
  <section aria-labelledby="show-sessions-title">
    <h3 id="show-sessions-title">Sessões recentes</h3>
    <ul>
      <li v-for="watch in watches" :key="watch.id">
        <div>
          <strong>{{ watch.title }}</strong>
          <p>{{ watch.context }}</p>
        </div>
        <time :datetime="watch.watchedAt">{{ watch.meta }}</time>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import type { ShowWatchEntryModel } from '~/types/shows'

defineProps<{ watches: ShowWatchEntryModel[] }>()
</script>

<style scoped>
h3,
p,
ul {
  margin: 0;
}
h3 {
  font-size: 1rem;
}
ul {
  list-style: none;
  padding: 0;
  margin-top: 8px;
}
li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: baseline;
  gap: 4px 16px;
  padding: 12px 0;
  border-top: 1px solid var(--base-color-surface-soft);
}
p,
time {
  color: var(--base-color-text-secondary);
  font-size: 0.875rem;
}
</style>
