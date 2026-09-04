<template>
  <main class="page">
    <div v-if="status === 'pending'" class="state">Carregando…</div>
    <div v-else-if="error" class="state">Lista não encontrada.</div>
    <template v-else-if="data">
      <header class="hero">
        <div class="cover">
          <img
            v-if="resolveMediaUrl(data.coverUrl || data.shows[0]?.coverUrl)"
            :src="resolveMediaUrl(data.coverUrl || data.shows[0]?.coverUrl) || undefined"
            :alt="data.name"
          /><span v-else>{{ data.name.slice(0, 1) }}</span>
        </div>
        <div>
          <NuxtLink to="/shows/lists">Voltar para listas</NuxtLink>
          <p>Lista manual</p>
          <h1>{{ data.name }}</h1>
          <p>{{ data.description || 'Curadoria pessoal de séries.' }}</p>
          <small>{{ data.showCount }} séries · {{ data.watchedShowsCount }} concluídas</small>
          <button class="delete-list" type="button" :disabled="saving" @click="deleteList">Excluir lista</button>
        </div>
      </header>
      <section>
        <SectionHeading eyebrow="Ordem manual" title="Séries da lista" />
        <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
        <p v-if="!data.shows.length">Esta lista ainda está vazia.</p>
        <div class="items">
          <article v-for="(show, index) in ordered" :key="show.showId" class="item">
            <span>{{ index + 1 }}</span
            ><img
              v-if="resolveMediaUrl(show.coverUrl)"
              :src="resolveMediaUrl(show.coverUrl) || undefined"
              :alt="show.title"
            />
            <div>
              <NuxtLink :to="show.slug ? `/shows/${show.slug}` : '#'"
                ><h2>{{ show.title }}</h2></NuxtLink
              >
              <p>{{ show.year || 'Série' }} · {{ show.watchedEpisodesCount }}/{{ show.episodesCount }} episódios</p>
            </div>
            <div class="actions">
              <button :disabled="index === 0 || saving" @click="move(index, -1)">Subir</button
              ><button :disabled="index === ordered.length - 1 || saving" @click="move(index, 1)">Descer</button
              ><button
                :class="{ active: data.coverShowId === show.showId }"
                :disabled="saving"
                @click="setCover(show.showId)"
              >
                {{ data.coverShowId === show.showId ? 'Capa' : 'Usar como capa' }}</button
              ><button :disabled="saving" @click="remove(show.showId)">Remover</button>
            </div>
          </article>
        </div>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import { useShowList } from '~/composables/useShowListPageData'
import type { ShowListItemDto } from '~/types/shows'
const route = useRoute()
const slug = String(route.params.slug)
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const { data, error, status, refresh } = await useShowList(slug)
const ordered = ref<ShowListItemDto[]>([])
const saving = ref(false)
const feedback = ref<string | null>(null)
watch(
  data,
  (v) => {
    ordered.value = v?.shows.slice() || []
  },
  { immediate: true },
)
async function move(index: number, delta: number) {
  const target = index + delta
  ;[ordered.value[index], ordered.value[target]] = [ordered.value[target]!, ordered.value[index]!]
  await persistOrder()
}
async function persistOrder() {
  if (!data.value) return
  saving.value = true
  feedback.value = null
  try {
    await $fetch(`/api/shows/lists/${data.value.listId}/order`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { showIds: ordered.value.map((s) => s.showId) },
    })
    await refresh()
  } catch (error) {
    feedback.value = resolveError(error)
    await refresh()
  } finally {
    saving.value = false
  }
}
async function setCover(showId: number) {
  if (!data.value) return
  saving.value = true
  feedback.value = null
  try {
    await $fetch(`/api/shows/lists/${data.value.listId}/cover`, {
      baseURL: config.public.apiBase,
      method: 'PATCH',
      body: { coverShowId: data.value.coverShowId === showId ? null : showId },
    })
    await refresh()
  } catch (error) {
    feedback.value = resolveError(error)
  } finally {
    saving.value = false
  }
}
async function remove(showId: number) {
  if (!data.value) return
  saving.value = true
  feedback.value = null
  try {
    await $fetch(`/api/shows/${showId}/lists/${data.value.listId}`, {
      baseURL: config.public.apiBase,
      method: 'DELETE',
    })
    await refresh()
  } catch (error) {
    feedback.value = resolveError(error)
  } finally {
    saving.value = false
  }
}
async function deleteList() {
  if (!data.value || !window.confirm(`Excluir a lista “${data.value.name}”? As séries serão preservadas.`)) return
  saving.value = true
  feedback.value = null
  try {
    await $fetch(`/api/shows/lists/${data.value.slug}`, { baseURL: config.public.apiBase, method: 'DELETE' })
    await navigateTo('/shows/lists')
  } catch (error) {
    feedback.value = resolveError(error)
  } finally {
    saving.value = false
  }
}
function resolveError(error: unknown) {
  if (error && typeof error === 'object' && 'data' in error) {
    const data = (error as { data?: { detail?: string; message?: string } }).data
    return data?.detail || data?.message || 'Não foi possível atualizar a lista.'
  }
  return error instanceof Error ? error.message : 'Não foi possível atualizar a lista.'
}
useHead(() => ({
  title: data.value ? `${data.value.name} · Listas de séries · Media Pulse` : 'Lista de séries · Media Pulse',
}))
</script>
<style scoped>
.page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1180px, calc(100vw - 32px));
  margin: auto;
  padding: 28px 0 84px;
}
.state,
.hero,
.item {
  background: var(--base-color-surface-strong);
  border-radius: 24px;
}
.hero {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 28px;
  padding: 24px;
}
.cover {
  height: 340px;
  border-radius: 20px;
  overflow: hidden;
  background: var(--base-color-surface-warm);
  display: grid;
  place-items: center;
  font-size: 5rem;
}
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hero h1 {
  font-size: clamp(2.5rem, 7vw, 4.4rem);
  line-height: 1;
  margin: 12px 0;
}
.items {
  display: grid;
  gap: 10px;
}
.item {
  display: grid;
  grid-template-columns: 30px 70px 1fr auto;
  gap: 14px;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--base-color-border);
}
.item img {
  width: 70px;
  height: 92px;
  object-fit: cover;
  border-radius: 12px;
}
.item h2,
.item p {
  margin: 0;
}
.feedback {
  margin: 0;
  color: #9e0a0a;
}
.item p {
  color: var(--base-color-text-secondary);
}
.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}
.actions button {
  padding: 7px 10px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
}
.actions button:disabled,
.delete-list:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.actions .active {
  background: var(--base-color-brand-red);
}
.delete-list {
  margin-top: 18px;
  padding: 7px 12px;
  border: 0;
  border-radius: var(--comp-radius-button);
  background: var(--base-color-surface-warm);
  color: #9e0a0a;
}
@media (max-width: 760px) {
  .hero {
    grid-template-columns: 1fr;
  }
  .cover {
    height: 300px;
  }
  .item {
    grid-template-columns: 24px 56px 1fr;
  }
  .item img {
    width: 56px;
    height: 74px;
  }
  .actions {
    grid-column: 2/-1;
    justify-content: flex-start;
  }
}
</style>
