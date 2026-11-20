<template>
  <header class="top-nav" @keydown.esc="closeMenuWithFocus">
    <div class="nav-inner">
      <NuxtLink class="brand" to="/">
        <span class="brand-dot" />
        <span>Media Pulse</span>
      </NuxtLink>

      <nav id="primary-navigation" class="links" :class="{ expanded: menuOpen }" aria-label="Seções">
        <NuxtLink
          v-for="item in items"
          :key="item.href"
          :to="item.href"
          class="nav-link"
          :class="{ active: isActive(item.href) }"
          :aria-current="isActive(item.href) ? 'page' : undefined"
          @click="menuOpen = false"
        >
          {{ item.label }}
        </NuxtLink>
        <NuxtLink v-if="!owner.authenticated" class="nav-action account-action" to="/login" @click="menuOpen = false"
          >Entrar</NuxtLink
        >
        <button v-else type="button" class="nav-action" :disabled="loggingOut" :aria-busy="loggingOut" @click="logout">
          {{ loggingOut ? 'Saindo…' : 'Sair' }}
        </button>
      </nav>

      <div class="nav-actions">
        <button type="button" class="nav-action" @click="openSearch">Buscar</button>
        <button
          ref="menuButton"
          type="button"
          class="nav-action menu-toggle"
          aria-controls="primary-navigation"
          :aria-expanded="menuOpen"
          @click="menuOpen = !menuOpen"
        >
          {{ menuOpen ? 'Fechar' : 'Menu' }}
        </button>
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
const menuOpen = ref(false)
const menuButton = ref<HTMLButtonElement | null>(null)

function closeMenuWithFocus() {
  if (!menuOpen.value) return
  menuOpen.value = false
  menuButton.value?.focus()
}

watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  },
)

function isActive(href: string) {
  return href === '/' ? route.path === '/' : route.path === href || route.path.startsWith(href + '/')
}
const emit = defineEmits<{
  openSearch: []
}>()

function openSearch() {
  menuOpen.value = false
  emit('openSearch')
}

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
  gap: 6px;
}

.menu-toggle {
  display: none;
}

.nav-link,
.nav-action {
  padding: 10px 12px;
  border: 0;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 86%, white);
  color: var(--base-color-text-primary);
  white-space: nowrap;
  font-size: 0.875rem;
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
    display: none;
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    max-height: calc(100dvh - 100px);
    overflow-y: auto;
    padding: 4px;
  }

  .links.expanded {
    display: grid;
  }

  .menu-toggle {
    display: inline-flex;
    align-items: center;
  }

  .nav-link,
  .links .nav-action {
    display: flex;
    align-items: center;
    min-height: 44px;
    text-align: left;
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
    min-height: 44px;
    padding: 10px 12px;
  }
}
</style>
