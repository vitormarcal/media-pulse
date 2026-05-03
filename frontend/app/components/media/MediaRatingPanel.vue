<template>
  <section class="rating-panel" :class="{ compact }">
    <SectionHeading
      v-if="!compact"
      eyebrow="Avaliação"
      :title="title"
      :description="description"
      summary="A escala fica fixa para te lembrar o peso de cada nota e evitar inflar avaliações no impulso."
    />

    <div class="rating-card" :class="{ compact }">
      <p class="eyebrow">{{ compact ? label : 'Escala pessoal de 1 a 5' }}</p>

      <div class="rating-actions">
        <button
          v-for="item in scale"
          :key="item.rating"
          type="button"
          class="rating-button"
          :class="{ selected: currentRating === item.rating, compact }"
          :disabled="submitting"
          @click="selectRating(item.rating)"
        >
          <span class="rating-number">{{ item.rating }}</span>
          <span v-if="!compact" class="rating-label">{{ item.title }}</span>
        </button>

        <button
          v-if="currentRating != null"
          type="button"
          class="clear-button"
          :disabled="submitting"
          @click="clearRating"
        >
          Limpar
        </button>
      </div>

      <p class="selection-copy">
        {{ currentScaleItem ? currentScaleItem.description : emptyCopy }}
      </p>

      <div v-if="!compact" class="legend-list">
        <article v-for="item in scale" :key="`legend-${item.rating}`" class="legend-item">
          <strong>{{ item.rating }}</strong>
          <div>
            <p>{{ item.title }}</p>
            <span>{{ item.description }}</span>
          </div>
        </article>
      </div>

      <p v-if="errorMessage" class="feedback error">{{ errorMessage }}</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { MediaRatingDto } from '~/types/ratings'

const scale = [
  { rating: 1, title: 'Fraco', description: 'Não gostei, achei chato ou de qualidade duvidosa.' },
  { rating: 2, title: 'Abaixo', description: 'Tem algo aqui, mas ficou aquém do que prometia.' },
  { rating: 3, title: 'Bom', description: 'Funciona bem, gostei, mas não chega a marcar tanto.' },
  { rating: 4, title: 'Muito bom', description: 'Ficou claramente acima da média e vale revisitar.' },
  { rating: 5, title: 'Masterpiece', description: 'Realmente amei. Fica reservado para o que é especial.' },
] as const

const props = withDefaults(
  defineProps<{
    mediaType: 'movies' | 'shows' | 'episodes' | 'albums' | 'tracks'
    entityId: number
    initialRating: MediaRatingDto | null
    title?: string
    description?: string
    label?: string
    compact?: boolean
  }>(),
  {
    title: 'Como essa obra fica na sua escala',
    description:
      'Uma nota curta, mas com peso real. A escala existe para te ajudar a manter consistência ao longo do tempo.',
    label: 'Sua nota',
    compact: false,
  },
)

const emit = defineEmits<{
  changed: [rating: MediaRatingDto | null]
}>()

const config = useRuntimeConfig()
const currentRating = ref<number | null>(props.initialRating?.rating ?? null)
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

watch(
  () => props.initialRating,
  (nextRating) => {
    currentRating.value = nextRating?.rating ?? null
  },
)

const currentScaleItem = computed(() => scale.find((item) => item.rating === currentRating.value) ?? null)
const emptyCopy = computed(() =>
  props.compact
    ? 'Sem nota por enquanto.'
    : 'Sem nota ainda. Use a escala acima para manter o critério estável entre obras e revisitas.',
)

async function selectRating(rating: number) {
  if (submitting.value) return

  submitting.value = true
  errorMessage.value = null

  try {
    const response = await $fetch<MediaRatingDto>(`/api/ratings/${props.mediaType}/${props.entityId}`, {
      method: 'POST',
      baseURL: config.public.apiBase,
      body: { rating },
    })

    currentRating.value = response.rating
    emit('changed', response)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Não foi possível salvar a nota.'
  } finally {
    submitting.value = false
  }
}

async function clearRating() {
  if (submitting.value) return

  submitting.value = true
  errorMessage.value = null

  try {
    await $fetch(`/api/ratings/${props.mediaType}/${props.entityId}`, {
      method: 'DELETE',
      baseURL: config.public.apiBase,
    })

    currentRating.value = null
    emit('changed', null)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Não foi possível limpar a nota.'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.rating-panel {
  display: grid;
  gap: 24px;
}

.rating-card {
  display: grid;
  gap: 18px;
  padding: clamp(22px, 3vw, 32px);
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
}

.rating-card.compact {
  padding: 14px 16px;
  border-radius: 20px;
  gap: 12px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.eyebrow,
.legend-item strong {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.rating-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.rating-button,
.clear-button {
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: #e5e5e0;
  color: #211922;
  min-height: 44px;
  padding: 8px 14px;
  font: inherit;
  cursor: pointer;
  transition:
    background 160ms ease,
    border-color 160ms ease,
    transform 160ms ease;
}

.rating-button {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.rating-button.compact {
  min-width: 40px;
  justify-content: center;
  padding: 8px 10px;
}

.rating-button:hover,
.clear-button:hover {
  border-color: #bcbcb3;
  transform: translateY(-1px);
}

.rating-button.selected {
  background: #e60023;
  border-color: transparent;
  color: #000000;
}

.rating-button:focus-visible,
.clear-button:focus-visible {
  outline: 3px solid #435ee5;
  outline-offset: 2px;
}

.rating-number {
  font-size: 1rem;
  font-weight: 700;
}

.rating-label,
.selection-copy,
.legend-item p,
.legend-item span,
.feedback {
  margin: 0;
}

.selection-copy,
.legend-item span {
  color: var(--base-color-text-secondary);
}

.legend-list {
  display: grid;
  gap: 10px;
}

.legend-item {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr);
  gap: 12px;
  padding: 12px 14px;
  border-radius: 16px;
  background: hsla(60, 20%, 98%, 0.5);
}

.legend-item p {
  color: var(--base-color-text-primary);
  font-size: 0.95rem;
  font-weight: 600;
}

.legend-item span {
  display: block;
  margin-top: 2px;
  font-size: 0.88rem;
}

.feedback.error {
  color: #9e0a0a;
}

@media (max-width: 720px) {
  .rating-actions {
    gap: 8px;
  }

  .rating-button:not(.compact) {
    width: calc(50% - 4px);
    justify-content: center;
  }

  .clear-button {
    width: 100%;
  }
}
</style>
