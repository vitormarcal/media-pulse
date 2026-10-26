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
  } catch {
    error.value = 'Não foi possível entrar. Confira a senha ou aguarde um minuto antes de tentar novamente.'
  } finally {
    busy.value = false
  }
}
</script>
