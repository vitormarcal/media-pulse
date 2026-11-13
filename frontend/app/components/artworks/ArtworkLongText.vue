<template>
  <section v-if="text" class="art-long-text" :aria-labelledby="headingId">
    <h2 :id="headingId">{{ title }}</h2>
    <p :id="contentId" class="art-preserve">
      <template v-for="(token, index) in visibleTokens" :key="index"
        ><strong v-if="token.kind === 'strong'">{{ token.value }}</strong
        ><em v-else-if="token.kind === 'em'">{{ token.value }}</em
        ><template v-else>{{ token.value }}</template></template
      >
    </p>
    <button
      v-if="long"
      type="button"
      class="art-button art-read-more"
      :aria-expanded="expanded"
      :aria-controls="contentId"
      @click="expanded = !expanded"
    >
      {{ expanded ? 'Recolher' : 'Continuar leitura' }}
    </button>
  </section>
</template>
<script setup lang="ts">
import { artworkTextTokens, artworkTextPreview } from '~/utils/artworkText'
const props = withDefaults(defineProps<{ title: string; text: string | null | undefined; previewLength?: number }>(), {
  previewLength: 700,
})
const headingId = useId()
const contentId = useId()
const expanded = ref(false)
const tokens = computed(() => artworkTextTokens(props.text || ''))
const long = computed(() => tokens.value.reduce((total, token) => total + token.value.length, 0) > props.previewLength)
const visibleTokens = computed(() =>
  expanded.value || !long.value ? tokens.value : artworkTextPreview(tokens.value, props.previewLength),
)
watch(
  () => props.text,
  () => {
    expanded.value = false
  },
)
</script>
