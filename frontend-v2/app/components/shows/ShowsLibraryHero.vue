<template>
  <section class="library-hero">
    <div class="copy">
      <p class="eyebrow">Biblioteca de séries</p>
      <h1>{{ title }}</h1>
      <p class="intro">{{ intro }}</p>
    </div>

    <div class="actions">
      <NuxtLink class="action-primary" :to="primaryLink">
        {{ primaryLabel }}
      </NuxtLink>
      <NuxtLink class="action-secondary" :to="secondaryLink">
        {{ secondaryLabel }}
      </NuxtLink>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  intro: string
  primaryLink: string
  primaryLabel: string
  secondaryLink: string
  secondaryLabel: string
}>()
</script>

<style scoped>
.library-hero {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
}

.copy {
  display: grid;
  gap: 12px;
}

.eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 5.8rem);
  line-height: 0.92;
  letter-spacing: -0.075em;
}

.intro {
  max-width: 42rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.62;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: end;
  gap: 10px;
}

.action-primary,
.action-secondary {
  padding: 8px 14px;
  border-radius: 16px;
  font-size: 0.8rem;
}

.action-primary {
  background: var(--base-color-brand-red);
  color: #000;
}

.action-secondary {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

@media (max-width: 900px) {
  .library-hero {
    grid-template-columns: 1fr;
  }

  .actions {
    justify-content: start;
  }
}
</style>
