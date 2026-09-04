<template>
  <main class="page">
    <div v-if="status === 'pending'" class="state">Carregando…</div>
    <div v-else-if="error" class="state">Não foi possível carregar as listas.</div>
    <template v-else-if="data">
      <ShowsLibraryHero
        title="Listas de séries"
        intro="Recortes pessoais para organizar séries em qualquer ordem."
        back-link="/shows"
        back-label="Voltar para séries"
        accent-link="/shows?add=1"
        accent-label="Adicionar série"
        :spotlight="null"
      />
      <section>
        <SectionHeading eyebrow="Curadoria" title="Listas manuais" :summary="`${data.length} listas`" />
        <form class="create" @submit.prevent="createList">
          <label><span>Nome</span><input v-model="name" required placeholder="Favoritas" /></label
          ><label><span>Descrição</span><input v-model="description" placeholder="Opcional" /></label
          ><button type="submit" :disabled="creating">{{ creating ? 'Criando…' : 'Criar lista' }}</button>
        </form>
        <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
        <p v-if="!data.length" class="quiet">Nenhuma lista criada ainda.</p>
        <div v-else class="grid">
          <NuxtLink v-for="list in data" :key="list.listId" :to="`/shows/lists/${list.slug}`" class="card"
            ><div class="mosaic">
              <img
                v-if="resolveMediaUrl(list.coverUrl || list.previewShows[0]?.coverUrl)"
                :src="resolveMediaUrl(list.coverUrl || list.previewShows[0]?.coverUrl) || undefined"
                :alt="list.name"
              /><span v-else>{{ list.name.slice(0, 1) }}</span>
            </div>
            <p>Lista manual</p>
            <h2>{{ list.name }}</h2>
            <small>{{ list.itemCount }} séries · Ordem manual</small></NuxtLink
          >
        </div>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import ShowsLibraryHero from '~/components/shows/ShowsLibraryHero.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ShowListSummaryDto } from '~/types/shows'
import { useShowLists } from '~/composables/useShowListPageData'
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const { data, error, status, refresh } = await useShowLists()
const name = ref('')
const description = ref('')
const creating = ref(false)
const feedback = ref<string | null>(null)
async function createList() {
  creating.value = true
  feedback.value = null
  try {
    const created = await $fetch<ShowListSummaryDto>('/api/shows/lists', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { name: name.value, description: description.value || null },
    })
    await refresh()
    await navigateTo(`/shows/lists/${created.slug}`)
  } catch (error) {
    feedback.value = error instanceof Error ? error.message : 'Não foi possível criar a lista.'
  } finally {
    creating.value = false
  }
}
useHead({ title: 'Listas de séries · Media Pulse' })
</script>
<style scoped>
.page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: auto;
  padding: 28px 0 84px;
}
.page section {
  display: grid;
  gap: 20px;
}
.state,
.create,
.card {
  padding: 20px;
  border-radius: 24px;
  background: var(--base-color-surface-strong);
}
.create {
  display: grid;
  grid-template-columns: 1fr 2fr auto;
  gap: 12px;
  align-items: end;
  border: 1px solid var(--base-color-border);
}
label {
  display: grid;
  gap: 5px;
  font-size: 0.75rem;
}
input {
  padding: 10px 12px;
  border: 1px solid var(--base-color-text-muted);
  border-radius: 16px;
}
input:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--base-color-focus) 66%, white);
  outline-offset: 3px;
}
button {
  padding: 8px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-brand-red);
  font-weight: 700;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.card {
  display: grid;
  gap: 6px;
  border: 1px solid var(--base-color-border);
}
.card p,
.card h2 {
  margin: 0;
}
.card p,
.card small,
.quiet,
.feedback {
  color: var(--base-color-text-secondary);
}
.mosaic {
  height: 210px;
  border-radius: 16px;
  overflow: hidden;
  background: var(--base-color-surface-warm);
  display: grid;
  place-items: center;
  font-size: 4rem;
  font-weight: 700;
}
.mosaic img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
@media (max-width: 760px) {
  .create,
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
