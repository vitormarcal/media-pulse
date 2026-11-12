<template>
  <main class="author-page">
    <div v-if="status === 'pending'" class="panel" role="status"><p>Carregando autor…</p></div>
    <div v-else-if="error" class="panel" role="alert">
      <p>Não foi possível carregar este autor.</p>
      <button type="button" @click="refresh()">Tentar novamente</button>
      <NuxtLink to="/books">Voltar para livros</NuxtLink>
    </div>
    <template v-else-if="data">
      <AuthorPageHero :title="data.name" :profile="data.profile"
        ><AuthorContextPanel :stats="data.stats"
      /></AuthorPageHero>
      <section class="panel" aria-labelledby="author-books-heading">
        <div class="section-header">
          <h2 id="author-books-heading">Livros no acervo</h2>
          <label v-if="data.books.length > 1" class="sort-label"
            >Ordenar por
            <select v-model="sort">
              <option value="title">Título</option>
              <option value="year">Ano de publicação</option>
            </select>
          </label>
        </div>
        <p v-if="!data.books.length" class="muted">Nenhum livro deste autor no acervo.</p>
        <div v-else id="author-books-grid" class="author-books" :class="{ single: data.books.length === 1 }">
          <article v-for="book in visibleBooks" :key="book.bookId" class="author-book">
            <NuxtLink class="book-cover" :to="`/books/${book.slug}`" :aria-label="`Ver ${book.title}`">
              <MediaImage
                :src="resolveMediaUrl(book.coverUrl)"
                :alt="`Capa de ${book.title}`"
                loading="lazy"
                fallback="Capa indisponível"
              />
            </NuxtLink>
            <div class="book-copy">
              <NuxtLink :to="`/books/${book.slug}`"
                ><h3>{{ book.title }}</h3></NuxtLink
              >
              <p v-if="coauthors(book).length" class="book-authors">
                <span>Outros créditos: </span>
                <template v-for="(author, index) in coauthors(book)" :key="author.id"
                  ><span v-if="index">, </span
                  ><NuxtLink :to="`/books/authors/${author.id}`">{{ author.name }}</NuxtLink></template
                >
              </p>
              <p class="muted book-year">{{ book.releaseDate?.slice(0, 4) || 'Ano não informado' }}</p>
            </div>
          </article>
        </div>
        <button
          v-if="data.books.length > 12"
          class="show-books"
          type="button"
          :aria-expanded="showAllBooks"
          aria-controls="author-books-grid"
          @click="showAllBooks = !showAllBooks"
        >
          {{ showAllBooks ? 'Mostrar menos livros' : `Ver todos os ${data.books.length} livros` }}
        </button>
      </section>
      <section v-if="data.recentReads.length" class="panel" aria-labelledby="author-reads-heading">
        <h2 id="author-reads-heading">Leituras recentes</h2>
        <p class="muted history-note">
          {{
            data.stats.readsCount > data.recentReads.length
              ? `Mostrando as ${data.recentReads.length} leituras mais recentes.`
              : 'Seu histórico de leitura com este autor.'
          }}
        </p>
        <article v-for="read in data.recentReads" :key="read.id" class="author-read">
          <div>
            <NuxtLink v-if="read.href" :to="read.href"
              ><h3>{{ read.title }}</h3></NuxtLink
            >
            <p class="muted">{{ read.context }}</p>
          </div>
          <p class="muted read-period">{{ read.period || 'Datas não informadas' }}</p>
        </article>
      </section>
      <section v-if="data.series.length" class="panel" aria-labelledby="author-series-heading">
        <h2 id="author-series-heading">Séries no acervo</h2>
        <div class="book-groups author-series">
          <div class="group-grid"><BookGroupCard v-for="series in data.series" :key="series.id" :group="series" /></div>
        </div>
      </section>
      <section v-if="data.profile || isOwner" class="panel author-about">
        <h2>Sobre o autor</h2>
        <template v-if="data.profile">
          <p class="muted">
            Perfil vinculado:
            <a :href="data.profile.sourceUrl" target="_blank" rel="noopener noreferrer"
              >{{ data.profile.externalName }} no Hardcover ↗</a
            >
          </p>
          <ul v-if="data.profile.links.length" class="author-links">
            <li v-for="link in data.profile.links" :key="link">
              <a :href="link" target="_blank" rel="noopener noreferrer">{{ link }}</a>
            </li>
          </ul>
        </template>
        <BookAuthorEnrichmentPanel
          v-if="isOwner"
          :key="data.id"
          :author-id="data.id"
          :profile="data.profile"
          @saved="saveProfile"
        />
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import BookAuthorEnrichmentPanel from '~/components/books/BookAuthorEnrichmentPanel.vue'
import BookGroupCard from '~/components/books/BookGroupCard.vue'
import AuthorContextPanel from '~/components/books/AuthorContextPanel.vue'
import AuthorPageHero from '~/components/books/AuthorPageHero.vue'
import type { AuthorEnrichmentProfile, BookCardDto } from '~/types/books'
import { useAuthorPageData } from '~/composables/useAuthorPageData'

