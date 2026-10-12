<template>
  <nav class="mag-actions" aria-label="Acervo de revistas">
    <NuxtLink class="mag-button" to="/magazines/publications" :aria-current="isPublication ? 'page' : undefined">
      Publicações
    </NuxtLink>
    <NuxtLink class="mag-button" to="/magazines?view=archive" :aria-current="!isPublication ? 'page' : undefined">
      Todos os números
    </NuxtLink>
  </nav>
</template>
<script setup lang="ts">
const route = useRoute()
const isPublication = computed(() => route.path.startsWith('/magazines/publications'))
</script>
<style scoped src="../../assets/css/magazines.css"></style>
