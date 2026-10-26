<template>
  <div class="app-shell">
    <AppTopNav @open-search="searchOpen = true" />
    <SpotifyStatusBanner
      v-if="owner.authenticated && spotifyStatus?.status === 'REAUTHORIZATION_REQUIRED'"
      :status="spotifyStatus"
    />
    <SearchOverlay
      :open="searchOpen"
      :loading="loading"
      :error="hasError"
      :query="query"
      :results="results"
      @close="searchOpen = false"
      @search="handleSearch"
    />
    <slot />
  </div>
</template>

<script setup lang="ts">
import AppTopNav from '~/components/navigation/AppTopNav.vue'
import SpotifyStatusBanner from '~/components/integrations/SpotifyStatusBanner.vue'
import SearchOverlay from '~/components/navigation/SearchOverlay.vue'
import { fetchGlobalSearch } from '~/composables/useGlobalSearch'
import type { GlobalSearchData } from '~/types/search'
import type { SpotifyStatusResponse } from '~/types/spotify'

const owner = useOwnerAuth()

const route = useRoute()
const config = useRuntimeConfig()
const { data: spotifyStatus } = await useAsyncData(
  'owner-spotify-status',
  () =>
    owner.value.authenticated
      ? $fetch<SpotifyStatusResponse>('/api/admin/spotify/status', { baseURL: config.public.apiBase })
      : Promise.resolve(null),
  { watch: [() => owner.value.authenticated] },
)
const searchOpen = ref(false)
const query = ref('')
const results = ref<GlobalSearchData | null>(null)
const loading = ref(false)
const hasError = ref(false)

watch(
  () => route.fullPath,
  () => {
    searchOpen.value = false
  },
)

let searchTimer: ReturnType<typeof setTimeout> | null = null

function handleSearch(value: string) {
  query.value = value
  hasError.value = false

  if (searchTimer) {
    clearTimeout(searchTimer)
  }

  if (!value.trim()) {
    results.value = null
    loading.value = false
    return
  }

  loading.value = true

  searchTimer = setTimeout(async () => {
    try {
      results.value = await fetchGlobalSearch(value)
    } catch {
      hasError.value = true
      results.value = null
    } finally {
      loading.value = false
    }
  }, 180)
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}
</style>
