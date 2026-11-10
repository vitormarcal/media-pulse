<template>
  <span
    v-if="label"
    class="image-state"
    :class="[stateClass, { 'on-dark': dark }]"
    :title="description"
    :aria-label="description"
  >
    <svg v-if="state === 'CHECKING'" class="state-icon spinner" viewBox="0 0 20 20" aria-hidden="true">
      <circle cx="10" cy="10" r="7" fill="none" stroke="currentColor" stroke-width="2" stroke-dasharray="30 14" />
    </svg>
    <svg
      v-else-if="state === 'IN_GALLERY'"
      class="state-icon"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      aria-hidden="true"
    >
      <rect x="3" y="3" width="14" height="14" rx="3" />
      <circle cx="7" cy="7" r="1" />
      <path d="m4 14 4-4 3 3 2-2 3 3" />
    </svg>
    <svg
      v-else-if="state === 'IN_LIBRARY'"
      class="state-icon"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      aria-hidden="true"
    >
      <rect x="6" y="3" width="11" height="11" rx="2" />
      <path d="M3 6v9a2 2 0 0 0 2 2h9" />
    </svg>
    <svg
      v-else
      class="state-icon"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      aria-hidden="true"
    >
      <circle cx="10" cy="10" r="7" />
      <path d="M10 9v5m0-9v2" />
    </svg>
    <span :class="{ 'visually-hidden': state === 'CHECKING' }">{{ label }}</span>
  </span>
</template>
<script setup lang="ts">
const props = defineProps<{
  state?: 'CHECKING' | 'IN_GALLERY' | 'IN_LIBRARY' | 'NOT_FOUND' | 'UNAVAILABLE'
  dark?: boolean
  mediaType: 'games' | 'movies' | 'shows'
}>()
const entity = computed(() => ({ games: 'no jogo', movies: 'no filme', shows: 'na série' })[props.mediaType])
const association = computed(() => ({ games: 'ao jogo', movies: 'ao filme', shows: 'à série' })[props.mediaType])
const label = computed(
  () =>
    ({
      CHECKING: 'Verificando',
      IN_GALLERY: `Já ${entity.value}`,
      IN_LIBRARY: 'No Visual Library',
      NOT_FOUND: '',
      UNAVAILABLE: 'Sem verificação',
    })[props.state ?? 'NOT_FOUND'],
)
const description = computed(
  () =>
    ({
      CHECKING: 'Verificando se a imagem já existe no Visual Library.',
      IN_GALLERY: `Já ${entity.value}. Esta imagem já está na galeria e continua selecionável.`,
      IN_LIBRARY: `No Visual Library. Esta imagem já existe e pode ser associada ${association.value}.`,
      NOT_FOUND: '',
      UNAVAILABLE: 'Não foi possível verificar esta imagem. Você pode selecioná-la normalmente.',
    })[props.state ?? 'NOT_FOUND'],
)
const stateClass = computed(() => props.state?.toLowerCase().replaceAll('_', '-'))
</script>
<style scoped>
.image-state {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 26px;
  padding: 4px 8px;
  border-radius: 8px;
  color: var(--base-color-text-secondary);
  font-size: 0.72rem;
  font-weight: 600;
  line-height: 1.35;
  white-space: nowrap;
}
.in-gallery {
  background: var(--base-color-dark);
  color: #fff;
}
.in-library {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}
.checking,
.unavailable {
  padding-left: 0;
  font-weight: 400;
}
.on-dark.checking,
.on-dark.unavailable {
  color: #e5e5e0;
}
.state-icon {
  flex: 0 0 15px;
  width: 15px;
  height: 15px;
}
.spinner {
  animation: checking 1.2s linear infinite;
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
@keyframes checking {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation: none;
  }
}
</style>
