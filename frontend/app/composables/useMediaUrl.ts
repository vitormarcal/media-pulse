export function useMediaUrl() {
  const config = useRuntimeConfig()
  const requestUrl = useRequestURL()

  function resolveMediaUrl(value: string | null | undefined) {
    if (!value) return null

    if (/^https?:\/\//i.test(value)) {
      return value
    }

    const apiBase = String(config.public.apiBase || '').trim()
    const base = /^https?:\/\//i.test(apiBase) ? apiBase : requestUrl.origin
    return new URL(value, base).toString()
  }

  return {
    resolveMediaUrl,
  }
}
