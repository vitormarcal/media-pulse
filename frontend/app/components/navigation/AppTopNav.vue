<template>
  <header class="top-nav">
    <div class="nav-inner">
      <NuxtLink class="brand" to="/">
        <span class="brand-dot" />
        <span>Media Pulse</span>
      </NuxtLink>

      <nav class="links" aria-label="Seções">
        <NuxtLink
          v-for="item in items"
          :key="item.href"
          :to="item.href"
          class="nav-link"
          :class="{ active: isActive(item.href) }"
          :aria-current="isActive(item.href) ? 'page' : undefined"
        >
          {{ item.label }}
        </NuxtLink>
      </nav>

      <div class="nav-actions">
        <NuxtLink v-if="!owner.authenticated" class="nav-action" to="/login">Entrar</NuxtLink>
        <button v-else type="button" class="nav-action" :disabled="loggingOut" :aria-busy="loggingOut" @click="logout">
          {{ loggingOut ? 'Saindo…' : 'Sair' }}
        </button>
        <button type="button" class="nav-action" @click="$emit('openSearch')">Buscar</button>
      </div>
    </div>
    <p v-if="logoutError" class="logout-error" role="alert">{{ logoutError }}</p>
  </header>
</template>

<script setup lang="ts">
const owner = useOwnerAuth()
const route = useRoute()
const loggingOut = ref(false)
const logoutError = ref('')
function isActive(href: string) {
  return href === '/' ? route.path === '/' : route.path === href || route.path.startsWith(href + '/')
}
defineEmits<{
  openSearch: []
}>()

const config = useRuntimeConfig()
async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  logoutError.value = ''
  try {
    await $fetch('/api/auth/logout', { baseURL: config.public.apiBase, method: 'POST' })
    owner.value = { authenticated: false, csrfToken: '' }
    await navigateTo('/')
    window.location.reload()
  } catch {
    logoutError.value = 'Não foi possível sair. Confira a conexão e tente novamente.'
  } finally {
    loggingOut.value = false
  }
}

const items = [
  { label: 'Início', href: '/' },
  { label: 'Música', href: '/music' },
  { label: 'Séries', href: '/shows' },
  { label: 'Filmes', href: '/movies' },
  { label: 'Livros', href: '/books' },
  { label: 'Revistas', href: '/magazines' },
  { label: 'Artes visuais', href: '/artworks' },
  { label: 'Games', href: '/games' },
  { label: 'Treinos', href: '/workouts' },
  { label: 'Pessoas', href: '/people' },
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
  gap: 16px;
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--base-color-text-primary);
  flex-shrink: 0;
  white-space: nowrap;
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
  min-width: 0;
  gap: 8px;
}

.nav-link,
.nav-action {
  padding: 8px 14px;
  border: 0;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 86%, white);
  color: var(--base-color-text-primary);
  white-space: nowrap;
  font-size: 1rem;
  cursor: pointer;
}

.nav-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.nav-action {
  font-size: 0.875rem;
}

.nav-link:hover,
.nav-action:hover:not(:disabled) {
  background: var(--base-color-surface-warm);
}

.nav-link.active {
  color: var(--base-color-brand-red);
  background: color-mix(in srgb, var(--base-color-brand-red) 8%, var(--base-color-surface-strong));
}

.nav-action:disabled {
  cursor: wait;
  opacity: 0.6;
}

.logout-error {
  width: min(1480px, calc(100vw - 32px));
  margin: 8px auto 0;
  color: #9e0a0a;
  font-size: 0.875rem;
}

@media (max-width: 1200px) {
  .nav-inner {
    flex-wrap: wrap;
    gap: 12px;
  }

  .links {
    order: 3;
    flex-wrap: nowrap;
    width: 100%;
    overflow-x: auto;
    scrollbar-width: thin;
    scrollbar-color: var(--base-color-border) transparent;
    padding: 4px;
  }

  .nav-link {
    flex-shrink: 0;
  }
}

@media (max-width: 575px) {
  .nav-inner,
  .logout-error {
    width: calc(100% - 20px);
  }

  .brand {
    gap: 8px;
    font-size: 0.875rem;
  }

  .nav-action {
    padding: 8px 12px;
  }
}
</style>
