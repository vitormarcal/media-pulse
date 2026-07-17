<template>
  <main class="lists-page">
    <section class="hero">
      <div class="hero-copy">
        <NuxtLink class="back-link" to="/music">Voltar para música</NuxtLink>
        <p class="eyebrow">Curadoria pessoal</p>
        <h1>Listas de álbuns</h1>
        <p class="intro">Recortes para ouvir com intenção, preservar uma ordem e acompanhar o caminho.</p>
      </div>

      <form class="create-card" @submit.prevent="createList">
        <p class="eyebrow">Nova lista</p>
        <label>
          Nome
          <input v-model="draft.name" required maxlength="160" placeholder="100 maiores discos brasileiros" />
        </label>
        <label>
          Descrição <span>opcional</span>
          <textarea v-model="draft.description" rows="3" placeholder="De onde veio este recorte e por que ouvi-lo." />
        </label>
        <button type="submit" :disabled="saving || !draft.name.trim()">
          {{ saving ? 'Criando...' : 'Criar lista' }}
        </button>
        <p v-if="formError" class="form-error">{{ formError }}</p>
      </form>
    </section>

    <section class="collection">
      <div class="section-heading">
        <div>
          <p class="eyebrow">Estante manual</p>
          <h2>Seus percursos</h2>
        </div>
        <p>{{ data?.length || 0 }} listas</p>
      </div>

      <p v-if="status === 'pending'" class="state-card">Buscando suas listas...</p>
      <p v-else-if="error" class="state-card error">Não foi possível carregar as listas.</p>
      <p v-else-if="!data?.length" class="state-card">Crie a primeira lista para começar um percurso.</p>

      <div v-else class="list-grid">
        <NuxtLink v-for="item in data" :key="item.listId" :to="`/music/lists/${item.slug}`" class="list-card">
          <div class="cover-mosaic" :class="{ empty: !item.coverUrls.length }">
            <img v-for="cover in item.coverUrls" :key="cover" :src="resolveMediaUrl(cover) || ''" alt="" />
            <span v-if="!item.coverUrls.length">{{ item.name.slice(0, 1) }}</span>
          </div>
          <div class="card-copy">
            <p class="eyebrow">Lista de álbuns</p>
            <h2>{{ item.name }}</h2>
            <p v-if="item.description">{{ item.description }}</p>
            <div class="progress-line">
              <span>{{ item.itemCount }} álbuns</span>
              <span>{{ item.listenedCount }} ouvidos nesta lista</span>
            </div>
          </div>
        </NuxtLink>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import type { AlbumListDetailsResponse } from '~/types/music'

const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const { data, error, status, refresh } = await useAlbumListsData()
const draft = reactive({ name: '', description: '' })
const saving = ref(false)
const formError = ref('')

async function createList() {
  if (saving.value || !draft.name.trim()) return
  saving.value = true
  formError.value = ''
  try {
    const created = await $fetch<AlbumListDetailsResponse>('/api/music/lists', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: draft,
    })
    await refresh()
    await navigateTo(`/music/lists/${created.slug}`)
  } catch {
    formError.value = 'Não foi possível criar a lista. Confira se o nome já está em uso.'
  } finally {
    saving.value = false
  }
}

useHead({ title: 'Listas de álbuns · Media Pulse' })
</script>

<style scoped>
.lists-page {
  display: grid;
  gap: 56px;
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(18rem, 0.75fr);
  gap: 28px;
  padding: clamp(24px, 5vw, 52px);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  border-radius: 40px;
  background: radial-gradient(circle at top right, rgba(230, 0, 35, 0.09), transparent 30%), #fff;
}
.hero-copy {
  display: grid;
  gap: 14px;
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
  letter-spacing: 0.09em;
  text-transform: uppercase;
}
h1,
h2,
p {
  margin: 0;
}
h1 {
  max-width: 11ch;
  font-size: clamp(3rem, 6vw, 4.38rem);
  line-height: 0.9;
  letter-spacing: -0.075em;
}
.intro {
  max-width: 38rem;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}
.create-card {
  display: grid;
  gap: 14px;
  padding: 24px;
  border-radius: 28px;
  background: var(--base-color-surface-soft);
}
label {
  display: grid;
  gap: 7px;
  font-size: 0.82rem;
  font-weight: 700;
}
label span {
  color: var(--base-color-text-secondary);
  font-weight: 400;
}
input,
textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: #fff;
  color: var(--base-color-text-primary);
  font: inherit;
  resize: vertical;
}
button {
  padding: 11px 15px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-brand-red);
  color: #000;
  font: inherit;
  font-size: 0.78rem;
  font-weight: 700;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
}
.form-error,
.error {
  color: #9e0a0a;
}
.collection {
  display: grid;
  gap: 24px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: end;
}
.section-heading > div {
  display: grid;
  gap: 7px;
}
.section-heading h2 {
  font-size: clamp(2rem, 4vw, 3rem);
  letter-spacing: -0.05em;
}
.section-heading > p {
  color: var(--base-color-text-secondary);
}
.state-card {
  padding: 24px;
  border-radius: 24px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-secondary);
}
.list-grid {
  columns: 3;
  column-gap: 20px;
}
.list-card {
  display: inline-grid;
  width: 100%;
  gap: 18px;
  margin-bottom: 20px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  border-radius: 32px;
  background: #fff;
  color: inherit;
  break-inside: avoid;
}
.cover-mosaic {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 5px;
  aspect-ratio: 1.65;
  padding: 9px;
  background: var(--base-color-surface-warm);
}
.cover-mosaic img {
  width: 100%;
  height: 100%;
  min-width: 0;
  object-fit: cover;
  border: 5px solid #fff;
  border-radius: 20px;
}
.cover-mosaic.empty {
  place-items: center;
  grid-template-columns: 1fr;
  color: var(--base-color-text-secondary);
  font-size: 4rem;
  font-weight: 700;
}
.card-copy {
  display: grid;
  gap: 10px;
  padding: 0 22px 22px;
}
.card-copy h2 {
  font-size: 1.45rem;
  letter-spacing: -0.04em;
}
.card-copy > p:not(.eyebrow) {
  color: var(--base-color-text-secondary);
  line-height: 1.5;
}
.progress-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.progress-line span {
  padding: 7px 10px;
  border-radius: 14px;
  background: var(--base-color-surface-soft);
  font-size: 0.76rem;
}
@media (max-width: 1000px) {
  .hero {
    grid-template-columns: 1fr;
  }
  .list-grid {
    columns: 2;
  }
}
@media (max-width: 620px) {
  .list-grid {
    columns: 1;
  }
  .lists-page {
    width: min(100vw - 20px, 1480px);
  }
}
input:focus-visible,
textarea:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--base-color-focus) 66%, white);
  outline-offset: 2px;
}
</style>
