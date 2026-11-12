<template>
  <header class="author-hero">
    <NuxtLink class="back-link" to="/books">← Voltar para livros</NuxtLink>
    <div class="identity" :class="{ 'with-photo': photo }">
      <div class="identity-copy">
        <p class="eyebrow">Autor</p>
        <h1>{{ title }}</h1>
        <p v-if="dates.length || profile?.location" class="profile-meta">
          {{ [...dates, profile?.location ? `Origem: ${profile.location}` : null].filter(Boolean).join(' · ') }}
        </p>
        <slot />
      </div>
      <figure v-if="photo" class="portrait">
        <button type="button" :aria-label="`Ampliar foto de ${title}`" @click="gallery?.open(0)">
          <img :src="resolveMediaUrl(photo) || undefined" :alt="title" @error="failedPhoto = photo" /></button
        ><button type="button" class="view-photo" @click="gallery?.open(0)">Ver foto</button>
        <figcaption>
          Foto via <a :href="profile!.sourceUrl" target="_blank" rel="noopener noreferrer">Hardcover</a
          ><span v-if="profile?.photoSourceUrl">
            · <a :href="profile.photoSourceUrl" target="_blank" rel="noopener noreferrer">Imagem original ↗</a></span
          >
        </figcaption>
      </figure>
      <div v-if="profile?.biography" class="biography">
        <p :class="{ collapsed: !expanded && profile.biography.length > 600 }">{{ profile.biography }}</p>
        <button
          v-if="profile.biography.length > 600"
          type="button"
          :aria-expanded="expanded"
          @click="expanded = !expanded"
        >
          {{ expanded ? 'Ler menos' : 'Ler mais' }}
        </button>
        <p class="attribution">
          Biografia: <a :href="profile.sourceUrl" target="_blank" rel="noopener noreferrer">Hardcover ↗</a>
        </p>
      </div>
    </div>
    <MediaImageGallery v-if="photo" ref="gallery" :title="title" :images="[photo]" viewer-only />
  </header>
</template>
<script setup lang="ts">
import type { AuthorEnrichmentProfile } from '~/types/books'
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
const props = defineProps<{ title: string; profile?: AuthorEnrichmentProfile | null }>()
const { resolveMediaUrl } = useMediaUrl()
const failedPhoto = ref<string | null>(null),
  expanded = ref(false)
const gallery = ref<InstanceType<typeof MediaImageGallery> | null>(null)
const photo = computed(() =>
  props.profile?.photoUrl && props.profile.photoUrl !== failedPhoto.value ? props.profile.photoUrl : null,
)
watch(
  () => props.profile?.biography,
  () => {
    expanded.value = false
  },
)
const formatDate = (value: string) =>
  /^\d{4}$/.test(value) ? value : new Date(`${value}T12:00:00Z`).toLocaleDateString('pt-BR', { timeZone: 'UTC' })
const dates = computed(() =>
  [
    props.profile?.born ? `Nascimento: ${formatDate(props.profile.born)}` : '',
    props.profile?.died ? `Falecimento: ${formatDate(props.profile.died)}` : '',
  ].filter(Boolean),
)
</script>
<style scoped>
.author-hero {
  display: grid;
  gap: 20px;
}
.back-link {
  width: fit-content;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
.identity {
  display: grid;
  gap: 24px;
  padding: clamp(20px, 4vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
}
.identity.with-photo {
  grid-template-columns: minmax(0, 1fr) minmax(180px, 280px);
}
.identity-copy {
  min-width: 0;
}
.eyebrow {
  margin: 0 0 12px;
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}
h1 {
  margin: 0;
  font-size: clamp(2rem, 5vw, 3.5rem);
  line-height: 1.08;
  letter-spacing: -0.04em;
  overflow-wrap: anywhere;
}
.profile-meta {
  margin: 16px 0 0;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
  line-height: 1.6;
}
.portrait {
  grid-column: 2;
  grid-row: 1 / span 2;
  display: grid;
  justify-items: center;
  align-content: start;
  gap: 10px;
  margin: 0;
  min-width: 0;
}
.portrait > button {
  background: transparent;
  border: 0;
  padding: 0;
  cursor: pointer;
  color: inherit;
  font: inherit;
}
.portrait img {
  display: block;
  max-width: 100%;
  max-height: 360px;
  width: auto;
  height: auto;
  border-radius: 8px;
}
.portrait figcaption,
.attribution {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
  line-height: 1.6;
}
.biography {
  min-width: 0;
  max-width: 72ch;
}
.biography > p {
  margin: 0;
  line-height: 1.7;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.biography .collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 6;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.biography > button {
  margin-top: 10px;
  border: 1px solid var(--base-color-border);
  padding: 8px 12px;
  background: transparent;
  border-radius: 10px;
  color: inherit;
  cursor: pointer;
}
.biography .attribution {
  margin-top: 12px;
}
:focus-visible {
  outline: 2px solid #435ee5;
  outline-offset: 3px;
}
@media (max-width: 620px) {
  .identity.with-photo {
    grid-template-columns: minmax(0, 1fr);
  }
  .portrait {
    grid-column: 1;
    grid-row: auto;
  }
}
</style>
