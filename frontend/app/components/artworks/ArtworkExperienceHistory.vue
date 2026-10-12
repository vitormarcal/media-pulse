<template>
  <section class="art-panel">
    <div class="art-header">
      <h2>Experiências</h2>
      <button class="art-button" @click="start()">Registrar experiência</button>
    </div>
    <form v-if="editing !== undefined" @submit.prevent="save">
      <fieldset :disabled="busy">
        <ArtworkExperienceFields v-model="form" />
        <div class="art-actions">
          <button class="art-button art-primary">{{ busy ? 'Salvando…' : 'Salvar experiência' }}</button
          ><button type="button" class="art-button" @click="editing = undefined">Cancelar</button>
        </div>
      </fieldset>
    </form>
    <p v-if="error" class="art-error" role="alert">{{ error }}</p>
    <p v-if="success" class="art-success" role="status">{{ success }}</p>
    <p v-if="!experiences.length" class="art-muted">Nenhuma experiência registrada.</p>
    <article v-for="experience in experiences" :key="experience.id" class="art-section">
      <h3>
        {{ experience.experiencedAt ? formatDate(experience.experiencedAt) : 'Data não informada'
        }}<span v-if="experience.experienceType">
          · {{ experience.experienceType === 'IN_PERSON' ? 'Presencial' : 'Digital' }}</span
        >
      </h3>
      <p v-if="experience.sourceName">{{ experience.sourceName }}</p>
      <a v-if="experience.sourceUrl" :href="experience.sourceUrl" target="_blank" rel="noopener"
        >Origem da descoberta</a
      >
      <p v-if="experience.notes" class="art-preserve">{{ experience.notes }}</p>
      <div class="art-actions">
        <button class="art-button" :disabled="busy" @click="start(experience)">Editar</button
        ><button class="art-button" :disabled="busy" @click="remove(experience.id)">Excluir</button>
      </div>
    </article>
  </section>
</template>
<script setup lang="ts">
import type { ArtworkExperience, ArtworkExperienceInput } from '~/types/artworks'
import ArtworkExperienceFields from './ArtworkExperienceFields.vue'
const props = defineProps<{ artworkId: number; experiences: ArtworkExperience[] }>()
const emit = defineEmits<{ changed: [] }>()
const { request, run, busy, error, success } = useArtworkApi()
const editing = ref<number | null | undefined>(undefined)
const form = ref<ArtworkExperienceInput>({
  experiencedAt: null,
  experienceType: null,
  notes: null,
  sourceName: null,
  sourceUrl: null,
})
function start(value?: ArtworkExperience) {
  editing.value = value?.id ?? null
  form.value = {
    experiencedAt: value?.experiencedAt ?? null,
    experienceType: value?.experienceType ?? null,
    notes: value?.notes ?? null,
    sourceName: value?.sourceName ?? null,
    sourceUrl: value?.sourceUrl ?? null,
  }
  error.value = ''
  success.value = ''
}
function formatDate(date: string) {
  return new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`))
}
async function save() {
  await run(async () => {
    await request(`/${props.artworkId}/experiences${editing.value ? `/${editing.value}` : ''}`, {
      method: editing.value ? 'PUT' : 'POST',
      body: { ...form.value, experiencedAt: form.value.experiencedAt || null },
    })
    editing.value = undefined
    emit('changed')
  })
}
async function remove(id: number) {
  if (!window.confirm('Excluir esta experiência? A obra será preservada.')) return
  await run(async () => {
    await request(`/${props.artworkId}/experiences/${id}`, { method: 'DELETE' })
    if (editing.value === id) editing.value = undefined
    emit('changed')
  })
}
</script>
