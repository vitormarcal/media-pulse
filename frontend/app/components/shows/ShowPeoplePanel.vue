<template>
  <article class="people-card">
    <div class="people-head">
      <div class="people-copy">
        <p class="eyebrow">Pessoas</p>
        <h2>Direção, roteiro e elenco</h2>
      </div>

      <div class="head-meta">
        <span v-if="people.curated" class="curation-badge">Curadoria manual</span>
        <span class="summary-pill">{{ people.visibleCount }} pessoas</span>
      </div>
    </div>

    <div class="people-groups">
      <section v-for="group in people.groups" :key="group.id" class="group-row">
        <p class="group-label">{{ group.title }}</p>

        <div class="chip-list">
          <div v-for="item in group.items" :key="item.id" class="person-pill">
            <div class="person-avatar">
              <img v-if="resolveMediaUrl(item.profileUrl)" :src="resolveMediaUrl(item.profileUrl)" :alt="item.name" />
              <div v-else class="avatar-fallback">{{ item.name.slice(0, 1) }}</div>
            </div>

            <div class="person-copy">
              <NuxtLink :to="item.href" class="person-name">{{ item.name }}</NuxtLink>
              <small class="person-role">{{ item.roleLabel }}</small>
            </div>
            <button
              v-if="editing"
              type="button"
              class="remove-button"
              :disabled="removingKey === `${group.id}:${item.personId}`"
              :aria-label="`Remover ${item.name} de ${group.title}`"
              @click="removeCredit(group.id, item.personId, item.name)"
            >
              ×
            </button>
          </div>
        </div>
      </section>

      <p v-if="!people.groups.length" class="empty-copy">Nenhum crédito disponível.</p>

      <div v-if="editing" class="editor-actions">
        <button type="button" class="secondary-button" :disabled="syncing" @click="syncFromTmdb">
          {{ syncing ? 'Sincronizando...' : 'Sincronizar base TMDb' }}
        </button>
        <button type="button" class="secondary-button" :disabled="loadingCandidates" @click="toggleCandidates">
          {{ candidatesVisible ? 'Ocultar opções' : loadingCandidates ? 'Buscando...' : 'Trazer mais do TMDb' }}
        </button>
      </div>

      <div v-if="candidatesVisible" class="candidate-panel">
        <p v-if="loadingCandidates" class="empty-copy">Buscando pessoas no TMDb...</p>
        <template v-else-if="candidates">
          <div class="candidate-toolbar">
            <input v-model="candidateQuery" type="search" placeholder="Buscar por nome ou função" />
            <button
              v-if="selectedKeys.length"
              type="button"
              class="mini-button"
              :disabled="importingBatch"
              @click="importSelected"
            >
              {{ importingBatch ? 'Adicionando...' : `Adicionar selecionados (${selectedKeys.length})` }}
            </button>
          </div>
          <section v-for="group in filteredGroups" :key="group.id" class="group-row">
            <p class="group-label">{{ group.title }}</p>
            <div class="candidate-list">
              <div v-for="item in visibleCandidates(group)" :key="candidateKey(item)" class="candidate-card">
                <input
                  type="checkbox"
                  :checked="selectedKeys.includes(candidateKey(item))"
                  :aria-label="`Selecionar ${item.name}`"
                  @change="toggleCandidate(item)"
                />
                <div class="candidate-identity">
                  <div class="person-avatar">
                    <img
                      v-if="resolveMediaUrl(item.profileUrl)"
                      :src="resolveMediaUrl(item.profileUrl)"
                      :alt="item.name"
                    />
                    <div v-else class="avatar-fallback">{{ item.name.slice(0, 1) }}</div>
                  </div>
                  <div class="person-copy">
                    <span class="person-name">{{ item.name }}</span
                    ><small class="person-role">{{ item.roleLabel }}</small>
                  </div>
                </div>
                <a class="tmdb-person-link" :href="item.tmdbUrl" target="_blank" rel="noreferrer">TMDb</a>
                <button
                  type="button"
                  class="mini-button"
                  :disabled="importingKey === candidateKey(item)"
                  @click="importCandidate(item)"
                >
                  {{ importingKey === candidateKey(item) ? 'Salvando...' : 'Adicionar' }}
                </button>
              </div>
            </div>
            <button
              v-if="!candidateQuery.trim() && group.items.length > (visibleCounts[group.id] ?? 12)"
              type="button"
              class="secondary-button load-more"
              @click="showMore(group.id)"
            >
              Carregar mais
            </button>
          </section>
          <p v-if="candidates.candidateCount && !filteredGroups.length" class="empty-copy">Nenhuma pessoa corresponde à busca.</p>
          <p v-if="!candidates.candidateCount" class="empty-copy">Não restaram pessoas do TMDb para adicionar.</p>
        </template>
      </div>

      <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import type {
  ShowCreditsSyncResponse,
  ShowPageData,
  ShowTmdbCreditCandidate,
  ShowTmdbCreditCandidatesResponse,
} from '~/types/shows'
import type { CreditBatchImportResponse } from '~/types/movies'

