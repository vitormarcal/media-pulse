<template>
  <section class="artist-hero">
    <NuxtLink class="back-link" to="/music"> Voltar para música </NuxtLink>

    <div class="hero-grid" :style="heroShellStyle">
      <div class="copy">
        <p class="eyebrow">Artista</p>
        <h1>{{ title }}</h1>
        <p v-if="identityLine" class="subtitle">{{ identityLine }}</p>

        <div class="meta-list">
          <span v-if="activityPeriod" class="meta-pill">{{ activityPeriod }}</span>
          <span v-for="item in heroMeta" :key="item" class="meta-pill">{{ item }}</span>
          <span v-for="genre in visibleGenres.slice(0, 3)" :key="genre.id" class="meta-pill">{{ genre.name }}</span>
        </div>

        <div v-if="profile?.aliases.length" class="detail-row">
          <p class="detail-label">Também conhecido como</p>
          <p class="detail-copy">{{ shownAliases.map((alias) => alias.name).join(' · ') }}</p>
          <button
            v-if="profile.aliases.length > 5"
            type="button"
            class="text-action"
            @click="aliasesExpanded = !aliasesExpanded"
          >
            {{ aliasesExpanded ? 'Mostrar menos' : 'Ver todos' }}
          </button>
        </div>

        <div v-if="profile?.links.length" class="external-links">
          <a v-for="link in profile.links" :key="link.type" :href="link.url" target="_blank" rel="noreferrer">{{
            linkLabels[link.type]
          }}</a>
        </div>

        <NuxtLink
          class="merge-albums-link"
          :to="{
            path: '/music/admin/album-duplicates',
            query: { artist: title, artistId: String(artistId) },
          }"
        >
          Revisar álbuns duplicados
        </NuxtLink>
      </div>

      <div class="cover-frame">
        <img v-if="resolvedCoverUrl" :src="resolvedCoverUrl" :alt="title" />
        <div v-else class="cover-fallback">{{ title.slice(0, 1) }}</div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { AlbumTermDto, ArtistMusicBrainzProfile } from '~/types/music'
const props = defineProps<{
  artistId: number
  title: string
  coverUrl: string | null
  heroMeta: string[]
  profile: ArtistMusicBrainzProfile | null
  genres: AlbumTermDto[]
}>()

const aliasesExpanded = ref(false)
const shownAliases = computed(() => props.profile?.aliases.slice(0, aliasesExpanded.value ? undefined : 5) ?? [])
const visibleGenres = computed(() => props.genres.filter((genre) => genre.active))
const typeLabels: Record<string, string> = {
  Person: 'Pessoa',
  Group: 'Grupo',
  Orchestra: 'Orquestra',
  Choir: 'Coro',
  Character: 'Personagem',
  Other: 'Artista',
}
const linkLabels = { OFFICIAL: 'Site oficial', WIKIPEDIA: 'Wikipedia', DISCOGS: 'Discogs', BANDCAMP: 'Bandcamp' }
const countryName = computed(() => {
  if (!props.profile?.countryCode) return null
  try {
    return (
      new Intl.DisplayNames(['pt-BR'], { type: 'region' }).of(props.profile.countryCode) ?? props.profile.countryCode
    )
  } catch {
    return props.profile.countryCode
  }
})
const identityLine = computed(() => {
  if (!props.profile) return null
  const type = props.profile.type ? (typeLabels[props.profile.type] ?? props.profile.type) : null
  const place = props.profile.beginAreaName ?? props.profile.areaName ?? countryName.value
  return [type, place].filter(Boolean).join(' · ') || null
})
const activityPeriod = computed(() => {
  if (!props.profile?.lifeSpanBegin && !props.profile?.lifeSpanEnd) return null
  return `${props.profile.lifeSpanBegin ?? '?'}–${props.profile.lifeSpanEnd ?? (props.profile.lifeSpanEnded ? '?' : 'presente')}`
})

const { resolveMediaUrl } = useMediaUrl()
const resolvedCoverUrl = computed(() => resolveMediaUrl(props.coverUrl))
const heroShellStyle = computed(() =>
  resolvedCoverUrl.value
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 243, 238, 0.97)), radial-gradient(circle at top right, rgba(230, 0, 35, 0.1), transparent 28%), url("${resolvedCoverUrl.value}")`,
      }
    : undefined,
)
</script>

<style scoped>
.artist-hero {
  display: grid;
  gap: 18px;
}

.back-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(16rem, 0.72fr);
  gap: 28px;
  padding: clamp(24px, 4vw, 42px);
  border-radius: 40px;
  background-image:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98));
  background-size: cover;
  background-position: center;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
}

.copy {
  display: grid;
  align-content: end;
  gap: 12px;
}

.eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 5.8rem);
  line-height: 0.92;
  letter-spacing: -0.07em;
}

.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.subtitle,
.detail-copy {
  margin: 0;
  color: var(--base-color-text-secondary);
}
.detail-row {
  display: grid;
  gap: 6px;
  margin-top: 4px;
}
.detail-label {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.detail-copy {
  max-width: 42rem;
  line-height: 1.5;
}
.text-action {
  width: fit-content;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
}
.external-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.external-links a {
  padding: 9px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
  font-weight: 700;
  text-decoration: none;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.merge-albums-link {
  width: fit-content;
  margin-top: 4px;
  padding: 10px 16px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
  font-weight: 700;
  text-decoration: none;
}

.merge-albums-link:hover {
  background: var(--base-color-border);
}

.merge-albums-link:focus-visible {
  outline: 3px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}

.cover-frame {
  min-height: 28rem;
  overflow: hidden;
  border-radius: 28px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.cover-frame img,
.cover-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  display: grid;
  place-items: center;
  font-size: 5rem;
  color: var(--base-color-text-secondary);
  font-weight: 700;
}

@media (max-width: 980px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .cover-frame {
    min-height: 22rem;
  }
}
</style>
