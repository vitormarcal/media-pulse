<template>
  <div class="section-heading">
    <p class="eyebrow">{{ eyebrow }}</p>
    <h2>{{ title }}</h2>
    <p class="description">{{ description }}</p>
    <p class="summary">{{ summary }}</p>
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
  max-width: 54rem;
}

.eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h2 {
  margin: 0;
  font-size: clamp(1.8rem, 3vw, 2.8rem);
  letter-spacing: -0.05em;
}

.description,
.summary {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.summary {
  color: var(--base-color-text-muted);
  font-size: 0.92rem;
}
</style>
