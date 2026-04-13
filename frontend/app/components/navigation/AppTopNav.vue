<template>
  <header class="top-nav">
    <div class="nav-inner">
      <NuxtLink class="brand" to="/">
        <span class="brand-dot" />
        <span>Media Pulse</span>
      </NuxtLink>

      <nav class="links" aria-label="Seções">
        <NuxtLink v-for="item in items" :key="item.href" :to="item.href" class="nav-link">
          {{ item.label }}
        </NuxtLink>
      </nav>

      <button type="button" class="search-trigger" @click="$emit('openSearch')">
        Buscar
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
defineEmits<{
  openSearch: []
}>()

const items = [
  { label: 'Início', href: '/' },
  { label: 'Música', href: '/#music' },
  { label: 'Séries', href: '/shows' },
  { label: 'Filmes', href: '/movies' },
  { label: 'Livros', href: '/books' },
]
</script>

<style scoped>
.top-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  padding: 14px 0 10px;
  backdrop-filter: blur(18px);
  background: linear-gradient(180deg, rgba(255, 253, 248, 0.92), rgba(255, 253, 248, 0.76));
}

.nav-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--base-color-text-primary);
  font-size: 0.95rem;
  font-weight: 700;
}

.brand-dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--base-color-brand-red);
  box-shadow: 0 0 0 8px color-mix(in srgb, var(--base-color-brand-red) 8%, white);
}

.links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.nav-link,
.search-trigger {
  padding: 8px 14px;
  border: 0;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 86%, white);
  color: var(--base-color-text-primary);
  font-size: 0.82rem;
  cursor: pointer;
}

@media (max-width: 900px) {
  .nav-inner {
    width: min(100vw - 20px, 1480px);
    flex-wrap: wrap;
  }

  .links {
    order: 3;
    width: 100%;
  }
}
</style>