const props = defineProps<{
  showId: number
  people: ShowPageData['people']
  editing: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const syncing = ref(false)
const feedback = ref<string | null>(null)
const candidatesVisible = ref(false)
const loadingCandidates = ref(false)
const candidates = ref<ShowTmdbCreditCandidatesResponse | null>(null)
const importingKey = ref<string | null>(null)
const removingKey = ref<string | null>(null)
const visibleCounts = reactive<Record<string, number>>({})
const candidateQuery = ref('')
const selectedKeys = ref<string[]>([])
const importingBatch = ref(false)
const filteredGroups = computed(() => {
  const query = candidateQuery.value.trim().toLocaleLowerCase('pt-BR')
  return (candidates.value?.groups ?? [])
    .map((group) => ({
      ...group,
      items: group.items
        .filter((item) => !query || `${item.name} ${item.roleLabel}`.toLocaleLowerCase('pt-BR').includes(query))
        .sort((left, right) => left.name.localeCompare(right.name, 'pt-BR', { sensitivity: 'base' })),
    }))
    .filter((group) => group.items.length)
})

async function syncFromTmdb() {
  if (props.people.curated && !window.confirm('Restaurar o recorte do TMDb e substituir sua curadoria manual?')) return
  syncing.value = true
  feedback.value = null

  try {
    const response = await $fetch<ShowCreditsSyncResponse>(`/api/admin/shows/${props.showId}/credits/sync-tmdb`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
    feedback.value = `${response.syncedCount} créditos locais atualizados a partir do TMDb.`
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível sincronizar os créditos do TMDb.'
  } finally {
    syncing.value = false
  }
}

function candidateKey(item: ShowTmdbCreditCandidate) {
  return [item.personTmdbId, item.creditType, item.job ?? '', item.characterName ?? ''].join('|')
}

function visibleCandidates(group: ShowTmdbCreditCandidatesResponse['groups'][number]) {
  return candidateQuery.value.trim() ? group.items : group.items.slice(0, visibleCounts[group.id] ?? 12)
}

function toggleCandidate(item: ShowTmdbCreditCandidate) {
  const key = candidateKey(item)
  selectedKeys.value = selectedKeys.value.includes(key)
    ? selectedKeys.value.filter((candidate) => candidate !== key)
    : [...selectedKeys.value, key]
}

async function toggleCandidates() {
  if (candidatesVisible.value) {
    candidatesVisible.value = false
    return
  }
  candidatesVisible.value = true
  loadingCandidates.value = true
  try {
    candidates.value = await $fetch<ShowTmdbCreditCandidatesResponse>(
      `/api/shows/${props.showId}/credits/tmdb-candidates`,
      {
        baseURL: config.public.apiBase,
        method: 'POST',
      },
    )
  } catch {
    feedback.value = 'Não foi possível buscar pessoas no TMDb.'
  } finally {
    loadingCandidates.value = false
  }
}

async function importCandidate(item: ShowTmdbCreditCandidate) {
  importingKey.value = candidateKey(item)
  try {
    await $fetch(`/api/shows/${props.showId}/credits/from-tmdb`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: item,
    })
    feedback.value = `${item.name} foi adicionado aos créditos.`
    emit('changed')
    await fetchCandidates()
  } catch {
    feedback.value = `Não foi possível adicionar ${item.name}.`
  } finally {
    importingKey.value = null
  }
}

async function fetchCandidates() {
  candidates.value = await $fetch<ShowTmdbCreditCandidatesResponse>(
    `/api/shows/${props.showId}/credits/tmdb-candidates`,
    { baseURL: config.public.apiBase, method: 'POST' },
  )
  const available = new Set(candidates.value.groups.flatMap((group) => group.items.map(candidateKey)))
  selectedKeys.value = selectedKeys.value.filter((key) => available.has(key))
}

async function importSelected() {
  const selected = (candidates.value?.groups ?? [])
    .flatMap((group) => group.items)
    .filter((item) => selectedKeys.value.includes(candidateKey(item)))
  if (!selected.length) return
  importingBatch.value = true
  try {
    const response = await $fetch<CreditBatchImportResponse>(`/api/shows/${props.showId}/credits/from-tmdb/batch`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { items: selected },
    })
    selectedKeys.value = response.results.filter((item) => !item.success).map((item) => item.key)
    feedback.value = response.failed
      ? `${response.succeeded} adicionados; ${response.failed} não puderam ser adicionados.`
      : `${response.succeeded} pessoas adicionadas.`
    emit('changed')
    await fetchCandidates()
  } catch {
    feedback.value = 'Não foi possível adicionar as pessoas selecionadas.'
  } finally {
    importingBatch.value = false
  }
}

