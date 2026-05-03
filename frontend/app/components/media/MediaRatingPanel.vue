<template>
  <section class="rating-panel" :class="{ compact }">
    <SectionHeading
      v-if="!compact"
      eyebrow="Avaliação"
      :title="title"
      :description="description"
      summary="A nota precisa ser rápida de usar no dia a dia, mas firme o bastante para sustentar consistência ao longo do tempo."
    />

    <div class="rating-card" :class="{ compact }">
      <template v-if="compact">
        <div class="compact-row">
          <span class="compact-label">{{ label }}</span>

          <div class="compact-actions">
            <button
              v-for="item in scale"
              :key="item.rating"
              type="button"
              class="compact-button"
              :class="{ selected: currentRating === item.rating }"
              :disabled="submitting"
              :aria-label="`${label}: ${item.rating}`"
              @click="toggleRating(item.rating)"
            >
              {{ item.rating }}
            </button>
          </div>
        </div>

        <p v-if="currentScaleItem" class="compact-copy">{{ currentScaleItem.compactHint }}</p>
        <p v-if="errorMessage" class="feedback error">{{ errorMessage }}</p>
      </template>

      <template v-else>
        <div class="rating-overview">
          <div class="current-state">
            <p class="kicker">Nota atual</p>
            <div class="current-value">
              <strong>{{ currentRating ?? '—' }}</strong>
              <div>
                <p class="current-title">{{ currentScaleItem?.title ?? 'Ainda sem nota' }}</p>
                <span class="current-copy">
                  {{ currentScaleItem?.description ?? 'Use a escala abaixo para marcar o peso real dessa obra.' }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div class="rating-scale" role="group" :aria-label="title">
          <button
            v-for="item in scale"
            :key="item.rating"
            type="button"
            class="scale-button"
            :class="{ selected: currentRating === item.rating }"
            :disabled="submitting"
            @click="toggleRating(item.rating)"
          >
            <span class="scale-number">{{ item.rating }}</span>
            <span class="scale-title">{{ item.title }}</span>
          </button>
        </div>

        <p class="scale-copy">
          {{
            currentScaleItem
              ? currentScaleItem.description
              : 'Escolha uma nota. O critério mais importante aqui é manter o uso consistente entre obras.'
          }}
        </p>

        <div class="scale-anchors">
          <span v-for="anchor in anchors" :key="anchor.id" class="anchor-item">{{ anchor.label }}</span>
        </div>

        <details class="scale-reference">
          <summary>Escala completa</summary>

          <div class="reference-list">
            <article v-for="item in scale" :key="`reference-${item.rating}`" class="reference-item">
              <strong>{{ item.rating }}</strong>
              <div>
                <p>{{ item.title }}</p>
                <span>{{ item.description }}</span>
              </div>
            </article>
          </div>
        </details>

        <p v-if="errorMessage" class="feedback error">{{ errorMessage }}</p>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { MediaRatingDto } from '~/types/ratings'

const scale = [
  {
    rating: 1,
    title: 'Ruim',
    description: 'Não gostei. Ficou chato, fraco ou de qualidade duvidosa.',
    compactHint: 'Ruim: não gostei.',
  },
  {
    rating: 2,
    title: 'Fraco',
    description: 'Tem algo aqui, mas o saldo final ficou abaixo do que eu queria.',
    compactHint: 'Fraco: abaixo do esperado.',
  },
  {
    rating: 3,
    title: 'Bom',
    description: 'Gostei. Funciona bem, mas não vira referência nem pede retorno imediato.',
    compactHint: 'Bom: gostei, mas sem grande peso.',
  },
  {
    rating: 4,
    title: 'Muito bom',
    description: 'Me pegou de verdade. Claramente acima da média e com vontade real de revisitar.',
    compactHint: 'Muito bom: acima da média.',
  },
  {
    rating: 5,
    title: 'Essencial',
    description: 'Amei. Entra no grupo raro do que considero grande, pessoal e memorável.',
    compactHint: 'Essencial: raro e grande para você.',
  },
] as const

const anchors = [
  {
    id: 'three',
    label: '3 = gostei',
    copy: 'Bom de verdade, mas ainda não entra no grupo do que mais te marca.',
  },
  {
    id: 'four',
    label: '4 = me pegou',
    copy: 'Já está claramente acima da média e deixa vontade forte de voltar.',
  },
  {
    id: 'five',
    label: '5 = raro',
    copy: 'Reserve para o que você realmente ama e quer proteger da inflação da escala.',
  },
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
      'A nota deve te ajudar a distinguir algo só bom de algo que realmente merece ocupar espaço maior no seu repertório.',
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

async function toggleRating(rating: number) {
  if (currentRating.value === rating) {
    await clearRating()
    return
  }

  await selectRating(rating)
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
  gap: 16px;
  padding: clamp(20px, 2.4vw, 28px);
  border-radius: 28px;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, white 94%, var(--base-color-surface-soft)),
    color-mix(in srgb, var(--base-color-surface-soft) 92%, white)
  );
  border: 1px solid color-mix(in srgb, var(--base-color-border) 48%, white);
}

