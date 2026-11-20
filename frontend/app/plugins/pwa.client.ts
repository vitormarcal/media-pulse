export default defineNuxtPlugin(() => {
  if (import.meta.dev || !window.isSecureContext || !('serviceWorker' in navigator)) {
    return
  }

  const register = () => {
    void navigator.serviceWorker.register('/sw.js', { scope: '/', updateViaCache: 'none' }).catch((error: unknown) => {
      console.warn('Não foi possível preparar o Media Pulse para falta de conexão.', error)
    })
  }

  if (document.readyState === 'complete') {
    register()
  } else {
    window.addEventListener('load', register, { once: true })
  }
})
