<template>
  <aside class="spotify-status" role="status" aria-live="polite">
    <div class="spotify-status__copy">
      <strong>Spotify precisa ser reconectado</strong>
      <span>{{ status.message }}</span>
    </div>
    <a
      v-if="status.reauthorizationAvailable && status.reauthorizationUrl"
      class="spotify-status__action"
      :href="status.reauthorizationUrl"
    >
      Reconectar Spotify
    </a>
    <span v-else class="spotify-status__hint">Atualize o refresh token e reinicie a aplicação.</span>
  </aside>
</template>

<script setup lang="ts">
import type { SpotifyStatusResponse } from '~/types/spotify'

defineProps<{
  status: SpotifyStatusResponse
}>()
</script>

<style scoped>
.spotify-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: min(1448px, calc(100vw - 64px));
  margin: 4px auto 12px;
  padding: 12px 16px;
  border: 1px solid color-mix(in srgb, var(--base-color-brand-red) 20%, var(--base-color-surface-warm));
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-brand-red) 5%, var(--base-color-surface-warm));
  color: var(--base-color-text-primary);
  font-size: 0.82rem;
}

.spotify-status__copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.spotify-status__copy strong {
  font-size: 0.88rem;
}

.spotify-status__copy span,
.spotify-status__hint {
  color: var(--base-color-text-secondary);
}

.spotify-status__action {
  flex: 0 0 auto;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-brand-red);
  color: white;
  font-weight: 700;
}

@media (max-width: 700px) {
  .spotify-status {
    align-items: flex-start;
    flex-direction: column;
    width: calc(100vw - 20px);
  }
}
</style>