const ownerSession = useOwnerAuth()
const isOwner = computed(() => ownerSession.value.authenticated)
const route = useRoute()
const id = computed(() => String(route.params.id))
const { data, error, status, refresh } = await useAuthorPageData(id)
const { resolveMediaUrl } = useMediaUrl()
function saveProfile(profile: AuthorEnrichmentProfile) {
  if (data.value) data.value = { ...data.value, profile }
}
const sort = ref('title')
const showAllBooks = ref(false)
watch(id, () => {
  showAllBooks.value = false
})
const coauthors = (book: BookCardDto) => book.authors.filter((author) => String(author.id) !== data.value?.id)
const collator = new Intl.Collator('pt-BR', { sensitivity: 'base', numeric: true })
const sortedBooks = computed(() =>
  [...(data.value?.books ?? [])].sort((a, b) => {
    if (sort.value === 'year') {
      const difference =
        (b.releaseDate?.slice(0, 4) ? Number(b.releaseDate.slice(0, 4)) : -Infinity) -
        (a.releaseDate?.slice(0, 4) ? Number(a.releaseDate.slice(0, 4)) : -Infinity)
      if (difference && !Number.isNaN(difference)) return difference
    }
    return collator.compare(a.title, b.title) || a.bookId - b.bookId
  }),
)
const visibleBooks = computed(() => (showAllBooks.value ? sortedBooks.value : sortedBooks.value.slice(0, 12)))
useHead(() => ({
  title: data.value ? `${data.value.name} · Media Pulse` : 'Autor · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value ? `Livros de ${data.value.name} no acervo e histórico de leitura.` : 'Autor no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.author-links {
  padding-left: 20px;
  overflow-wrap: anywhere;
}
.author-about > p {
  line-height: 1.6;
}
.author-page {
  display: grid;
  gap: 32px;
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}
.panel {
  min-width: 0;
  padding: clamp(20px, 4vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
}
h2 {
  margin: 0;
  font-size: 1.3rem;
  letter-spacing: -0.025em;
}
h3 {
  margin: 0;
  font-size: 1rem;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.section-header {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.sort-label {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}
select,
button {
  padding: 8px 12px;
  border: 1px solid var(--base-color-border);
  border-radius: 10px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
  font: inherit;
}
.author-books {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 32px 24px;
}
.author-book {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
}
.book-copy {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
}
.author-books.single {
  grid-template-columns: minmax(0, 1fr);
}
.single .author-book {
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 24px;
  align-items: center;
}
.single .book-cover {
  height: 260px;
  margin: 0;
}
.single .book-copy {
  max-width: 60ch;
  gap: 12px;
}
.single h3 {
  font-size: 1.25rem;
}
.book-cover {
  height: 240px;
  display: flex;
  align-items: end;
  justify-content: center;
  margin-bottom: 8px;
}
.book-cover :deep(img) {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
  border-radius: 4px;
}
.book-cover :deep(.media-image-fallback) {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  font-size: 0.82rem;
}
.book-authors,
.book-year {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.5;
  color: var(--base-color-text-secondary);
}
.muted {
  color: var(--base-color-text-secondary);
}
.history-note {
  margin: 12px 0 20px;
  font-size: 0.85rem;
}
.author-read {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12px 24px;
  padding: 18px 0;
  border-top: 1px solid var(--base-color-border);
}
.author-read p {
  margin: 6px 0 0;
  font-size: 0.85rem;
  line-height: 1.6;
}
.read-period {
  text-align: right;
}
.author-series {
  width: 100%;
  min-width: 0;
  margin: 24px 0 0;
  padding: 0;
  gap: 0;
}
.author-series :deep(.group-grid) {
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 320px));
}
.author-series :deep(.group-card) {
  min-width: 0;
}
.show-books {
  display: block;
  margin: 28px auto 0;
  cursor: pointer;
}
.book-authors {
  overflow-wrap: anywhere;
}
.author-page :focus-visible {
  outline: 2px solid #435ee5;
  outline-offset: 3px;
}
@media (max-width: 800px) {
  .author-books {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .book-cover {
    height: 200px;
  }
  .author-read {
    flex-direction: column;
    gap: 4px;
  }
  .read-period {
    text-align: left;
  }
}
@media (max-width: 560px) {
  .single .author-book {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }
  .single .book-cover {
    height: 240px;
  }
  .single h3 {
    font-size: 1.1rem;
  }

  .author-books {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px 16px;
  }
  .book-cover {
    height: 180px;
  }
  .author-page {
    padding-top: 20px;
  }
}
</style>
