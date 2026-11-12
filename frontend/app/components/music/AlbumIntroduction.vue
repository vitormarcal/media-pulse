<template><WikipediaIntroduction class="album-introduction" :introduction="introduction" /></template>
<script setup lang="ts">
import WikipediaIntroduction from '~/components/music/WikipediaIntroduction.vue'
import type { AlbumIntroduction } from '~/types/music'
defineProps<{ introduction: AlbumIntroduction }>()
</script>
