<template>
  <div class="enrichment">
    <button type="button" :aria-expanded="opened" aria-controls="author-enrichment" @click="opened = !opened">
      {{ opened ? 'Fechar atualização' : 'Atualizar dados' }}
    </button>
    <div v-show="opened" id="author-enrichment" class="enrichment-content">
      <p>Consulte o perfil no Hardcover e confira a pessoa antes de aplicar.</p>
      <p v-if="profile">
        Vínculo atual:
        <a :href="profile.sourceUrl" target="_blank" rel="noopener noreferrer">{{ profile.externalName }}</a>
      </p>
      <form @submit.prevent="lookup">
        <label
          >Link ou ID do autor no Hardcover<input
            v-model="reference"
            type="text"
            required
            :disabled="busy"
            placeholder="https://hardcover.app/authors/…"
            @input="invalidate"
        /></label>
        <button type="submit" :disabled="busy || !reference.trim()">
          {{ busy ? 'Aguarde…' : 'Consultar perfil' }}
        </button>
      </form>
      <p v-if="message" :role="failed ? 'alert' : 'status'">{{ message }}</p>
      <form v-if="preview" @submit.prevent="apply">
        <h3>Conferir perfil</h3>
        <p>
          <a :href="preview.proposed.sourceUrl" target="_blank" rel="noopener noreferrer"
            >{{ preview.proposed.externalName }} no Hardcover ↗</a
          >
        </p>
        <p v-if="changing">
          Você está trocando de pessoa. Os dados do perfil anterior serão removidos; livros e leituras permanecem.
        </p>
        <label v-if="changing" class="field-check"
          ><input v-model="confirmChange" type="checkbox" :disabled="busy" />Confirmo a troca do perfil vinculado</label
        >
        <fieldset :disabled="busy">
          <legend>Escolha os campos disponíveis</legend>
          <label v-for="field in fields" :key="field.key" class="field-check"
            ><input v-model="selected" type="checkbox" :value="field.key" :disabled="!field.available" /><span
              ><strong>{{ field.label }}</strong
              ><span class="field-current">Atual: {{ field.current || 'Não informado' }}</span
              ><span class="field-proposed">Hardcover: {{ field.proposed || 'Não disponível' }}</span
              ><img
                v-if="field.key === 'PHOTO' && preview.proposed.photoUrl"
                class="photo-preview"
                :src="preview.proposed.photoUrl"
                :alt="`Foto proposta de ${preview.proposed.externalName}`"
                referrerpolicy="no-referrer" /></span
          ></label>
        </fieldset>
        <p v-if="!fields.some((field) => field.available)">Este perfil não tem dados disponíveis para aplicar.</p>
        <button type="submit" :disabled="busy || !selected.length || (changing && !confirmChange)">
          {{ busy ? 'Aplicando…' : 'Aplicar campos selecionados' }}
        </button>
      </form>
    </div>
  </div>
</template>
<script setup lang="ts">
import type { AuthorEnrichmentProfile, AuthorEnrichmentPreview, AuthorEnrichmentField } from '~/types/books'
import { bookGroupError, useBookGroupsApi } from '~/composables/useBookGroupsApi'
const props = defineProps<{ authorId: string; profile: AuthorEnrichmentProfile | null }>()
const emit = defineEmits<{ saved: [profile: AuthorEnrichmentProfile] }>()
const api = useBookGroupsApi()
const opened = ref(false),
  reference = ref(props.profile?.sourceUrl || ''),
  busy = ref(false),
  failed = ref(false),
  message = ref('')
const preview = ref<AuthorEnrichmentPreview | null>(null),
  selected = ref<AuthorEnrichmentField[]>([]),
  confirmChange = ref(false)
const changing = computed(
  () => !!preview.value?.current && preview.value.current.hardcoverId !== preview.value.proposed.hardcoverId,
)
const fields = computed(() => {
  const current = preview.value?.current,
    proposed = preview.value?.proposed
  const dates = (value?: AuthorEnrichmentProfile | null) =>
    [value?.born ? `Nascimento: ${value.born}` : '', value?.died ? `Falecimento: ${value.died}` : '']
      .filter(Boolean)
      .join(' · ')
  return [
    { key: 'PHOTO' as const, label: 'Foto', current: current?.photoSourceUrl, proposed: proposed?.photoSourceUrl },
    { key: 'BIOGRAPHY' as const, label: 'Biografia', current: current?.biography, proposed: proposed?.biography },
    { key: 'DATES' as const, label: 'Datas', current: dates(current), proposed: dates(proposed) },
    { key: 'LOCATION' as const, label: 'Origem', current: current?.location, proposed: proposed?.location },
    {
      key: 'LINKS' as const,
      label: 'Links externos',
      current: current?.links.join('\n'),
      proposed: proposed?.links.join('\n'),
    },
  ].map((field) => ({ ...field, available: !!field.proposed }))
})
function invalidate() {
  preview.value = null
  selected.value = []
  confirmChange.value = false
  message.value = ''
}
async function lookup() {
  if (busy.value) return
  invalidate()
  busy.value = true
  failed.value = false
  try {
    preview.value = await api<AuthorEnrichmentPreview>(`/authors/${props.authorId}/enrichment/preview`, 'POST', {
      reference: reference.value.trim(),
    })
  } catch (error) {
    failed.value = true
    message.value = bookGroupError(error)
  } finally {
    busy.value = false
  }
}
async function apply() {
  if (busy.value || !preview.value) return
  busy.value = true
  failed.value = false
  message.value = ''
  try {
    const result = await api<AuthorEnrichmentProfile>(`/authors/${props.authorId}/enrichment`, 'POST', {
      token: preview.value.token,
      fields: selected.value,
      confirmIdentityChange: confirmChange.value,
    })
    emit('saved', result)
    preview.value = null
    selected.value = []
    message.value = 'Dados do autor atualizados.'
  } catch (error) {
    failed.value = true
    message.value = bookGroupError(error)
  } finally {
    busy.value = false
  }
}
</script>
<style scoped>
.photo-preview {
  max-width: 100%;
  max-height: 200px;
  width: auto;
  height: auto;
  object-fit: contain;
}
.enrichment {
  margin-top: 20px;
}
.enrichment-content,
form,
label {
  display: grid;
  gap: 12px;
  min-width: 0;
}
.enrichment-content {
  margin-top: 20px;
  gap: 20px;
}
p,
h3 {
  margin: 0;
  line-height: 1.6;
}
button,
input[type='text'] {
  padding: 10px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 12px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
  font: inherit;
}
button {
  width: fit-content;
  cursor: pointer;
}
button:disabled {
  opacity: 0.55;
  cursor: default;
}
input[type='text'] {
  box-sizing: border-box;
  width: 100%;
}
fieldset {
  border: 1px solid var(--base-color-border);
  padding: 16px;
  border-radius: 16px;
  min-width: 0;
}
.field-check {
  display: flex;
  align-items: start;
  gap: 10px;
  padding: 12px 0;
}
.field-check > input {
  margin-top: 5px;
  flex-shrink: 0;
}
.field-check > span {
  min-width: 0;
  display: grid;
  gap: 8px;
}
.field-current,
.field-proposed {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 0.88rem;
  line-height: 1.6;
  color: var(--base-color-text-secondary);
}
:focus-visible {
  outline: 2px solid #435ee5;
  outline-offset: 3px;
}
</style>