function categoryForGroup(groupId: string) {
  return groupId === 'directors' ? 'DIRECTING' : groupId === 'writers' ? 'WRITING' : 'CAST'
}

async function removeCredit(groupId: string, personId: number, name: string) {
  removingKey.value = `${groupId}:${personId}`
  try {
    await $fetch(`/api/shows/${props.showId}/people/${personId}`, {
      baseURL: config.public.apiBase,
      method: 'DELETE',
      query: { category: categoryForGroup(groupId) },
    })
    feedback.value = `${name} foi removido deste grupo.`
    emit('changed')
  } catch {
    feedback.value = `Não foi possível remover ${name}.`
  } finally {
    removingKey.value = null
  }
}

function showMore(groupId: string) {
  visibleCounts[groupId] = (visibleCounts[groupId] ?? 12) + 12
}
</script>

<style scoped>
.people-card {
  display: grid;
  gap: 18px;
  padding: 22px 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.people-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: start;
}

.people-copy,
.people-groups,
.group-row,
.person-copy {
  display: grid;
  gap: 10px;
}

.eyebrow,
.group-label {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h2,
.empty-copy {
  margin: 0;
}

.people-copy h2 {
  font-size: 1.4rem;
  line-height: 1;
  letter-spacing: -0.04em;
}

.summary-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 76%, white);
  color: var(--base-color-text-primary);
  font-size: 0.78rem;
}

.head-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.curation-badge {
  padding: 7px 10px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--base-color-brand-red) 9%, white);
  color: var(--base-color-text-primary);
  font-size: 0.74rem;
  font-weight: 700;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.person-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 8px 12px 8px 8px;
  border-radius: 18px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
}

.person-avatar {
  width: 40px;
  height: 40px;
  overflow: hidden;
  border-radius: 50%;
  background: linear-gradient(160deg, rgba(230, 0, 35, 0.1), rgba(33, 25, 34, 0.06)), var(--base-color-surface-soft);
  flex-shrink: 0;
}

.person-avatar img,
.avatar-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 1rem;
  font-weight: 700;
}

.person-copy {
  gap: 2px;
  min-width: 0;
}

.person-name {
  margin: 0;
  font-weight: 700;
  line-height: 1.1;
}

.person-role {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
  line-height: 1.2;
}

.empty-copy {
  color: var(--base-color-text-secondary);
}

.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 4px;
}

.candidate-panel,
.candidate-list {
  display: grid;
  gap: 10px;
}

.candidate-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.candidate-toolbar input {
  min-width: min(100%, 280px);
  padding: 10px 14px;
  border: 1px solid var(--base-color-border, #91918c);
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
  font: inherit;
}

.tmdb-person-link {
  color: var(--base-color-text-secondary);
  font-size: 0.76rem;
  font-weight: 700;
}

.candidate-card,
.candidate-identity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.candidate-identity {
  min-width: 0;
}

.candidate-card {
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 18px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
}

.person-name {
  color: inherit;
  text-decoration: none;
}

.mini-button,
.remove-button {
  border: 0;
  cursor: pointer;
}

.mini-button {
  padding: 9px 12px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--base-color-brand-red) 10%, white);
}

.remove-button {
  display: grid;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  place-items: center;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-secondary);
}

.remove-button:hover {
  color: var(--base-color-brand-red);
}

.load-more {
  justify-self: start;
}

.secondary-button {
  padding: 10px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
}

.secondary-button:hover {
  background: color-mix(in srgb, var(--base-color-surface-warm) 80%, white);
}

.secondary-button:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}

.secondary-button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.feedback {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}

@media (max-width: 900px) {
  .people-head {
    display: grid;
  }

  .head-meta {
    justify-content: flex-start;
  }
}

@media (max-width: 560px) {
  .candidate-card {
    align-items: stretch;
    flex-direction: column;
  }

  .mini-button {
    align-self: flex-start;
  }
}
</style>
