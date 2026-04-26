<template>
  <article class="companies-card">
    <div class="companies-head">
      <p class="eyebrow">Empresas do filme</p>
      <span class="summary-pill">{{ companies.visibleCount }} ativas</span>
    </div>

    <div v-if="companies.items.length" class="chip-list">
      <NuxtLink v-for="item in companies.items" :key="item.id" :to="item.href" class="company-pill">
        <div v-if="resolveMediaUrl(item.logoUrl)" class="logo-badge">
          <img :src="resolveMediaUrl(item.logoUrl)" :alt="item.name" />
        </div>
        <span class="company-name">{{ item.name }}</span>
        <small v-if="item.originCountry || item.typeLabel" class="company-meta">
          {{ [item.typeLabel, item.originCountry].filter(Boolean).join(' · ') }}
        </small>
      </NuxtLink>
    </div>

    <p v-else class="empty-copy">Ainda não há estúdios ou produtoras locais ligados a este filme.</p>

    <div v-if="editingEnabled" class="editor-toolbar">
      <button type="button" class="secondary-button" :disabled="syncing" @click="syncFromTmdb">
        {{ syncing ? 'Sincronizando...' : 'Sincronizar TMDb' }}
      </button>
    </div>

    <p v-if="feedback" class="feedback">{{ feedback }}</p>
  </article>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import type { MovieCompaniesSyncResponse, MoviePageData } from '~/types/movies'

const props = defineProps<{
  movieId: number
  companies: MoviePageData['companies']
  editing?: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const syncing = ref(false)
const feedback = ref<string | null>(null)
const editingEnabled = computed(() => props.editing ?? false)

async function syncFromTmdb() {
  syncing.value = true
  feedback.value = null

  try {
    const response = await $fetch<MovieCompaniesSyncResponse>(`/api/movies/${props.movieId}/companies/sync-tmdb`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
    feedback.value = `${response.syncedCount} empresas vindas do TMDb foram atualizadas.`
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível sincronizar as empresas do TMDb.'
  } finally {
    syncing.value = false
  }
}
</script>

<style scoped>
.companies-card,
.companies-head,
.editor-toolbar {
  display: grid;
  gap: 8px;
}

.companies-head {
  grid-template-columns: auto auto;
  justify-content: space-between;
  align-items: center;
}

.eyebrow {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.summary-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.76rem;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.company-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-primary);
}

.logo-badge {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 6px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 76%, white);
}

.logo-badge img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.company-name {
  font-size: 0.84rem;
  font-weight: 600;
}

.company-meta,
.empty-copy,
.feedback {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.72rem;
}

.secondary-button {
  width: fit-content;
  border: none;
  padding: 10px 14px;
  border-radius: 16px;
  cursor: pointer;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
}

.secondary-button:disabled {
  opacity: 0.6;
  cursor: default;
}
</style>
