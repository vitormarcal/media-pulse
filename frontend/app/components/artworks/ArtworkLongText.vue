<template>
  <section v-if="text" class="art-long-text" :aria-labelledby="headingId">
    <h2 :id="headingId">{{ title }}</h2>
    <p :id="contentId" class="art-preserve">{{ expanded || !long ? text : excerpt }}</p>
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
const props = defineProps<{ title: string; text: string | null | undefined }>()
const previewLength = 700
const headingId = useId()
const contentId = useId()
const expanded = ref(false)
const long = computed(() => (props.text?.length || 0) > previewLength)
const excerpt = computed(() => {
  const preview = props.text?.slice(0, previewLength) || ''
  const boundary = preview.lastIndexOf(' ')
  return `${preview.slice(0, boundary > previewLength * 0.7 ? boundary : previewLength).trimEnd()}…`
})
watch(
  () => props.text,
  () => {
    expanded.value = false
  },
)
</script>
