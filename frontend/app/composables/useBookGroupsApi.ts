export function useBookGroupsApi() {
  const config = useRuntimeConfig()
  return <T>(path: string, method: 'GET' | 'POST' | 'PUT' | 'DELETE' = 'GET', body?: object) =>
    $fetch<T>(`/api/books${path}`, { baseURL: config.public.apiBase, method, body })
}
export function bookGroupError(cause: unknown) {
  const error = cause as { data?: { detail?: string; message?: string } }
  return error.data?.detail || error.data?.message || 'Não foi possível concluir. Tente novamente.'
}
