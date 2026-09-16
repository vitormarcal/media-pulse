<template>
  <article v-if="people.visibleCount || editingEnabled" class="people-card">
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
              v-if="editingEnabled"
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

      <p v-if="!people.groups.length" class="empty-copy">
        {{
          enrichmentStatus?.status === 'PENDING'
            ? 'Completando créditos…'
            : enrichmentStatus?.status === 'RETRY_SCHEDULED'
              ? 'Não foi possível atualizar os créditos. Tentaremos novamente mais tarde.'
              : 'Nenhum crédito disponível.'
        }}
      </p>

      <div v-if="editingEnabled" class="editor-shell">
        <div class="editor-actions">
          <button type="button" class="secondary-button" :disabled="syncing" @click="syncFromTmdb">
            {{ syncing ? 'Sincronizando...' : 'Sincronizar base TMDb' }}
          </button>

          <button type="button" class="ghost-button" :disabled="loadingTmdbCandidates" @click="toggleTmdbCandidates">
            {{
              tmdbCandidatesVisible
                ? 'Ocultar extras do TMDb'
                : loadingTmdbCandidates
                  ? 'Buscando extras...'
                  : 'Trazer mais do TMDb'
            }}
          </button>
        </div>

        <div v-if="tmdbCandidatesVisible" class="tmdb-panel">
          <p v-if="loadingTmdbCandidates" class="tmdb-state">Buscando créditos extras no TMDb...</p>

          <template v-else-if="tmdbCandidates">
            <div class="candidate-toolbar">
              <input v-model="candidateQuery" type="search" placeholder="Buscar por nome ou função" />
              <button
                v-if="selectedCandidateKeys.length"
                type="button"
                class="mini-button"
                :disabled="importingBatch"
                @click="importSelected"
              >
                {{ importingBatch ? 'Adicionando...' : `Adicionar selecionados (${selectedCandidateKeys.length})` }}
              </button>
            </div>
            <div v-if="tmdbCandidates.candidateCount" class="tmdb-groups">
              <section v-for="group in filteredCandidateGroups" :key="group.id" class="tmdb-group">
                <p class="group-label">{{ group.title }}</p>

                <div class="candidate-list">
                  <div v-for="item in visibleCandidates(group)" :key="candidateKey(item)" class="candidate-card">
                    <input
                      type="checkbox"
                      :checked="selectedCandidateKeys.includes(candidateKey(item))"
                      :aria-label="`Selecionar ${item.name}`"
                      @change="toggleCandidate(item)"
                    />
                    <div class="candidate-identity">
                      <div class="person-avatar candidate-avatar">
                        <img
                          v-if="resolveMediaUrl(item.profileUrl)"
                          :src="resolveMediaUrl(item.profileUrl)"
                          :alt="item.name"
                        />
                        <div v-else class="avatar-fallback">{{ item.name.slice(0, 1) }}</div>
                      </div>

                      <div class="candidate-copy">
                        <span class="person-name">{{ item.name }}</span>
                        <small class="candidate-meta">{{ item.roleLabel }}</small>
                      </div>
                    </div>

                    <a class="tmdb-person-link" :href="item.tmdbUrl" target="_blank" rel="noreferrer">TMDb</a>

                    <button
                      type="button"
                      class="mini-button"
                      :disabled="importingCandidateKey === candidateKey(item)"
                      @click="importCandidate(item)"
                    >
                      {{ importingCandidateKey === candidateKey(item) ? 'Salvando...' : 'Adicionar' }}
                    </button>
                  </div>
                </div>
                <button
                  v-if="!candidateQuery.trim() && group.items.length > (visibleCandidateCounts[group.id] ?? 12)"
                  type="button"
                  class="ghost-button load-more"
                  @click="showMore(group.id)"
                >
                  Carregar mais
                </button>
              </section>
              <p v-if="!filteredCandidateGroups.length" class="tmdb-state">Nenhuma pessoa corresponde à busca.</p>
            </div>

            <p v-else class="tmdb-state">Não restaram pessoas extras do TMDb para escolher neste filme.</p>
          </template>
        </div>

        <div class="link-panel">
          <div class="editor-toolbar">
            <label class="field field-search">
              <span>Vincular pessoa já existente</span>
              <input v-model="searchQuery" type="text" placeholder="Ex.: Tarantino, Isabelle Huppert, Willem Dafoe" />
            </label>

            <label class="field field-group">
              <span>Grupo</span>
              <select v-model="linkGroup">
                <option value="DIRECTORS">Direção</option>
                <option value="WRITERS">Roteiro</option>
                <option value="CAST">Elenco</option>
              </select>
            </label>

            <label v-if="requiresRoleLabel" class="field field-role">
              <span>{{ linkGroup === 'CAST' ? 'Personagem' : 'Rótulo' }}</span>
              <input
                v-model="linkRoleLabel"
                type="text"
                :placeholder="linkGroup === 'CAST' ? 'Ex.: Drácula' : 'Ex.: Montagem'"
              />
            </label>
          </div>

          <div v-if="shouldShowSuggestions" class="suggestions-panel">
            <p v-if="searching" class="suggestions-state">Procurando pessoas já existentes...</p>

            <div v-else-if="suggestions.length" class="suggestions-list">
              <button
                v-for="item in suggestions"
                :key="item.personId"
                type="button"
                class="suggestion-item"
                :disabled="linkingPersonId === item.personId"
                @click="linkPerson(item)"
              >
                <div class="suggestion-avatar">
                  <img
                    v-if="resolveMediaUrl(item.profileUrl)"
                    :src="resolveMediaUrl(item.profileUrl)"
                    :alt="item.name"
                  />
                  <div v-else class="avatar-fallback">{{ item.name.slice(0, 1) }}</div>
                </div>

                <div class="suggestion-copy">
                  <span class="suggestion-name">{{ item.name }}</span>
                  <small class="suggestion-meta">{{ suggestionMeta(item) }}</small>
                </div>
              </button>
            </div>

            <p v-else class="suggestions-state">Nenhuma pessoa existente apareceu para essa busca.</p>
          </div>
        </div>
      </div>

      <p v-if="feedback" class="feedback">{{ feedback }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import type {
  MovieCreditsSyncResponse,
  MoviePageData,
  PersonCreditDto,
  PersonLinkRequest,
  PersonSuggestionDto,
  MovieTmdbCreditCandidate,
  MovieTmdbCreditCandidatesResponse,
  MovieTmdbCreditImportRequest,
  CreditBatchImportResponse,
} from '~/types/movies'

const props = defineProps<{
  movieId: number
  people: MoviePageData['people']
  editing?: boolean
  enrichmentStatus?: MoviePageData['enrichment']['credits']
}>()

const emit = defineEmits<{
  changed: []
}>()

const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const syncing = ref(false)
const searching = ref(false)
const loadingTmdbCandidates = ref(false)
const tmdbCandidatesVisible = ref(false)
const tmdbCandidates = ref<MovieTmdbCreditCandidatesResponse | null>(null)
const linkingPersonId = ref<number | null>(null)
const importingCandidateKey = ref<string | null>(null)
const removingKey = ref<string | null>(null)
const visibleCandidateCounts = reactive<Record<string, number>>({})
const feedback = ref<string | null>(null)
const candidateQuery = ref('')
const selectedCandidateKeys = ref<string[]>([])
const importingBatch = ref(false)
const searchQuery = ref('')
const linkGroup = ref<'DIRECTORS' | 'WRITERS' | 'CAST'>('DIRECTORS')
const linkRoleLabel = ref('')
const suggestions = ref<PersonSuggestionDto[]>([])
const editingEnabled = computed(() => props.editing ?? false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

const normalizedSearchQuery = computed(() => searchQuery.value.trim().replace(/\s+/g, ' ').toLowerCase())
const shouldShowSuggestions = computed(() => editingEnabled.value && normalizedSearchQuery.value.length >= 2)
const requiresRoleLabel = computed(() => linkGroup.value === 'CAST')
const filteredCandidateGroups = computed(() => {
  const query = candidateQuery.value.trim().toLocaleLowerCase('pt-BR')
  return (tmdbCandidates.value?.groups ?? [])
    .map((group) => ({
      ...group,
      items: group.items
        .filter((item) => !query || `${item.name} ${item.roleLabel}`.toLocaleLowerCase('pt-BR').includes(query))
        .sort((left, right) => left.name.localeCompare(right.name, 'pt-BR', { sensitivity: 'base' })),
    }))
    .filter((group) => group.items.length)
})

const assignedPeople = computed(() => {
  const groups = props.people.groups
  return new Map(groups.flatMap((group) => group.items.map((item) => [`${group.id}:${item.personId}`, item] as const)))
})

async function syncFromTmdb() {
  if (props.people.curated && !window.confirm('Restaurar o recorte do TMDb e substituir sua curadoria manual?')) return
  syncing.value = true
  feedback.value = null

  try {
    const response = await $fetch<MovieCreditsSyncResponse>(`/api/admin/movies/${props.movieId}/credits/sync-tmdb`, {
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

async function fetchTmdbCandidates() {
  loadingTmdbCandidates.value = true

  try {
    tmdbCandidates.value = await $fetch<MovieTmdbCreditCandidatesResponse>(
      `/api/movies/${props.movieId}/credits/tmdb-candidates`,
      {
        baseURL: config.public.apiBase,
        method: 'POST',
      },
    )
    const available = new Set(tmdbCandidates.value.groups.flatMap((group) => group.items.map(candidateKey)))
    selectedCandidateKeys.value = selectedCandidateKeys.value.filter((key) => available.has(key))
  } catch {
    feedback.value = 'Não foi possível buscar pessoas extras do TMDb.'
    tmdbCandidates.value = null
  } finally {
    loadingTmdbCandidates.value = false
  }
}

function showMore(groupId: string) {
  visibleCandidateCounts[groupId] = (visibleCandidateCounts[groupId] ?? 12) + 12
}

function categoryForGroup(groupId: string) {
  if (groupId === 'directors') return 'DIRECTING'
  if (groupId === 'writers') return 'WRITING'
  return 'CAST'
}

async function removeCredit(groupId: string, personId: number, name: string) {
  const key = `${groupId}:${personId}`
  removingKey.value = key
  try {
    await $fetch(`/api/movies/${props.movieId}/people/${personId}`, {
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

async function toggleTmdbCandidates() {
  if (tmdbCandidatesVisible.value) {
    tmdbCandidatesVisible.value = false
    return
  }

  tmdbCandidatesVisible.value = true
  await fetchTmdbCandidates()
}

function buildImportRequest(item: MovieTmdbCreditCandidate): MovieTmdbCreditImportRequest {
  return {
    personTmdbId: item.personTmdbId,
    creditType: item.creditType,
    department: item.department,
    job: item.job,
    characterName: item.characterName,
    billingOrder: item.billingOrder,
  }
}

function candidateKey(item: MovieTmdbCreditCandidate) {
  return [item.personTmdbId, item.creditType, item.job || '', item.characterName || ''].join('|')
}

function visibleCandidates(group: MovieTmdbCreditCandidatesResponse['groups'][number]) {
  return candidateQuery.value.trim() ? group.items : group.items.slice(0, visibleCandidateCounts[group.id] ?? 12)
}

function toggleCandidate(item: MovieTmdbCreditCandidate) {
  const key = candidateKey(item)
  selectedCandidateKeys.value = selectedCandidateKeys.value.includes(key)
    ? selectedCandidateKeys.value.filter((candidate) => candidate !== key)
    : [...selectedCandidateKeys.value, key]
}

async function importCandidate(item: MovieTmdbCreditCandidate) {
  const key = candidateKey(item)
  importingCandidateKey.value = key
  feedback.value = null

  try {
    await $fetch<PersonCreditDto>(`/api/movies/${props.movieId}/credits/from-tmdb`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: buildImportRequest(item),
    })
    feedback.value = `${item.name} foi incorporado aos créditos deste filme.`
    emit('changed')
    await fetchTmdbCandidates()
  } catch {
    feedback.value = `Não foi possível adicionar ${item.name} a partir do TMDb.`
  } finally {
    importingCandidateKey.value = null
  }
}

async function importSelected() {
  const selected = (tmdbCandidates.value?.groups ?? [])
    .flatMap((group) => group.items)
    .filter((item) => selectedCandidateKeys.value.includes(candidateKey(item)))
  if (!selected.length) return
  importingBatch.value = true
  try {
    const response = await $fetch<CreditBatchImportResponse>(`/api/movies/${props.movieId}/credits/from-tmdb/batch`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { items: selected.map(buildImportRequest) },
    })
    selectedCandidateKeys.value = response.results.filter((item) => !item.success).map((item) => item.key)
    feedback.value = response.failed
      ? `${response.succeeded} adicionados; ${response.failed} não puderam ser adicionados.`
      : `${response.succeeded} pessoas adicionadas.`
    emit('changed')
    await fetchTmdbCandidates()
  } catch {
    feedback.value = 'Não foi possível adicionar as pessoas selecionadas.'
  } finally {
    importingBatch.value = false
  }
}

async function fetchSuggestions() {
  if (!shouldShowSuggestions.value) {
    suggestions.value = []
    return
  }

  searching.value = true

  try {
    suggestions.value = await $fetch<PersonSuggestionDto[]>(`/api/people/search`, {
      baseURL: config.public.apiBase,
      query: {
        q: searchQuery.value.trim(),
        limit: 6,
      },
    })
  } catch {
    suggestions.value = []
  } finally {
    searching.value = false
  }
}

function scheduleSuggestionsRefresh() {
  if (searchTimer) clearTimeout(searchTimer)

  if (!shouldShowSuggestions.value) {
    suggestions.value = []
    searching.value = false
    return
  }

  searchTimer = setTimeout(() => {
    void fetchSuggestions()
  }, 180)
}

function normalizeGroupForLookup(group: string) {
  if (group === 'DIRECTORS') return 'directors'
  if (group === 'WRITERS') return 'writers'
  if (group === 'CAST') return 'cast'
  return 'directors'
}

function suggestionMeta(item: PersonSuggestionDto) {
  const assigned = assignedPeople.value.get(`${normalizeGroupForLookup(linkGroup.value)}:${item.personId}`)
  if (assigned) {
    return 'Já neste grupo'
  }

  return item.roles.length ? item.roles.slice(0, 2).join(' · ') : 'Pessoa já presente na base'
}

function buildLinkRequest(item: PersonSuggestionDto): PersonLinkRequest {
  return {
    personId: item.personId,
    group: linkGroup.value,
    roleLabel: linkRoleLabel.value.trim() || null,
  }
}

async function linkPerson(item: PersonSuggestionDto) {
  if (linkingPersonId.value) return
  if (requiresRoleLabel.value && !linkRoleLabel.value.trim()) {
    feedback.value = 'Informe o personagem para o vínculo manual.'
    return
  }

  linkingPersonId.value = item.personId
  feedback.value = null

  try {
    await $fetch<PersonCreditDto>(`/api/movies/${props.movieId}/people`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: buildLinkRequest(item),
    })
    feedback.value = `${item.name} foi vinculado a este filme.`
    searchQuery.value = ''
    linkRoleLabel.value = ''
    suggestions.value = []
    emit('changed')
  } catch {
    feedback.value = `Não foi possível vincular ${item.name} a este filme.`
  } finally {
    linkingPersonId.value = null
  }
}

watch([searchQuery, linkGroup, editingEnabled], () => {
  feedback.value = null
  scheduleSuggestionsRefresh()
})

watch(editingEnabled, (enabled) => {
  if (!enabled) {
    tmdbCandidatesVisible.value = false
    tmdbCandidates.value = null
  }
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
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
.person-copy,
.link-panel,
.suggestions-list,
.suggestion-copy,
.tmdb-panel,
.tmdb-group,
.editor-shell {
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
.empty-copy,
.feedback,
.tmdb-state {
  margin: 0;
}

h2 {
  font-size: 1.4rem;
  line-height: 1;
  letter-spacing: -0.04em;
}

.head-meta,
.editor-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.head-meta {
  justify-content: flex-end;
}

.summary-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 76%, white);
  color: var(--base-color-text-primary);
  font-size: 0.78rem;
}

.curation-badge {
  padding: 7px 10px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--base-color-brand-red) 9%, white);
  color: var(--base-color-text-primary);
  font-size: 0.74rem;
  font-weight: 700;
}

.secondary-button,
.ghost-button,
.mini-button {
  border: none;
  cursor: pointer;
  font: inherit;
}

.secondary-button,
.ghost-button {
  padding: 10px 14px;
  border-radius: 16px;
}

.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.ghost-button {
  background: rgba(255, 255, 255, 0.62);
  color: var(--base-color-text-secondary);
}

.mini-button {
  padding: 9px 12px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--base-color-brand-red) 10%, white);
  color: var(--base-color-text-primary);
  white-space: nowrap;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.person-pill,
.candidate-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 54px;
  padding: 8px 12px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.84);
  color: #211922;
}

.person-name {
  color: inherit;
  text-decoration: none;
}

.remove-button {
  display: grid;
  width: 28px;
  height: 28px;
  margin-left: 2px;
  border: 0;
  border-radius: 50%;
  place-items: center;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-secondary);
  cursor: pointer;
}

.remove-button:hover {
  color: var(--base-color-brand-red);
}

.load-more {
  justify-self: start;
}

.candidate-card {
  justify-content: space-between;
  width: 100%;
}

.candidate-identity {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
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

.candidate-list {
  display: grid;
  gap: 8px;
}

.person-avatar,
.suggestion-avatar {
  width: 38px;
  height: 38px;
  overflow: hidden;
  border-radius: 50%;
  background: color-mix(in srgb, var(--base-color-surface-warm) 82%, white);
  flex: 0 0 38px;
}

.candidate-avatar {
  width: 34px;
  height: 34px;
  flex-basis: 34px;
}

.person-avatar img,
.suggestion-avatar img,
.avatar-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 0.92rem;
  font-weight: 700;
}

.person-copy {
  gap: 3px;
}

.person-name,
.suggestion-name {
  font-size: 0.92rem;
  font-weight: 600;
}

.person-role,
.candidate-meta,
.empty-copy,
.feedback,
.tmdb-state,
.suggestion-meta,
.suggestions-state {
  color: var(--base-color-text-secondary);
  font-size: 0.74rem;
}

.editor-shell {
  gap: 14px;
  padding-top: 6px;
}

.tmdb-panel,
.suggestions-panel {
  padding: 12px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 44%, white);
}

.editor-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(10rem, 0.55fr) minmax(0, 0.7fr);
  gap: 12px;
  align-items: end;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.field input,
.field select {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
}

.suggestion-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.suggestion-copy {
  gap: 2px;
}

.suggestion-item:disabled,
.secondary-button:disabled,
.ghost-button:disabled,
.mini-button:disabled {
  opacity: 0.6;
  cursor: default;
}

@media (max-width: 900px) {
  .people-head {
    grid-template-columns: 1fr;
    display: grid;
  }

  .head-meta {
    justify-content: flex-start;
  }

  .editor-toolbar {
    grid-template-columns: 1fr;
  }

  .candidate-card {
    align-items: start;
    flex-direction: column;
  }
}
</style>
