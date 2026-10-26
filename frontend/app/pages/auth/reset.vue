<template>
  <main class="owner-access">
    <h1>Criar ou redefinir senha</h1>
    <form @submit.prevent="submit">
      <label for="reset-token">Código de autorização</label>
      <input id="reset-token" v-model="token" type="password" autocomplete="off" required />
      <label for="new-password">Nova senha</label>
      <input id="new-password" v-model="password" type="password" autocomplete="new-password" minlength="12" required />
      <p>Use pelo menos 12 caracteres e no máximo 72 bytes.</p>
      <label for="confirm-password">Confirmar senha</label>
      <input id="confirm-password" v-model="confirmation" type="password" autocomplete="new-password" required />
      <button :disabled="busy">{{ busy ? 'Salvando…' : 'Salvar senha' }}</button>
      <p v-if="message" role="status">{{ message }}</p>
    </form>
    <NuxtLink to="/login">Entrar</NuxtLink>
  </main>
</template>

<script setup lang="ts">
import { FetchError } from 'ofetch'

const owner = useOwnerAuth()
const config = useRuntimeConfig()
const token = ref('')
const password = ref('')
const confirmation = ref('')
const busy = ref(false)
const message = ref('')
async function submit() {
  if (password.value !== confirmation.value) {
    message.value = 'As senhas precisam ser iguais.'
    return
  }
  busy.value = true
  message.value = ''
  try {
    await $fetch('/api/auth/reset', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { token: token.value, password: password.value },
    })
    owner.value = { authenticated: false, csrfToken: '' }
    token.value = password.value = confirmation.value = ''
    message.value = 'Senha salva. Entre com a nova senha.'
  } catch (error) {
    const status = error instanceof FetchError ? error.statusCode : undefined
    const messages: Record<number, string> = {
      400: 'Use pelo menos 12 caracteres e no máximo 72 bytes na senha.',
      401: 'Código inválido, já utilizado ou indisponível. Confira AUTH_RESET_TOKEN no backend e reinicie após alterá-lo.',
      403: 'A sessão ou a origem da requisição foi recusada. Recarregue a página; se persistir, confira HTTPS, cookies e proxy.',
      429: 'Muitas tentativas deste endereço. Aguarde um minuto antes de tentar novamente.',
    }
    message.value = status
      ? (messages[status] ?? 'O servidor não conseguiu salvar a senha. Confira os logs do backend.')
      : 'Não foi possível conectar ao serviço. Confira a conexão e a configuração de HTTPS e CORS.'
  } finally {
    busy.value = false
  }
}
</script>
