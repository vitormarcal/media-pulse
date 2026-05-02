<template>
  <div class="section-heading">
    <p class="eyebrow">{{ eyebrow }}</p>
    <h2>{{ title }}</h2>
    <p v-if="description" class="description">{{ description }}</p>
    <p v-if="summary" class="summary">{{ summary }}</p>
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
  gap: 10px;
  max-width: 48rem;
}

.eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

h2 {
  margin: 0;
  font-size: clamp(1.95rem, 3.2vw, 3rem);
  line-height: 0.98;
  letter-spacing: -0.055em;
}

.description,
.summary {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.description {
  line-height: 1.56;
}

.summary {
  color: var(--base-color-text-muted);
  font-size: 0.88rem;
  line-height: 1.5;
}
</style>
