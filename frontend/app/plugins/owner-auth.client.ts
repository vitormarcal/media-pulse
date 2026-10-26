import type { OwnerSession } from '~/composables/useOwnerAuth'

export default defineNuxtPlugin(async (nuxtApp) => {
  const owner = useOwnerAuth()
  const config = useRuntimeConfig()
  const rawFetch = globalThis.$fetch
  const apiOrigin = new URL(String(config.public.apiBase), window.location.origin).origin
  const session = () =>
    rawFetch<OwnerSession>('/api/auth/session', {
      baseURL: config.public.apiBase,
      credentials: 'include',
    })
  try {
    owner.value = await session()
  } catch {
    owner.value = { authenticated: false, csrfToken: '' }
  }
  globalThis.$fetch = rawFetch.create({
    async onRequest({ request, options }) {
      const url = new URL(
        String(request),
        options.baseURL ? new URL(options.baseURL, window.location.origin) : window.location.origin,
      )
      if (url.origin !== apiOrigin || !url.pathname.startsWith('/api/')) return
      options.credentials = 'include'
      if (!['GET', 'HEAD', 'OPTIONS'].includes(String(options.method ?? 'GET').toUpperCase())) {
        if (!owner.value.csrfToken) owner.value = await session()
        options.headers.set('X-CSRF-Token', owner.value.csrfToken)
      }
    },
    onResponseError({ response }) {
      if (response.status === 401) owner.value.authenticated = false
      if (response.status === 403) owner.value = { authenticated: false, csrfToken: '' }
    },
  })
  const stops = new WeakMap<HTMLElement, () => void>()
  nuxtApp.vueApp.directive('owner', {
    beforeMount(el: HTMLElement) {
      stops.set(
        el,
        watchEffect(() => {
          el.toggleAttribute('data-owner-hidden', !owner.value.authenticated)
        }),
      )
    },
    unmounted(el: HTMLElement) {
      stops.get(el)?.()
      stops.delete(el)
    },
  })
})
