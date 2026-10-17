<template><BookGroupPage :id="id" :key="id" kind="universes" /></template>
<script setup lang="ts">
import BookGroupPage from '~/components/books/BookGroupPage.vue'
const route = useRoute()
const id = computed(() => String(route.params.id))
</script>
