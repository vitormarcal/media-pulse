<template>
  <div class="album-introduction">
    <p v-for="(paragraph, index) in displayed.split('\n').filter(Boolean)" :key="index" class="paragraph">
      {{ paragraph }}
    </p>
    <button v-if="introduction.text.length > 550" type="button" :aria-expanded="expanded" @click="expanded = !expanded">
      {{ expanded ? 'Ler menos' : 'Ler mais' }}
    </button>
    <p class="attribution">
      Texto da <a :href="source" target="_blank" rel="noopener noreferrer">Wikipedia e seus colaboradores</a> ·
      {{ introduction.language === 'pt' ? 'Português' : 'Inglês' }} ·
      <a :href="introduction.licenseUrl" target="_blank" rel="noopener noreferrer">{{ introduction.license }}</a>
      · Trecho da introdução
    </p>
  </div>
</template>
<script setup lang="ts">
import type { AlbumIntroduction } from '~/types/music'
const props = defineProps<{ introduction: AlbumIntroduction }>()
const expanded = ref(false)
watch(
  () => props.introduction,
  () => {
    expanded.value = false
  },
)
const displayed = computed(() =>
  expanded.value || props.introduction.text.length <= 550
    ? props.introduction.text
    : props.introduction.text.slice(0, 550).replace(/\s+\S*$/, '') + '…',
)
const source = computed(() =>
  props.introduction.revisionId
    ? `${props.introduction.sourceUrl}${props.introduction.sourceUrl.includes('?') ? '&' : '?'}oldid=${props.introduction.revisionId}`
    : props.introduction.sourceUrl,
)
</script>
<style scoped>
.album-introduction {
  display: grid;
  gap: 12px;
  margin-top: 24px;
}
p {
  margin: 0;
}
.paragraph {
  line-height: 1.65;
  font-size: 0.95rem;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.attribution {
  font-size: 0.73rem;
  line-height: 1.6;
  color: var(--base-color-text-secondary);
}
a {
  color: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
}
button {
  justify-self: start;
  padding: 8px 12px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;
}
button:focus-visible,
a:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
</style>
