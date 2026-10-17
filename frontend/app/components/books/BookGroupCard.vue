<template>
  <article class="group-card">
    <NuxtLink class="group-card-cover" :to="href" :aria-label="group.name">
      <div
        class="group-mosaic"
        :class="{
          'group-mosaic-single': group.coverUrls.length === 1,
          'group-mosaic-pair': group.coverUrls.length === 2,
          'group-mosaic-three': group.coverUrls.length === 3,
        }"
      >
        <MediaImage
          v-for="(cover, index) in group.coverUrls.slice(0, 4)"
          :key="`${cover}-${index}`"
          :src="resolveMediaUrl(cover)"
          alt=""
        />
        <span v-if="!group.coverUrls.length" class="group-initial">{{ group.name.slice(0, 1) }}</span>
      </div>
    </NuxtLink>
    <div class="group-card-copy">
      <NuxtLink :to="href"
        ><h3>{{ group.name }}</h3></NuxtLink
      >
      <p v-if="group.authors.length" class="group-muted">
        <template v-for="(author, index) in group.authors" :key="author.id"
          ><span v-if="index">, </span
          ><NuxtLink :to="`/books/authors/${author.id}`">{{ author.name }}</NuxtLink></template
        >
      </p>
      <p v-if="group.description" class="group-card-description">{{ group.description }}</p>
      <span class="group-badge"
        >{{ group.itemCount }}
        {{
          kind === 'series'
            ? group.itemCount === 1
              ? 'livro no acervo'
              : 'livros no acervo'
            : group.itemCount === 1
              ? 'série'
              : 'séries'
        }}</span
      >
    </div>
  </article>
</template>
<script setup lang="ts">
import type { BookGroup, BookGroupKind } from '~/types/book-series'
import '~/assets/css/book-groups.css'
const props = withDefaults(defineProps<{ group: BookGroup; kind?: BookGroupKind }>(), { kind: 'series' })
const href = computed(() => `/books/${props.kind}/${props.group.id}`)
const { resolveMediaUrl } = useMediaUrl()
</script>
