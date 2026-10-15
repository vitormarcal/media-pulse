import type { MusicSearchResponse } from '~/types/music'
import type { AlbumCreditBackfillBatch, BackfillSearchAlbum } from '~/types/musicBackfill'

const endpoint = '/api/admin/music/album-artist-backfills'

export function useAlbumCreditBackfill() {
  const config = useRuntimeConfig()
  const mode = ref<'automatic' | 'manual'>('automatic')
  const limit = ref(1)
  const selectedAlbums = ref<BackfillSearchAlbum[]>([])
  const searchText = ref('')
  const searchResults = ref<BackfillSearchAlbum[]>([])
  const searching = ref(false)
  const searchError = ref('')
  const recent = ref<AlbumCreditBackfillBatch[]>([])
  const batch = ref<AlbumCreditBackfillBatch | null>(null)
  const busy = ref<'history' | 'preview' | 'load' | 'apply' | 'undo' | null>(null)
  const error = ref('')
  const notice = ref('')
  const historyError = ref('')
  let timer: ReturnType<typeof setTimeout> | undefined
  let searchSequence = 0

  const readyCount = computed(
    () =>
      batch.value?.items.filter(
        (item) => item.status === 'READY' || (item.status === 'ERROR' && item.proposed.length > 0),
      ).length ?? 0,
  )
  const appliedCount = computed(() => batch.value?.items.filter((item) => item.status === 'APPLIED').length ?? 0)
  const pendingIds = computed(
    () =>
      batch.value?.items
        .filter(
          (item) =>
            item.status === 'PENDING' ||
            item.status === 'CONFLICT' ||
            (item.status === 'ERROR' && !item.proposed.length),
        )
        .map((item) => item.albumId) ?? [],
  )
  const selectedIds = computed(() => new Set(selectedAlbums.value.map((album) => album.id)))

  async function run(action: NonNullable<typeof busy.value>, failureMessage: string, work: () => Promise<void>) {
    if (busy.value) return false
    busy.value = action
    error.value = ''
    notice.value = ''
    try {
      await work()
      return true
    } catch {
      error.value = failureMessage
      return false
    } finally {
      busy.value = null
    }
  }

  async function refreshRecent() {
    try {
      recent.value = await $fetch<AlbumCreditBackfillBatch[]>(endpoint, { baseURL: config.public.apiBase })
      historyError.value = ''
    } catch {
      historyError.value = 'Não foi possível atualizar os lotes recentes. O lote aberto continua disponível.'
    }
  }

  async function loadRecent() {
    return run('history', 'Não foi possível carregar os lotes.', async () => {
      await refreshRecent()
      if (!batch.value) batch.value = recent.value[0] ?? null
    })
  }

  async function keepResult(result: AlbumCreditBackfillBatch) {
    batch.value = result
    recent.value = [result, ...recent.value.filter((item) => item.batchId !== result.batchId)].slice(0, 20)
    await refreshRecent()
  }

  async function preview(albumIds?: number[]) {
    const ids = albumIds ?? (mode.value === 'manual' ? selectedAlbums.value.map((album) => album.id) : [])
    const batchLimit = ids.length ? 1 : limit.value
    if (
      (!ids.length && mode.value === 'manual' && !albumIds) ||
      !Number.isInteger(batchLimit) ||
      batchLimit < 1 ||
      batchLimit > 10
    ) {
      error.value = 'Escolha ao menos um álbum ou uma quantidade entre 1 e 10.'
      return false
    }
    return run(
      'preview',
      'Não foi possível concluir a prévia. Atualize os lotes recentes para verificar se ela foi registrada.',
      async () => {
        const result = await $fetch<AlbumCreditBackfillBatch>(`${endpoint}/preview`, {
          baseURL: config.public.apiBase,
          method: 'POST',
          body: { albumIds: ids, limit: batchLimit },
        })
        await keepResult(result)
        notice.value = result.items.length
          ? 'Prévia registrada. Confira os créditos antes de aplicar.'
          : 'Nenhum álbum elegível encontrado.'
      },
    )
  }

  async function openBatch(batchId: string) {
    return run('load', 'Não foi possível abrir o lote. Tente novamente.', async () => {
      const result = await $fetch<AlbumCreditBackfillBatch>(`${endpoint}/${batchId}`, {
        baseURL: config.public.apiBase,
      })
      batch.value = result
      recent.value = recent.value.map((item) => (item.batchId === result.batchId ? result : item))
    })
  }

  async function apply() {
    const current = batch.value
    if (!current || !readyCount.value) return false
    return run(
      'apply',
      'Não foi possível obter o resultado da aplicação. Atualize o lote ou tente novamente.',
      async () => {
        const result = await $fetch<AlbumCreditBackfillBatch>(`${endpoint}/${current.batchId}/apply`, {
          baseURL: config.public.apiBase,
          method: 'POST',
        })
        await keepResult(result)
        notice.value = 'Aplicação concluída. Confira o resultado de cada álbum abaixo.'
      },
    )
  }

  async function undo() {
    const current = batch.value
    if (!current || !appliedCount.value) return false
    return run(
      'undo',
      'Não foi possível obter o resultado do desfazimento. Atualize o lote ou tente novamente.',
      async () => {
        const result = await $fetch<AlbumCreditBackfillBatch>(`${endpoint}/${current.batchId}/undo`, {
          baseURL: config.public.apiBase,
          method: 'POST',
        })
        await keepResult(result)
        notice.value = 'Pedido de desfazimento concluído. Confira quais álbuns foram restaurados.'
      },
    )
  }

  function toggleAlbum(album: BackfillSearchAlbum) {
    if (busy.value) return
    if (selectedIds.value.has(album.id))
      selectedAlbums.value = selectedAlbums.value.filter((item) => item.id !== album.id)
    else if (selectedAlbums.value.length < 10) selectedAlbums.value.push(album)
  }

  async function searchAlbums() {
    clearTimeout(timer)
    const sequence = ++searchSequence
    const q = searchText.value.trim()
    searchError.value = ''
    if (q.length < 2) {
      searchResults.value = []
      searching.value = false
      return
    }
    searching.value = true
    try {
      const result = await $fetch<MusicSearchResponse>('/api/music/search', {
        baseURL: config.public.apiBase,
        query: { q, limit: 20 },
      })
      if (sequence === searchSequence) searchResults.value = result.albums
    } catch {
      if (sequence === searchSequence) searchError.value = 'Não foi possível buscar álbuns. Tente novamente.'
    } finally {
      if (sequence === searchSequence) searching.value = false
    }
  }

  watch(searchText, () => {
    clearTimeout(timer)
    searchSequence++
    searchResults.value = []
    searching.value = mode.value === 'manual' && searchText.value.trim().length >= 2
    if (mode.value === 'manual') timer = setTimeout(searchAlbums, 350)
  })
  watch(mode, () => {
    clearTimeout(timer)
    searchSequence++
    searching.value = false
    if (mode.value === 'manual' && searchText.value.trim().length >= 2) void searchAlbums()
  })
  onScopeDispose(() => {
    clearTimeout(timer)
    searchSequence++
  })

  return {
    mode,
    limit,
    selectedAlbums,
    selectedIds,
    searchText,
    searchResults,
    searching,
    searchError,
    recent,
    batch,
    busy,
    error,
    notice,
    historyError,
    readyCount,
    appliedCount,
    pendingIds,
    loadRecent,
    preview,
    openBatch,
    apply,
    undo,
    toggleAlbum,
    searchAlbums,
  }
}
