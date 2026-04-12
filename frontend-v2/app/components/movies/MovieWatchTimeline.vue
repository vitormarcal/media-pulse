<template>
  <section class="watch-timeline">
    <SectionHeading
      eyebrow="Histórico"
      title="As sessões mais recentes"
      description="Uma linha do tempo curta para localizar quando esse filme voltou a aparecer."
      summary="Aqui a repetição também conta a história."
    />

    <div class="timeline-list">
      <article v-for="watch in watches" :key="watch.id" class="timeline-item">
        <div class="timeline-marker" />
        <div class="timeline-body">
          <div class="timeline-header">
            <div>
              <p class="timeline-context">{{ watch.source }}</p>
              <h3>{{ watch.title }}</h3>
            </div>
            <div class="timeline-dates">
              <strong>{{ watch.relativeWatchedAt }}</strong>
              <span>{{ watch.meta }}</span>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { MovieWatchEntryModel } from '~/types/movies'

defineProps<{
  watches: MovieWatchEntryModel[]
}>()
</script>

<style scoped>
.watch-timeline,
.timeline-list {
  display: grid;
  gap: 24px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 16px;
}

.timeline-marker {
  width: 18px;
  height: 18px;
  margin-top: 14px;
  border-radius: 50%;
  background: var(--base-color-brand-red);
  box-shadow: 0 0 0 8px color-mix(in srgb, var(--base-color-brand-red) 8%, white);
}

.timeline-body {
  display: grid;
  gap: 12px;
  padding: 18px 20px;
  border-radius: 26px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: start;
}

.timeline-context,
.timeline-dates span {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.timeline-context {
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h3 {
  margin: 6px 0 0;
  font-size: 1.14rem;
  line-height: 1.03;
}

.timeline-dates {
  display: grid;
  justify-items: end;
  gap: 6px;
  text-align: right;
}

.timeline-dates strong {
  font-size: 0.9rem;
}

.timeline-dates span {
  font-size: 0.84rem;
}

@media (max-width: 820px) {
  .timeline-header {
    flex-direction: column;
  }

  .timeline-dates {
    justify-items: start;
    text-align: left;
  }
}
</style>
