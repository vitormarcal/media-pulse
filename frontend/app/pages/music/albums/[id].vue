<template>
  <main class="album-page">
    <NuxtLink class="back-link" to="/music">← Música</NuxtLink>
    <div v-if="status === 'pending' && !data" class="panel state-card"><p>Carregando álbum...</p></div>
    <div v-else-if="error && !data" class="panel state-card" role="alert">
      <p>Não foi possível carregar este álbum.</p>
    </div>
    <template v-else-if="data">
      <AlbumPageHero
        :title="data.title"
        :artists="data.artists"
        :cover-url="data.coverUrl"
        :hero-meta="data.heroMeta"
        :context="data.context"
      >
        <div v-if="owner.authenticated || data.comments.length || data.rating" class="personal-memory">
          <MediaCommentsPanel
            :key="data.id"
            :entity-id="Number(data.id)"
            media-type="albums"
            title="Comentários"
            description=""
            empty-label="Nenhum comentário."
            :comments="data.comments"
            compact
            editorial
          >
            <template #meta>
              <MediaRatingPanel
                v-if="owner.authenticated || data.rating"
                media-type="albums"
                :entity-id="Number(data.id)"
                :initial-rating="data.rating"
                compact
                minimal
                label="Nota do álbum"
              />
            </template>
          </MediaCommentsPanel>
        </div>
      </AlbumPageHero>
      <section class="panel">
        <AlbumTrackList :tracks="data.tracks">
          <button
            v-owner
            class="section-action"
            type="button"
            :aria-expanded="activeAction === 'duplicates'"
            @click="toggleAction('duplicates')"
          >
            {{ activeAction === 'duplicates' ? 'Fechar revisão' : 'Revisar duplicatas' }}
          </button>
        </AlbumTrackList>
        <AlbumDuplicateTracksPanel
          v-if="owner.authenticated && activeAction === 'duplicates'"
          class="context-editor"
          :album-id="Number(data.id)"
          :album-title="data.title"
          :tracks="data.tracks"
          @merged="refresh"
        />
      </section>
      <AlbumContextPanel class="panel" :stats="data.stats" :recent-days="data.recentDays" />
      <section
        v-if="
          owner.authenticated ||
          data.terms.visibleCount ||
          data.lists.length ||
          Object.keys(data.context?.links || {}).length
        "
        class="panel about-panel"
        aria-labelledby="about-album"
      >
        <div class="section-head">
          <h2 id="about-album">Sobre o álbum</h2>
          <button
            v-owner
            type="button"
            class="section-action"
            :aria-expanded="activeAction === 'metadata'"
            aria-controls="album-metadata-panel"
            @click="toggleAction('metadata')"
          >
            {{ activeAction === 'metadata' ? 'Fechar atualização' : 'Atualizar dados' }}
          </button>
        </div>
        <div v-if="owner.authenticated" v-show="activeAction === 'metadata'" id="album-metadata-panel">
          <AlbumMusicBrainzPanel :album-id="Number(data.id)" :link="data.musicBrainz" @applied="refresh" />
        </div>
        <div v-if="data.terms.visibleCount || owner.authenticated" class="about-group">
          <div class="section-head">
            <h3>Gêneros e tags</h3>
            <button
              v-owner
              type="button"
              class="section-action"
              :aria-expanded="activeAction === 'terms'"
              @click="toggleAction('terms')"
            >
              {{ activeAction === 'terms' ? 'Concluir edição' : 'Editar' }}
            </button>
          </div>
          <AlbumTermsPanel
            :album-id="Number(data.id)"
            :terms="data.terms"
            :editing="activeAction === 'terms'"
            embedded
            @changed="handleTermsChanged"
          />
        </div>
        <div v-if="data.lists.length || owner.authenticated" class="about-group">
          <div class="section-head">
            <h3>Listas</h3>
            <button
              v-owner
              type="button"
              class="section-action"
              :aria-expanded="activeAction === 'lists'"
              @click="toggleAction('lists')"
            >
              {{ activeAction === 'lists' ? 'Concluir organização' : 'Organizar' }}
            </button>
          </div>
          <AlbumListsPanel
            :album-id="Number(data.id)"
            :lists="data.lists"
            :editing="activeAction === 'lists'"
            embedded
            @changed="refresh"
          />
          <p v-if="!data.lists.length && activeAction !== 'lists'" class="muted">
            Este álbum ainda não está em uma lista.
          </p>
        </div>
        <nav v-if="Object.keys(data.context?.links || {}).length" class="external-links" aria-label="Links do álbum">
          <a
            v-for="(url, label) in data.context?.links"
            :key="label"
            :href="url"
            target="_blank"
            rel="noopener noreferrer"
            >{{ label }} ↗</a
          >
        </nav>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import AlbumContextPanel from '~/components/music/AlbumContextPanel.vue'
import AlbumDuplicateTracksPanel from '~/components/music/AlbumDuplicateTracksPanel.vue'
import AlbumListsPanel from '~/components/music/AlbumListsPanel.vue'
import AlbumPageHero from '~/components/music/AlbumPageHero.vue'
import AlbumMusicBrainzPanel from '~/components/music/AlbumMusicBrainzPanel.vue'
import AlbumTermsPanel from '~/components/music/AlbumTermsPanel.vue'
import AlbumTrackList from '~/components/music/AlbumTrackList.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import { useAlbumPageData } from '~/composables/useAlbumPageData'

const owner = useOwnerAuth()

const route = useRoute()
const id = computed(() => String(route.params.id))
type AlbumAction = 'terms' | 'lists' | 'metadata' | 'duplicates'

const activeAction = ref<AlbumAction | null>(null)
const { data, error, status, refresh } = await useAlbumPageData(id.value)

useHead(() => ({
  title: data.value ? `${data.value.title} · Media Pulse` : 'Álbum · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `${data.value.title} de ${data.value.artistName}.`
        : 'Página interna de álbum no Media Pulse.',
    },
  ],
}))

async function handleTermsChanged() {
  await refresh()
}

function toggleAction(action: AlbumAction) {
  activeAction.value = activeAction.value === action ? null : action
}
</script>

<style scoped>
.album-page {
  display: grid;
  gap: 32px;
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0 80px;
}
.panel {
  min-width: 0;
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
}
.back-link {
  width: fit-content;
  color: var(--base-color-text-secondary);
}
.section-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
h2,
h3,
p {
  margin: 0;
}
h2 {
  font-size: 1.35rem;
  letter-spacing: -0.03em;
}
h3 {
  font-size: 1rem;
}
.about-panel,
.about-group {
  display: grid;
  gap: 20px;
}
.about-group {
  padding-top: 24px;
  border-top: 1px solid var(--base-color-border);
}
.section-action {
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  min-height: 44px;
  font: inherit;
  font-size: 0.82rem;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-primary);
  cursor: pointer;
}
.section-action:focus-visible,
.back-link:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
.context-editor {
  margin-top: 24px;
}
.muted,
.state-card {
  color: var(--base-color-text-secondary);
}
@media (max-width: 600px) {
  .album-page {
    width: calc(100% - 20px);
    gap: 24px;
    padding-top: 20px;
  }
}

.external-links {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 0.85rem;
}
.external-links a {
  text-decoration: underline;
  text-underline-offset: 4px;
}
</style>
