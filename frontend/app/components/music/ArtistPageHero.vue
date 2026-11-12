<template>
  <section class="artist-hero" :class="{ 'without-image': !resolvedCoverUrl, 'without-biography': !biography }">
    <div class="copy">
      <h1>{{ title }}</h1>
      <p v-if="identityLine" class="identity">{{ identityLine }}</p>
      <dl v-if="profile?.lifeSpanBegin || profile?.lifeSpanEnd" class="dates">
        <div v-if="profile.lifeSpanBegin">
          <dt>
            {{
              profile.type === 'Person'
                ? 'Nascimento'
                : ['Group', 'Orchestra', 'Choir'].includes(profile.type || '')
                  ? 'Formação'
                  : 'Início'
            }}
          </dt>
          <dd>{{ formatDate(profile.lifeSpanBegin) }}</dd>
        </div>
        <div v-if="profile.lifeSpanEnd">
          <dt>
            {{
              profile.type === 'Person'
                ? 'Falecimento'
                : ['Group', 'Orchestra', 'Choir'].includes(profile.type || '')
                  ? 'Encerramento'
                  : 'Fim'
            }}
          </dt>
          <dd>{{ formatDate(profile.lifeSpanEnd) }}</dd>
        </div>
      </dl>
    </div>
    <div v-if="resolvedCoverUrl" class="portrait">
      <button
        class="portrait-button"
        type="button"
        :aria-label="`Ampliar imagem de ${title}`"
        @click="gallery?.open(0)"
      >
        <img :src="resolvedCoverUrl" :alt="title" @error="failed = true" />
      </button>
      <button type="button" class="view-image" @click="gallery?.open(0)">Ver imagem</button>
      <p v-if="profile?.image" class="attribution">
        <a
          v-if="profile.image.descriptionUrl"
          :href="profile.image.descriptionUrl"
          target="_blank"
          rel="noopener noreferrer"
          >Wikimedia Commons</a
        ><span v-else>Wikimedia Commons</span>
        <span v-if="profile.image.author"> · {{ profile.image.author }}</span>
        <template v-if="profile.image.license">
          ·
          <a
            v-if="profile.image.licenseUrl"
            :href="profile.image.licenseUrl"
            target="_blank"
            rel="noopener noreferrer"
            >{{ profile.image.license }}</a
          ><span v-else>{{ profile.image.license }}</span></template
        >
      </p>
    </div>
    <WikipediaIntroduction class="biography" v-if="biography" :introduction="biography.introduction" />
    <MediaImageGallery ref="gallery" :images="coverUrl && !failed ? [coverUrl] : []" :title="title" viewer-only />
  </section>
</template>
<script setup lang="ts">
import WikipediaIntroduction from '~/components/music/WikipediaIntroduction.vue'
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
import type { ArtistBiography, ArtistMusicBrainzProfile } from '~/types/music'
const props = defineProps<{
  title: string
  coverUrl: string | null
  profile: ArtistMusicBrainzProfile | null
  biography: ArtistBiography | null
}>()
const gallery = ref<InstanceType<typeof MediaImageGallery> | null>(null)
const failed = ref(false)
watch(
  () => props.coverUrl,
  () => {
    failed.value = false
  },
)
const { resolveMediaUrl } = useMediaUrl()
const resolvedCoverUrl = computed(() => (failed.value ? null : resolveMediaUrl(props.coverUrl)))
const typeLabels: Record<string, string> = {
  Group: 'Grupo',
  Orchestra: 'Orquestra',
  Choir: 'Coro',
  Character: 'Personagem',
}
const identityLine = computed(() => {
  const profile = props.profile
  if (!profile) return ''
  let country = profile.countryCode
  if (country) {
    try {
      country = new Intl.DisplayNames(['pt-BR'], { type: 'region' }).of(country) || country
    } catch {
      /* Preserve unknown country codes. */
    }
  }
  return [
    ...new Set(
      [profile.type ? typeLabels[profile.type] : null, profile.beginAreaName || profile.areaName, country].filter(
        Boolean,
      ),
    ),
  ].join(' · ')
})
function formatDate(value: string) {
  if (!/^\d{4}(?:-\d{2}){0,2}$/.test(value) || value.length === 4) return value
  const [year, month, day] = value.split('-').map(Number)
  return new Intl.DateTimeFormat('pt-BR', {
    year: 'numeric',
    month: 'short',
    ...(day ? { day: '2-digit' as const } : {}),
  }).format(new Date(year!, month! - 1, day || 1))
}
</script>
<style scoped>
.artist-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(200px, 300px);
  grid-template-areas: 'copy portrait' 'bio portrait';
  grid-template-rows: auto 1fr;
  gap: 32px;
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
  align-items: start;
}
.artist-hero.without-image {
  grid-template-columns: minmax(0, 1fr);
}
.copy {
  grid-area: copy;
  min-width: 0;
}
h1 {
  margin: 0;
  font-size: clamp(2rem, 4.5vw, 3.5rem);
  line-height: 1.06;
  letter-spacing: -0.045em;
  overflow-wrap: anywhere;
}
.identity {
  margin: 16px 0 0;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}
.dates {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  margin: 16px 0 0;
  font-size: 0.85rem;
}
.dates div {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
dt {
  color: var(--base-color-text-secondary);
}
dd {
  margin: 0;
}
.portrait {
  grid-area: portrait;
  min-width: 0;
}
.portrait-button {
  display: block;
  width: fit-content;
  margin: 0 auto;
  padding: 0;
  border: 0;
  border-radius: 20px;
  background: transparent;
  cursor: zoom-in;
}
img {
  display: block;
  max-width: 100%;
  max-height: 360px;
  width: auto;
  height: auto;
  border-radius: 20px;
}
.view-image {
  margin-top: 12px;
  padding: 10px 14px;
  min-height: 44px;
  border: 0;
  border-radius: 16px;
  font: inherit;
  font-size: 0.85rem;
  color: var(--base-color-text-primary);
  background: var(--base-color-surface-soft);
  cursor: pointer;
}
.attribution {
  margin: 12px 0 0;
  font-size: 0.72rem;
  line-height: 1.6;
  color: var(--base-color-text-secondary);
  overflow-wrap: anywhere;
}
a {
  color: inherit;
  text-decoration: underline;
  text-underline-offset: 3px;
}
button:focus-visible,
a:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
@media (max-width: 720px) {
  .artist-hero {
    grid-template-columns: 1fr;
    gap: 24px;
  }
  .portrait {
    max-width: 300px;
    width: 100%;
    justify-self: center;
  }
}

.artist-hero .biography {
  grid-area: bio;
  min-width: 0;
  margin-top: 0;
}
.artist-hero.without-image {
  grid-template-areas: 'copy' 'bio';
}
.artist-hero.without-biography {
  grid-template-areas: 'copy portrait';
  grid-template-rows: auto;
}
.artist-hero.without-image.without-biography {
  grid-template-areas: 'copy';
}
@media (max-width: 720px) {
  .artist-hero {
    grid-template-areas: 'copy' 'portrait' 'bio';
    grid-template-rows: auto;
  }
  .artist-hero.without-biography {
    grid-template-areas: 'copy' 'portrait';
  }
}
</style>
