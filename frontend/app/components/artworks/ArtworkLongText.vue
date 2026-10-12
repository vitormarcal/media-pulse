<template>
  <details v-if="text" class="art-long-text">
    <summary>{{ title }}</summary>
    <p class="art-preserve">{{ text }}</p>
  </details>
</template>
<script setup lang="ts">
defineProps<{ title: string; text: string | null | undefined }>()
</script>
