export function useArtworkApi() {
  const config = useRuntimeConfig()
  const busy = ref(false)
  const error = ref('')
  const success = ref('')
  function request<T>(
    path: string,
    options: { method?: 'POST' | 'PUT' | 'DELETE'; body?: object | FormData; query?: Record<string, unknown> } = {},
  ) {
    return $fetch<T>(`/api/artworks${path}`, { baseURL: config.public.apiBase, ...options })
  }
  async function run(action: () => Promise<void>) {
    if (busy.value) return
    busy.value = true
    error.value = ''
    success.value = ''
    try {
      await action()
      success.value = 'Salvo.'
    } catch (cause: unknown) {
      const failure = cause as { data?: { detail?: string }; message?: string }
      error.value = failure.data?.detail || 'Não foi possível salvar. Confira os dados e tente novamente.'
    } finally {
      busy.value = false
    }
  }
  return { request, run, busy, error, success }
}
