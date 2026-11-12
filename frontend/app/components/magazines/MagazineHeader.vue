<template>
  <header class="mag-header">
    <NuxtLink class="mag-back" :to="backLink">← {{ backLabel }}</NuxtLink>
    <div class="mag-header-panel" :class="{ 'has-cover': image }">
      <div class="mag-header-copy">
        <h1>
          <NuxtLink v-if="titleHref" :to="titleHref">{{ title }}</NuxtLink
          ><template v-else>{{ title }}</template>
        </h1>
        <p v-if="subtitle">{{ subtitle }}</p>
        <slot />
        <p v-if="failed && coverUrl" class="mag-muted">Capa indisponível.</p>
      </div>
      <figure v-if="image" class="mag-header-cover">
        <button v-if="zoom" type="button" :aria-label="`Ampliar capa de ${title}`" @click="gallery?.open(0)">
          <img :src="image" :alt="`Capa de ${title}`" @error="failed = true" />
        </button>
        <NuxtLink v-else-if="coverHref" :to="coverHref"
          ><img :src="image" :alt="coverCaption || title" @error="failed = true"
        /></NuxtLink>
        <button v-if="zoom" type="button" class="mag-button" @click="gallery?.open(0)">Ver capa</button>
        <figcaption v-if="coverCaption">{{ coverCaption }}</figcaption>
      </figure>
    </div>
    <MediaImageGallery
      v-if="zoom && coverUrl && !failed"
      ref="gallery"
      :title="title"
      :images="[coverUrl]"
      viewer-only
    />
  </header>
</template>
<script setup lang="ts">
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
const props = defineProps<{
  title: string
  subtitle?: string
  titleHref?: string
  backLink: string
  backLabel: string
  coverUrl?: string | null
  coverHref?: string
  coverCaption?: string
  zoom?: boolean
}>()
const { resolveMediaUrl } = useMediaUrl()
const failed = ref(false)
const image = computed(() => (!failed.value ? resolveMediaUrl(props.coverUrl) : null))
const gallery = ref<InstanceType<typeof MediaImageGallery> | null>(null)
watch(
  () => props.coverUrl,
  () => {
    failed.value = false
  },
)
</script>
<style scoped>
.mag-header {
  display: grid;
  gap: 20px;
}
.mag-back {
  width: fit-content;
  font-size: 0.88rem;
  color: var(--base-color-text-secondary);
}
.mag-header-panel {
  padding: clamp(20px, 4vw, 32px);
  background: var(--base-color-surface-strong);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  display: grid;
  gap: 24px;
}
.has-cover {
  grid-template-columns: minmax(0, 1fr) 240px;
  align-items: center;
}
h1 {
  margin: 0;
  font-size: clamp(2rem, 5vw, 3.5rem);
  line-height: 1.08;
  letter-spacing: -0.04em;
  overflow-wrap: anywhere;
}
.mag-header-copy {
  min-width: 0;
}
.mag-header-copy > p {
  margin: 16px 0 0;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}
.mag-header-cover {
  display: grid;
  justify-items: center;
  align-content: start;
  gap: 12px;
  margin: 0;
  min-width: 0;
}
.mag-header-cover > button:first-child {
  background: transparent;
  padding: 0;
  border: 0;
  cursor: pointer;
}
.mag-header-cover img {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 360px;
  object-fit: contain;
  border-radius: 4px;
}
.mag-header-cover figcaption {
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
  line-height: 1.5;
  text-align: center;
}
.mag-header-cover .mag-button {
  border: 1px solid var(--base-color-border);
  background: transparent;
  border-radius: 12px;
  padding: 8px 12px;
  color: inherit;
}
:focus-visible {
  outline: 2px solid #435ee5;
  outline-offset: 3px;
}
@media (max-width: 620px) {
  .has-cover {
    grid-template-columns: minmax(0, 1fr);
  }
  .mag-header-cover img {
    max-height: 320px;
    max-width: min(240px, 100%);
  }
}
</style>
