<template>
  <main class="game-page">
    <div v-if="status === 'pending' && !data" class="state-card">
      <p>Carregando...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este game.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <GamePageHero
        ref="gameHero"
        :title="data.title"
        :subtitle="heroSubtitle"
        :description="data.description"
        :gallery="data.gallery.slice(0, 4)"
        :image-count="data.gallery.length"
        :hero-meta="data.heroMeta"
        :identifiers="data.identifiers"
        @view-image="galleryViewer?.open($event)"
      />

      <MediaImageGallery
        ref="galleryViewer"
        :images="data.gallery"
        :title="data.title"
        viewer-only
        :busy="imageBusy"
        @image-change="handleGalleryImageChanged"
        @closed="handleGalleryClosed"
      >
        <template v-if="owner.authenticated" #actions="{ image, index }">
          <div class="image-actions" :aria-label="`Ações da imagem ${index + 1}`">
            <span v-if="image === data.coverUrl" class="cover-label">Capa atual</span>
            <button v-else type="button" :disabled="imageBusy" @click="manageImage(image, false)">
              Definir como capa
            </button>
            <button type="button" :disabled="imageBusy" @click="confirmRemoval = image">Remover imagem</button>
            <div v-if="confirmRemoval === image" class="remove-confirmation">
              <p>
                Remover esta imagem do jogo?{{
                  image === data.coverUrl ? ' Outra imagem será usada como capa, se disponível.' : ''
                }}
              </p>
              <button type="button" :disabled="imageBusy" @click="manageImage(image, true)">Confirmar remoção</button>
              <button type="button" :disabled="imageBusy" @click="confirmRemoval = ''">Cancelar</button>
            </div>
            <p v-if="imageBusy" class="image-feedback" role="status">Salvando alteração…</p>
            <p v-if="imageError" class="image-feedback image-error" role="alert">{{ imageError }}</p>
            <p v-if="imageMessage" class="image-feedback" role="status">{{ imageMessage }}</p>
          </div>
        </template>
      </MediaImageGallery>
      <p v-if="imageMessage && !data.gallery.length" role="status">{{ imageMessage }}</p>

      <MediaCoverUpload
        v-if="owner.authenticated"
        media-type="games"
        :entity-id="data.gameId"
        @saved="handleCoverSaved"
      />

      <GameImagesPanel
        v-if="owner.authenticated"
        :key="data.gameId"
        :game-id="data.gameId"
        :game-title="data.title"
        @imported="refresh()"
      />

      <section class="context-grid">
        <article class="context-card">
          <p>Sessões</p>
          <strong>{{ data.stats.totalSessions }}</strong>
          <span>{{ data.stats.latestSessionRelative }}</span>
        </article>
      </section>

      <MediaRatingPanel
        media-type="games"
        :entity-id="data.gameId"
        :initial-rating="data.rating"
        title="Nota do game"
        minimal
      />

      <GameAddSessionPanel v-if="owner.authenticated" :game-id="data.gameId" @created="handleSessionChanged" />

      <MediaCommentsPanel
        :entity-id="data.gameId"
        media-type="games"
        title="Comentários do game"
        description=""
        :comments="data.comments"
        empty-label="Nenhum comentário."
      />

      <GameSessionsTimeline :game-id="data.gameId" :sessions="data.sessions" @changed="handleSessionChanged" />
    </template>
  </main>
</template>

<script setup lang="ts">
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
import GameImagesPanel from '~/components/games/GameImagesPanel.vue'
import GameAddSessionPanel from '~/components/games/GameAddSessionPanel.vue'
import GamePageHero from '~/components/games/GamePageHero.vue'
import GameSessionsTimeline from '~/components/games/GameSessionsTimeline.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import { useGamePageData } from '~/composables/useGamePageData'

import MediaCoverUpload from '~/components/media/MediaCoverUpload.vue'