.rating-card.compact {
  padding: 0;
  gap: 6px;
  border: 0;
  background: transparent;
}

.rating-overview,
.current-value,
.compact-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
}

.current-state {
  display: grid;
  gap: 10px;
}

.kicker,
.compact-label,
.scale-reference summary {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.current-value strong {
  display: inline-grid;
  place-items: center;
  min-width: 40px;
  min-height: 40px;
  padding: 0 10px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 82%, white);
  color: var(--base-color-text-primary);
  font-size: 1rem;
  line-height: 1;
}

.current-title,
.current-copy,
.compact-copy,
.anchor-item p,
.anchor-item span,
.reference-item p,
.reference-item span,
.feedback {
  margin: 0;
}

.current-title,
.reference-item p {
  color: var(--base-color-text-primary);
  font-weight: 600;
}

.current-copy,
.compact-copy,
.anchor-item span,
.reference-item span {
  color: var(--base-color-text-secondary);
}

.rating-scale {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.scale-button,
.compact-button {
  border: 1px solid color-mix(in srgb, var(--base-color-border) 76%, white);
  border-radius: 16px;
  background: transparent;
  color: var(--base-color-text-primary);
  font: inherit;
  cursor: pointer;
  transition:
    background 160ms ease,
    border-color 160ms ease,
    transform 160ms ease;
}

.scale-button {
  display: grid;
  gap: 4px;
  justify-items: center;
  min-height: 64px;
  padding: 10px 10px;
  text-align: center;
}

.compact-actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.compact-button {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  padding: 0;
  font-size: 0.88rem;
  font-weight: 700;
}

.scale-button:hover,
.compact-button:hover {
  border-color: var(--base-color-hover-grayscale-150);
  transform: translateY(-1px);
}

.scale-button.selected,
.compact-button.selected {
  background: color-mix(in srgb, var(--base-color-brand-red) 14%, white);
  border-color: color-mix(in srgb, var(--base-color-brand-red) 24%, var(--base-color-border));
  color: var(--base-color-text-primary);
}

.scale-button:focus-visible,
.compact-button:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 2px;
}

.scale-number {
  font-size: 1.08rem;
  font-weight: 700;
}

.scale-title {
  font-size: 0.82rem;
  line-height: 1.2;
}

.scale-copy {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.92rem;
  line-height: 1.6;
}

.scale-reference {
  display: grid;
  gap: 12px;
}

.scale-reference summary {
  cursor: pointer;
  list-style: none;
}

.scale-reference summary::-webkit-details-marker {
  display: none;
}

.reference-list {
  display: grid;
  gap: 10px;
}

.scale-anchors {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.anchor-item,
.reference-item {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 16px;
  background: hsla(60, 20%, 98%, 0.5);
}

.anchor-item,
.reference-item strong {
  color: var(--base-color-text-primary);
  font-weight: 600;
}

.reference-item {
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 12px;
}

.feedback.error {
  color: #9e0a0a;
}

@media (max-width: 820px) {
  .rating-scale {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .scale-anchors {
    grid-template-columns: 1fr;
  }
}
</style>
