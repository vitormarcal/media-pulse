<template>
  <main class="album-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página do álbum...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este álbum.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <AlbumPageHero
        :album-id="Number(data.id)"
        :editing="editMode"
        :title="data.title"
        :artist-name="data.artistName"
        :artist-href="data.artistHref"
        :cover-url="data.coverUrl"
        :hero-meta="data.heroMeta"
        :terms="data.terms"
        @terms-changed="handleTermsChanged"
        @toggle-editing="toggleEditing"
      />

      <AlbumContextPanel :stats="data.stats" :recent-days="data.recentDays" />

      <AlbumListsPanel :album-id="Number(data.id)" :lists="data.lists" :editing="editMode" @changed="refresh" />

      <AlbumMusicBrainzPanel v-if="editMode" :album-id="Number(data.id)" :link="data.musicBrainz" @applied="refresh" />

      <MediaRatingPanel
        media-type="albums"
        :entity-id="Number(data.id)"
        :initial-rating="data.rating"
        title="Força real desse álbum"
        description="Use a nota para separar disco só bom de disco que realmente continua pedindo retorno."
      />

      <MediaCommentsPanel
        :entity-id="Number(data.id)"
        media-type="albums"
        title="Impressões do álbum"
        description="Espaço para primeira audição, redescobertas tardias e qualquer mudança de leitura conforme o disco volta para a rotação."
        :comments="data.comments"
        empty-label="Nenhuma impressão manual registrada para este álbum ainda."
      />

      <AlbumTrackList :tracks="data.tracks" />
    </template>
  </main>
</template>

<script setup lang="ts">
import AlbumContextPanel from '~/components/music/AlbumContextPanel.vue'
import AlbumListsPanel from '~/components/music/AlbumListsPanel.vue'
import AlbumPageHero from '~/components/music/AlbumPageHero.vue'
import AlbumMusicBrainzPanel from '~/components/music/AlbumMusicBrainzPanel.vue'
import AlbumTrackList from '~/components/music/AlbumTrackList.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import { useAlbumPageData } from '~/composables/useAlbumPageData'

const route = useRoute()
const id = computed(() => String(route.params.id))
const editMode = ref(false)

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

function toggleEditing() {
  editMode.value = !editMode.value
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
}
</style>
