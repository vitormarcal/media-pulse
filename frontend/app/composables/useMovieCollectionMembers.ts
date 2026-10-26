import type { MovieCollectionMembersResponse } from '~/types/movies'

export function useMovieCollectionMembers(collectionId: MaybeRefOrGetter<string | null>) {
  const config = useRuntimeConfig()
  const owner = useOwnerAuth()
  const membersResponse = ref<MovieCollectionMembersResponse | null>(null)
  const loadingMembers = ref(false)
  const membersError = ref<string | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined
  let polls = 0
  let disposed = false

  const canRetry = computed(() => !!membersError.value || membersResponse.value?.syncState === 'FAILED')
  const syncMessage = computed(() => {
    if (canRetry.value) return 'Não foi possível carregar os filmes da coleção.'
    if (!membersResponse.value || membersResponse.value.syncState === 'PENDING') {
      return 'Carregando filmes da coleção…'
    }
    return membersResponse.value.members.length ? null : 'Esta coleção não possui filmes informados.'
  })

  async function loadMembers(retry = false) {
    const id = toValue(collectionId)
    if (!id || loadingMembers.value || disposed) return
    clearTimeout(timer)
    loadingMembers.value = true
    membersError.value = null
    try {
      const response = await $fetch<MovieCollectionMembersResponse>(
        retry ? `/api/admin/movies/collections/${id}/tmdb-members` : `/api/movies/collections/${id}`,
        { baseURL: config.public.apiBase, method: retry ? 'POST' : 'GET' },
      )
      if (disposed || id !== toValue(collectionId)) return
      membersResponse.value = response
      if (response.syncState === 'PENDING' && polls < 36) {
        polls++
        timer = setTimeout(() => void loadMembers(), 5000)
      }
    } catch {
      if (!disposed && id === toValue(collectionId))
        membersError.value = 'Não foi possível carregar os filmes da coleção.'
    } finally {
      if (id === toValue(collectionId)) loadingMembers.value = false
    }
  }

  function retryMembers() {
    polls = 0
    return loadMembers(owner.value.authenticated && membersResponse.value?.syncState === 'FAILED')
  }

  watch(
    () => toValue(collectionId),
    () => {
      clearTimeout(timer)
      membersResponse.value = null
      membersError.value = null
      loadingMembers.value = false
      polls = 0
      void loadMembers()
    },
    { immediate: true },
  )

  onScopeDispose(() => {
    disposed = true
    clearTimeout(timer)
  })

  return { membersResponse, loadingMembers, membersError, canRetry, syncMessage, loadMembers, retryMembers }
}
