<template>
  <main class="person-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página da pessoa...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar esta pessoa.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <section class="person-hero" :style="heroShellStyle">
        <div class="copy">
          <NuxtLink class="back-link" to="/movies"> Voltar ao catálogo </NuxtLink>

          <p class="eyebrow">Pessoa</p>
          <h1>{{ data.name }}</h1>
          <p class="intro">{{ heroIntro }}</p>

          <div class="meta-list">
            <span v-for="item in data.heroMeta" :key="item" class="meta-pill">{{ item }}</span>
          </div>

          <div v-if="heroFacts.length" class="hero-facts">
            <div v-for="fact in heroFacts" :key="fact.label" class="fact-card">
              <span>{{ fact.label }}</span>
              <strong>{{ fact.value }}</strong>
            </div>
          </div>
        </div>

        <div class="portrait-card">
          <div class="portrait-frame">
            <img v-if="resolveMediaUrl(data.profileUrl)" :src="resolveMediaUrl(data.profileUrl)" :alt="data.name" />
            <div v-else class="portrait-fallback">{{ data.name.slice(0, 1) }}</div>
          </div>
        </div>
      </section>

      <section v-if="biography || aliasList.length || profileLinks.length" class="person-notes">
        <div v-if="biography" class="biography-card">
          <p class="notes-eyebrow">Perfil TMDb</p>
          <p class="biography-text">{{ biography }}</p>
        </div>

        <div class="aside-stack">
          <article v-if="aliasList.length" class="detail-card">
            <p class="notes-eyebrow">Também aparece como</p>
            <div class="alias-list">
              <span v-for="alias in aliasList" :key="alias" class="alias-pill">{{ alias }}</span>
            </div>
          </article>

          <article v-if="profileLinks.length" class="detail-card">
            <p class="notes-eyebrow">Fora do catálogo</p>
            <div class="link-list">
              <a
                v-for="item in profileLinks"
                :key="item.href"
                class="profile-link"
                :href="item.href"
                target="_blank"
                rel="noreferrer"
              >
                {{ item.label }}
              </a>
            </div>
          </article>
        </div>
      </section>

      <MoviesLibraryGrid
        eyebrow="Catálogo local"
        :title="gridTitle"
        :description="gridDescription"
        :summary="gridSummary"
        :items="data.movies"
        layout="masonry"
        empty-message="Nenhum filme local apareceu ligado a esta pessoa."
      />

      <ShowsLibraryGrid
        eyebrow="Séries locais"
        :title="showsTitle"
        :description="''"
        :summary="showsSummary"
        :items="data.shows"
        empty-message="Nenhuma série local apareceu ligada a esta pessoa."
      />

      <PersonScreenographyPanel :person="data" @added="handleCatalogAdded" />
    </template>
  </main>
</template>

<script setup lang="ts">
import PersonScreenographyPanel from '~/components/people/PersonScreenographyPanel.vue'
import MoviesLibraryGrid from '~/components/movies/MoviesLibraryGrid.vue'
import ShowsLibraryGrid from '~/components/shows/ShowsLibraryGrid.vue'
import { usePersonPageData } from '~/composables/usePersonPageData'
import { formatAbsoluteDate, formatShortNumber } from '~/utils/formatting'

const route = useRoute()
const { resolveMediaUrl } = useMediaUrl()
const slug = computed(() => String(route.params.slug))

