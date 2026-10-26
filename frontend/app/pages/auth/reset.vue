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
  } catch {
    message.value =
      'Não foi possível salvar. Confira o código, os limites da senha ou aguarde um minuto antes de tentar novamente.'
  } finally {
    busy.value = false
  }
}
</script>
