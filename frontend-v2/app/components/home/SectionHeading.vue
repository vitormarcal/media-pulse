<template>
  <div class="section-heading">
    <p class="eyebrow">{{ eyebrow }}</p>
    <div class="content">
      <div>
        <h2>{{ title }}</h2>
        <p class="description">{{ description }}</p>
      </div>
      <p class="summary">{{ summary }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  eyebrow: string
  title: string
  description: string
  summary: string
}>()
</script>

<style scoped>
.section-heading {
  display: grid;
  gap: 12px;
}

.eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.content {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(16rem, 1fr);
  gap: 20px;
  align-items: end;
}

h2 {
  margin: 0;
  font-size: clamp(1.8rem, 3vw, 2.8rem);
  letter-spacing: -0.04em;
}

.description,
.summary {
  margin: 8px 0 0;
  color: var(--base-color-text-secondary);
}

.summary {
  padding: 16px 18px;
  border-radius: 20px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 70%, white);
}

@media (max-width: 920px) {
  .content {
    grid-template-columns: 1fr;
  }
}
</style>
