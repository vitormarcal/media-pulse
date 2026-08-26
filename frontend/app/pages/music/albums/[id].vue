<template>
  <main class="album-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Carregando...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este álbum.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <div ref="termsTarget">
        <AlbumPageHero
          :album-id="Number(data.id)"
          :editing="activeAction === 'terms'"
          :title="data.title"
          :artist-name="data.artistName"
          :artist-href="data.artistHref"
          :cover-url="data.coverUrl"
          :hero-meta="data.heroMeta"
          :terms="data.terms"
          @terms-changed="handleTermsChanged"
        />
      </div>

      <section class="album-actions" aria-labelledby="album-actions-title">
        <div class="actions-copy">
          <p class="actions-eyebrow">Curadoria</p>
          <h2 id="album-actions-title">Ações do álbum</h2>
          <p>Escolha o que deseja revisar ou organizar.</p>
        </div>

        <div class="action-list">
          <button
            v-for="action in albumActions"
            :key="action.id"
            type="button"
            class="action-button"
            :class="{ active: activeAction === action.id }"
            :aria-pressed="activeAction === action.id"
            @click="toggleAction(action.id)"
          >
            <span>{{ action.label }}</span>
            <small>{{ activeAction === action.id ? 'Fechar' : action.description }}</small>
          </button>
        </div>
      </section>

      <AlbumContextPanel :stats="data.stats" :recent-days="data.recentDays" />

      <div ref="listsTarget">
        <AlbumListsPanel
          :album-id="Number(data.id)"
          :lists="data.lists"
          :editing="activeAction === 'lists'"
          @changed="refresh"
        />
      </div>

      <div v-if="activeAction === 'metadata'" ref="metadataTarget">
        <AlbumMusicBrainzPanel :album-id="Number(data.id)" :link="data.musicBrainz" @applied="refresh" />
      </div>

      <div v-if="activeAction === 'duplicates'" ref="duplicatesTarget">
        <AlbumDuplicateTracksPanel
          :album-id="Number(data.id)"
          :album-title="data.title"
          :tracks="data.tracks"
          @merged="refresh"
        />
      </div>

      <MediaRatingPanel
        media-type="albums"
        :entity-id="Number(data.id)"
        :initial-rating="data.rating"
        title="Avaliação"
        description=""
        minimal
      />

      <MediaCommentsPanel
        :entity-id="Number(data.id)"
        media-type="albums"
        title="Comentários do álbum"
        description=""
        :comments="data.comments"
        empty-label="Nenhum comentário."
      />

      <AlbumTrackList :tracks="data.tracks" />
    </template>
  </main>
</template>

<script setup lang="ts">
import AlbumContextPanel from '~/components/music/AlbumContextPanel.vue'
import AlbumDuplicateTracksPanel from '~/components/music/AlbumDuplicateTracksPanel.vue'
import AlbumListsPanel from '~/components/music/AlbumListsPanel.vue'
import AlbumPageHero from '~/components/music/AlbumPageHero.vue'
import AlbumMusicBrainzPanel from '~/components/music/AlbumMusicBrainzPanel.vue'
import AlbumTrackList from '~/components/music/AlbumTrackList.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import { useAlbumPageData } from '~/composables/useAlbumPageData'

const route = useRoute()
const id = computed(() => String(route.params.id))
type AlbumAction = 'terms' | 'lists' | 'metadata' | 'duplicates'

const activeAction = ref<AlbumAction | null>(null)
const termsTarget = ref<HTMLElement | null>(null)
const listsTarget = ref<HTMLElement | null>(null)
const metadataTarget = ref<HTMLElement | null>(null)
const duplicatesTarget = ref<HTMLElement | null>(null)
const albumActions: Array<{ id: AlbumAction; label: string; description: string }> = [
  { id: 'terms', label: 'Editar gêneros e tags', description: 'Revisar marcações' },
  { id: 'lists', label: 'Organizar em listas', description: 'Adicionar ou remover' },
  { id: 'metadata', label: 'Enriquecer dados', description: 'Consultar MusicBrainz' },
  { id: 'duplicates', label: 'Revisar duplicatas', description: 'Sanear faixas' },
]

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

async function toggleAction(action: AlbumAction) {
  activeAction.value = activeAction.value === action ? null : action
  if (activeAction.value === null) return

  await nextTick()
  const targets: Record<AlbumAction, HTMLElement | null> = {
    terms: termsTarget.value,
    lists: listsTarget.value,
    metadata: metadataTarget.value,
    duplicates: duplicatesTarget.value,
  }
  targets[action]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<style scoped>
.album-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.album-actions {
  display: grid;
  grid-template-columns: minmax(12rem, 0.34fr) minmax(0, 1fr);
  gap: 24px;
  align-items: center;
  padding: 22px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
}

.actions-copy {
  display: grid;
  gap: 4px;
}

.actions-copy h2,
.actions-copy p {
  margin: 0;
}

.actions-copy h2 {
  font-size: 1.35rem;
  letter-spacing: -0.03em;
}

.actions-copy > p:last-child {
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}

.actions-eyebrow {
  color: var(--base-color-brand-red);
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.action-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.action-button {
  display: grid;
  gap: 4px;
  min-height: 68px;
  padding: 12px 14px;
  border: 2px solid transparent;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.action-button:hover {
  background: color-mix(in srgb, var(--base-color-surface-warm) 80%, white);
}

.action-button:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}

.action-button.active {
  border-color: var(--base-color-brand-red);
  background: white;
}

.action-button span {
  font-size: 0.82rem;
  font-weight: 700;
}

.action-button small {
  color: var(--base-color-text-secondary);
  font-size: 0.7rem;
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

@media (max-width: 720px) {
  .album-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .album-actions {
    grid-template-columns: 1fr;
    padding: 18px;
  }

  .action-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 420px) {
  .action-list {
    grid-template-columns: 1fr;
  }
}
</style>
