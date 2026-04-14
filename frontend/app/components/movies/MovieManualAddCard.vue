<template>
  <section class="manual-add-card">
    <SectionHeading
      eyebrow="Não achou?"
      title="Registre manualmente e refine depois"
      description="Quando a busca não devolve o filme certo, você ainda pode abrir uma entrada enxuta e consolidar os detalhes mais tarde."
      summary="Um atalho editorial para não interromper o catálogo."
    />

    <article class="form-card">
      <form class="form-grid" @submit.prevent="handleSubmit">
        <label class="field field-wide">
          <span>Título</span>
          <input v-model="title" type="text" placeholder="Nome do filme">
        </label>

        <label class="field">
          <span>Ano</span>
          <input v-model="year" type="number" inputmode="numeric" placeholder="2024">
        </label>

        <label class="field">
          <span>TMDb ID</span>
          <input v-model="tmdbId" type="text" placeholder="Opcional">
        </label>

        <label class="field">
          <span>IMDb ID</span>
          <input v-model="imdbId" type="text" placeholder="Opcional">
        </label>

        <div class="actions">
          <button type="submit" class="primary-button" :disabled="submitting || !title.trim()">
            {{ submitting ? 'Registrando...' : 'Registrar filme' }}
          </button>
        </div>
      </form>

      <p v-if="feedback" class="feedback" :class="{ error: feedbackError }">{{ feedback }}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ManualMovieCatalogCreateResponse } from '~/types/movies'

const props = defineProps<{
  initialTitle: string
}>()

const title = ref(props.initialTitle)
const year = ref('')
const tmdbId = ref('')
const imdbId = ref('')
const submitting = ref(false)
const feedback = ref<string | null>(null)
const feedbackError = ref(false)
const config = useRuntimeConfig()
const router = useRouter()

watch(() => props.initialTitle, (value) => {
  title.value = value
})

async function handleSubmit() {
  submitting.value = true
  feedback.value = null
  feedbackError.value = false

  try {
    const response = await $fetch<ManualMovieCatalogCreateResponse>('/api/movies/catalog', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        title: title.value.trim(),
        year: year.value.trim() ? Number(year.value) : null,
        tmdbId: tmdbId.value.trim() || null,
        imdbId: imdbId.value.trim() || null,
      },
    })

    feedback.value = response.createdMovie ? 'Filme adicionado ao catálogo.' : 'Filme existente reaproveitado e consolidado.'
    await router.push(response.slug ? `/movies/${response.slug}` : `/movies/library?q=${encodeURIComponent(title.value.trim())}`)
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível adicionar o filme agora.'
    feedbackError.value = true
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.manual-add-card {
  display: grid;
  gap: 24px;
}

.form-card {
  display: grid;
  gap: 18px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.field {
  display: grid;
  gap: 8px;
}

.field-wide {
  grid-column: span 2;
}

.field span {
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.field input {
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
}

.actions {
  grid-column: span 2;
  display: flex;
  justify-content: flex-end;
}

.primary-button {
  border: 0;
  border-radius: 16px;
  padding: 12px 16px;
  background: var(--base-color-brand-red);
  color: #000000;
  cursor: pointer;
}

.feedback {
  margin: 0;
  color: #103c25;
}

.feedback.error {
  color: #7a1414;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }

  .field-wide,
  .actions {
    grid-column: auto;
  }
}
</style>