const { data, error, status, refresh } = await usePersonPageData(slug.value)
const heroImageUrl = computed(() =>
  resolveMediaUrl(
    data.value?.movies[0]?.imageUrl ?? data.value?.tmdbProfile?.profileUrl ?? data.value?.profileUrl ?? null,
  ),
)
const heroShellStyle = computed(() =>
  heroImageUrl.value
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 243, 238, 0.97)), radial-gradient(circle at top right, rgba(230, 0, 35, 0.1), transparent 28%), url("${heroImageUrl.value}")`,
      }
    : undefined,
)

const heroIntro = computed(() => {
  if (!data.value) return ''
  if (data.value.tmdbProfile?.biography) {
    return data.value.tmdbProfile.biography
  }

  return data.value.roles.length ? data.value.roles.join(' · ') : 'Pessoa atravessando o catálogo audiovisual.'
})

const gridTitle = computed(() => (data.value ? `Os filmes locais ligados a ${data.value.name}` : 'Filmes desta pessoa'))
const gridDescription = computed(() => '')
const gridSummary = computed(() =>
  data.value
    ? `${data.value.stats.movieCount} filmes locais e ${data.value.stats.watchedMoviesCount} com sessão registrada.`
    : 'Uma nova porta de entrada para a biblioteca.',
)
const showsTitle = computed(() =>
  data.value ? `As séries locais ligadas a ${data.value.name}` : 'Séries desta pessoa',
)
const showsSummary = computed(() =>
  data.value
    ? `${data.value.stats.showCount} séries locais e ${data.value.stats.watchedShowsCount} com watch registrado.`
    : 'A presença desta pessoa no arquivo de séries.',
)

const biography = computed(() => {
  const value = data.value?.tmdbProfile?.biography?.trim()
  if (!value) return ''
  return value.length > 520 ? `${value.slice(0, 517).trimEnd()}...` : value
})

const heroFacts = computed(() => {
  if (!data.value) return []

  const facts = [
    data.value.tmdbProfile?.knownForDepartment
      ? { label: 'Foco', value: data.value.tmdbProfile.knownForDepartment }
      : null,
    data.value.tmdbProfile?.birthday
      ? { label: 'Nascimento', value: formatAbsoluteDate(data.value.tmdbProfile.birthday) }
      : null,
    data.value.tmdbProfile?.placeOfBirth ? { label: 'Origem', value: data.value.tmdbProfile.placeOfBirth } : null,
    typeof data.value.tmdbProfile?.popularity === 'number'
      ? { label: 'Pulso TMDb', value: formatShortNumber(data.value.tmdbProfile.popularity) }
      : null,
    data.value.stats.showCount > 0 ? { label: 'Séries', value: String(data.value.stats.showCount) } : null,
  ]

  return facts.filter(Boolean) as Array<{ label: string; value: string }>
})

const aliasList = computed(() => data.value?.tmdbProfile?.aliases.slice(0, 8) ?? [])

const profileLinks = computed(() => {
  if (!data.value?.tmdbProfile) return []

  return [
    data.value.tmdbProfile.homepage ? { label: 'Site oficial', href: data.value.tmdbProfile.homepage } : null,
    data.value.tmdbProfile.imdbId
      ? { label: 'IMDb', href: `https://www.imdb.com/name/${data.value.tmdbProfile.imdbId}/` }
      : null,
    data.value.tmdbId ? { label: 'TMDb', href: `https://www.themoviedb.org/person/${data.value.tmdbId}` } : null,
  ].filter(Boolean) as Array<{ label: string; href: string }>
})

async function handleCatalogAdded() {
  await refresh()
}

useHead(() => ({
  title: data.value ? `${data.value.name} · Pessoas · Media Pulse` : 'Pessoa · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `Pessoa atravessando filmes e séries no catálogo audiovisual do Media Pulse.`
        : 'Página de pessoa do catálogo audiovisual do Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.person-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.state-card {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
}

.state-card.error {
  color: #7a1414;
}

pre {
  margin: 12px 0 0;
  white-space: pre-wrap;
}

.person-hero {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 0.9fr) minmax(20rem, 0.7fr);
  align-items: end;
  padding: clamp(24px, 4vw, 36px);
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
  gap: 12px;
  align-content: end;
}

.back-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
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
  letter-spacing: -0.075em;
}

.intro {
  max-width: 44rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.62;
}

.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 2px;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.portrait-card {
  display: grid;
}

.hero-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  max-width: 40rem;
  margin-top: 10px;
}

.fact-card {
  display: grid;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 24px;
  background: color-mix(in srgb, rgba(255, 255, 255, 0.84) 78%, var(--base-color-surface-warm));
}

.fact-card span,
.notes-eyebrow {
  margin: 0;
  color: var(--base-color-text-muted);
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.fact-card strong {
  font-size: 0.98rem;
  line-height: 1.35;
}

.portrait-frame {
  overflow: hidden;
  min-height: 24rem;
  border: 8px solid #ffffff;
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 243, 238, 0.98));
}

.portrait-frame img,
.portrait-fallback {
  width: 100%;
  height: 100%;
  min-height: 24rem;
  object-fit: cover;
}

.portrait-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 4rem;
  font-weight: 700;
}

.person-notes {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(18rem, 0.8fr);
  gap: 22px;
}

.biography-card,
.detail-card {
  display: grid;
  gap: 14px;
  padding: 22px 24px;
  border-radius: 28px;
  background: radial-gradient(circle at top right, rgba(230, 0, 35, 0.06), transparent 26%), rgba(255, 255, 255, 0.82);
}

.biography-text {
  margin: 0;
  color: var(--base-color-text-primary);
  font-size: 1rem;
  line-height: 1.68;
}

.aside-stack {
  display: grid;
  gap: 18px;
  align-content: start;
}

.alias-list,
.link-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.alias-pill {
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.82rem;
}

.profile-link {
  padding: 8px 12px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.82rem;
}

@media (max-width: 980px) {
  .person-hero {
    grid-template-columns: 1fr;
  }

  .person-notes {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .hero-facts {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .person-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
