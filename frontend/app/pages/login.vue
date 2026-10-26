<template>
  <main class="owner-access">
    <h1>Entrar</h1>
    <form @submit.prevent="submit">
      <label for="owner-password">Senha</label>
      <input id="owner-password" v-model="password" type="password" autocomplete="current-password" required />
      <button :disabled="busy">{{ busy ? 'Entrando…' : 'Entrar' }}</button>
      <p v-if="error" role="alert">{{ error }}</p>
    </form>
    <NuxtLink to="/auth/reset">Criar ou redefinir senha</NuxtLink>
  </main>
</template>

<script setup lang="ts">
import { FetchError } from 'ofetch'
import type { OwnerSession } from '~/composables/useOwnerAuth'

const owner = useOwnerAuth()
const config = useRuntimeConfig()
const password = ref('')
const busy = ref(false)
const error = ref('')
async function submit() {
  busy.value = true
  error.value = ''
  try {
    await $fetch('/api/auth/login', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { password: password.value },
    })
    password.value = ''
    owner.value = await $fetch<OwnerSession>('/api/auth/session', { baseURL: config.public.apiBase })
    await navigateTo('/')
  } catch (cause) {
    const status = cause instanceof FetchError ? cause.statusCode : undefined
    const messages: Record<number, string> = {
      401: 'Senha inválida.',
      403: 'A sessão ou a origem da requisição foi recusada. Recarregue a página; se persistir, confira HTTPS, cookies e proxy.',
      429: 'Muitas tentativas deste endereço. Aguarde um minuto antes de tentar novamente.',
    }
    error.value = status
      ? (messages[status] ?? 'O servidor não conseguiu concluir o login.')
      : 'Não foi possível conectar ao serviço. Confira a conexão e a configuração de HTTPS e CORS.'
  } finally {
    busy.value = false
  }
}
</script>