const gameHero = ref<InstanceType<typeof GamePageHero> | null>(null)
const galleryViewer = ref<InstanceType<typeof MediaImageGallery> | null>(null)
const owner = useOwnerAuth()
const config = useRuntimeConfig()
const imageBusy = ref(false)
const imageError = ref('')
const imageMessage = ref('')
const confirmRemoval = ref('')

const route = useRoute()
const slug = computed(() => String(route.params.slug))

const { data, error, status, refresh } = await useGamePageData(slug.value)

const heroSubtitle = computed(() => {
  if (!data.value) return null
  if (data.value.originalTitle !== data.value.title && data.value.year) {
    return `${data.value.originalTitle} · ${data.value.year}`
  }
  if (data.value.originalTitle !== data.value.title) return data.value.originalTitle
  return data.value.year ? String(data.value.year) : null
})

function handleGalleryImageChanged() {
  confirmRemoval.value = ''
  imageError.value = ''
}

async function handleGalleryClosed(needsFocusFallback: boolean) {
  confirmRemoval.value = ''
  imageError.value = ''
  await nextTick()
  if (needsFocusFallback) gameHero.value?.focusGallery()
}

async function handleSessionChanged() {
  await refresh()
}

async function handleCoverSaved(coverUrl: string) {
  if (!data.value) return
  data.value.coverUrl = coverUrl
  data.value.gallery = [coverUrl, ...data.value.gallery.filter((url) => url !== coverUrl)]
  await refresh()
}

async function manageImage(url: string, remove: boolean) {
  if (!data.value || imageBusy.value) return
  imageBusy.value = true
  imageError.value = ''
  imageMessage.value = ''
  try {
    const result = await $fetch<{ coverUrl: string | null }>(
      `/api/games/${data.value.gameId}/images${remove ? '' : '/primary'}`,
      { baseURL: config.public.apiBase, method: remove ? 'DELETE' : 'POST', body: { url } },
    )
    data.value.coverUrl = result.coverUrl
    const remaining = data.value.gallery.filter((image) => !remove || image !== url)
    data.value.gallery = result.coverUrl
      ? [result.coverUrl, ...remaining.filter((image) => image !== result.coverUrl)]
      : remaining
    confirmRemoval.value = ''
    imageMessage.value = remove ? 'Imagem removida do jogo.' : 'Capa atualizada.'
    await refresh()
  } catch {
    imageError.value = 'Não foi possível concluir a alteração. Tente novamente.'
  } finally {
    imageBusy.value = false
  }
}

useHead(() => ({
  title: data.value ? `${data.value.title} · Media Pulse` : 'Game · Media Pulse',
  meta: [{ name: 'description', content: data.value?.description || 'Página interna de game no Media Pulse.' }],
}))
</script>

<style scoped>
.game-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.image-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  justify-content: center;
  color: #e5e5e0;
  padding: 8px 0;
}
.image-actions button {
  min-height: 44px;
  padding: 8px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.75rem;
  cursor: pointer;
}
.image-actions button:disabled {
  opacity: 0.5;
  cursor: default;
}
.image-actions button:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
.cover-label {
  font-size: 0.75rem;
  font-weight: 700;
  color: #e5e5e0;
}
.image-feedback {
  flex-basis: 100%;
  margin: 4px 0;
  text-align: center;
  font-size: 0.88rem;
}
.remove-confirmation {
  width: 100%;
  text-align: center;
}
.remove-confirmation button + button {
  margin-left: 8px;
}
.remove-confirmation p {
  font-size: 0.88rem;
}
.image-error {
  color: #ffb4b4;
}

.state-card,
.context-card {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
}

.state-card.error {
  color: #7a1414;
}

.context-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.context-card {
  display: grid;
  gap: 8px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.context-card p,
.context-card span {
  margin: 0;
}

.context-card strong {
  color: var(--base-color-text-primary);
  font-size: 1.8rem;
}

pre {
  margin: 12px 0 0;
  white-space: pre-wrap;
}

@media (max-width: 720px) {
  .game-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .context-grid {
    grid-template-columns: 1fr;
  }
}
</style>
