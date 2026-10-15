<template>
  <article class="credit-item">
    <header>
      <div>
        <NuxtLink class="album-title" :to="`/music/albums/${item.albumId}`">{{ item.albumTitle }}</NuxtLink>
        <a v-if="providerUrl" :href="providerUrl" target="_blank" rel="noopener noreferrer" class="provider-link"
          >{{ providerName }} ↗</a
        >
      </div>
      <span class="status" :class="statusTone">{{ backfillStatusLabels[item.status] }}</span>
    </header>
    <dl class="comparison">
      <div>
        <dt>Antes do lote</dt>
        <dd>{{ albumArtistLabel(item.before) || 'Sem créditos registrados' }}</dd>
      </div>
      <div>
        <dt>Proposta</dt>
        <dd>{{ proposedLabel || 'Nenhuma alteração proposta' }}</dd>
      </div>
      <div v-if="item.after">
        <dt>Aplicado neste lote</dt>
        <dd>{{ albumArtistLabel(item.after) }}</dd>
      </div>
    </dl>
    <p v-if="sameDisplayedCredits && item.status === 'READY'" class="item-note">
      Os nomes exibidos permanecem iguais. Aplicar registra os créditos e sua origem no {{ providerName }}.
    </p>
    <p v-if="item.status === 'UNDONE'" class="item-note">A lista anterior de créditos foi restaurada.</p>
    <p v-if="item.status === 'PENDING'" class="item-note">
      A consulta não terminou. Gere uma nova prévia para este álbum.
    </p>
    <p v-if="item.reason" class="item-note">{{ item.reason }}</p>
  </article>
</template>
<script setup lang="ts">
import { backfillStatusLabels, type AlbumCreditBackfillItem } from '~/types/musicBackfill'
import { albumArtistLabel } from '~/utils/musicCredits'
const props = defineProps<{ item: AlbumCreditBackfillItem }>()
const proposedLabel = computed(() =>
  props.item.proposed.map((artist) => artist.creditedName + artist.joinPhrase).join(''),
)
const sameDisplayedCredits = computed(
  () =>
    props.item.before.length === props.item.proposed.length &&
    proposedLabel.value !== '' &&
    albumArtistLabel(props.item.before) === proposedLabel.value,
)
const providerName = computed(() => (props.item.provider === 'SPOTIFY' ? 'Spotify' : 'MusicBrainz'))
const providerUrl = computed(() => {
  if (!props.item.providerId || !props.item.provider) return null
  const id = encodeURIComponent(props.item.providerId)
  return props.item.provider === 'SPOTIFY'
    ? `https://open.spotify.com/album/${id}`
    : `https://musicbrainz.org/release-group/${id}`
})
const statusTone = computed(() =>
  ['APPLIED', 'UNDONE'].includes(props.item.status)
    ? 'success'
    : ['ERROR', 'CONFLICT', 'UNDO_CONFLICT'].includes(props.item.status)
      ? 'attention'
      : '',
)
</script>
<style scoped>
.credit-item {
  padding: 22px;
  border: 1px solid var(--base-color-border);
  border-radius: 20px;
  background: white;
}
header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}
header > div {
  display: grid;
  gap: 6px;
  min-width: 0;
}
.album-title {
  color: var(--base-color-text-primary);
  font-size: 1.1rem;
  font-weight: 700;
  overflow-wrap: anywhere;
}
.provider-link {
  width: fit-content;
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
}
.status {
  padding: 6px 10px;
  border-radius: 12px;
  background: var(--base-color-surface-warm);
  font-size: 0.75rem;
  font-weight: 600;
  white-space: nowrap;
}
.success {
  background: #e8efe8;
  color: #103c25;
}
.attention {
  background: #fff2ec;
  color: #9e0a0a;
}
.comparison {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 180px), 1fr));
  gap: 18px;
  margin: 20px 0 0;
}
dt {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
  margin-bottom: 6px;
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.item-note {
  margin: 16px 0 0;
  font-size: 0.85rem;
  color: var(--base-color-text-secondary);
}
a:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 3px;
}
@media (max-width: 576px) {
  header {
    flex-direction: column;
  }
}
</style>
