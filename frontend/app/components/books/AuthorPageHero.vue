<template>
  <header class="author-hero">
    <NuxtLink class="back-link" to="/books">← Voltar para livros</NuxtLink>
    <div class="identity">
      <p class="eyebrow">Autor</p>
      <h1>{{ title }}</h1>
      <slot />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{ title: string }>()
</script>

<style scoped>
.author-hero {
  display: grid;
  gap: 20px;
}
.back-link {
  width: fit-content;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
.identity {
  padding: clamp(20px, 4vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
}
.eyebrow {
  margin: 0 0 12px;
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}
h1 {
  margin: 0;
  font-size: clamp(2rem, 5vw, 3.5rem);
  line-height: 1.08;
  letter-spacing: -0.04em;
  overflow-wrap: anywhere;
}
</style>
