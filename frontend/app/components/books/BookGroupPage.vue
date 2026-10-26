<template>
  <main class="book-groups">
    <p v-if="status === 'pending'" class="group-state">Carregando...</p>
    <div v-else-if="error" class="group-state group-error" role="alert">
      Não foi possível carregar. <button @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="group">
      <header class="group-hero">
        <div class="group-hero-copy">
          <div class="group-actions">
            <NuxtLink class="group-back" :to="`/books/${kind}`">{{
              kind === 'series' ? 'Voltar para séries' : 'Voltar para universos'
            }}</NuxtLink
            ><button
              v-owner
              :aria-expanded="editing"
              aria-controls="group-edit-panel"
              :disabled="busy"
              @click="toggleEditing"
            >
              {{ editing ? 'Fechar edição' : kind === 'series' ? 'Editar série' : 'Editar universo' }}
            </button>
          </div>
          <p class="group-eyebrow">{{ kind === 'series' ? 'Série de livros' : 'Universo' }}</p>
          <h1>{{ group.name }}</h1>
          <p v-if="group.authors.length" class="group-authors">
            <template v-for="(author, index) in group.authors" :key="author.id"
              ><span v-if="index">, </span
              ><NuxtLink :to="`/books/authors/${author.id}`">{{ author.name }}</NuxtLink></template
            >
          </p>
          <div v-if="group.description" class="group-description">
            <p :class="{ 'group-description-collapsed': !expanded && group.description.length > 500 }">
              {{ group.description }}
            </p>
            <button v-if="group.description.length > 500" :aria-expanded="expanded" @click="expanded = !expanded">
              {{ expanded ? 'Recolher' : 'Continuar leitura' }}
            </button>
          </div>
          <div class="group-actions">
            <span class="group-badge"
              >{{ group.items.length }} {{ kind === 'series' ? 'livros no acervo' : 'séries' }}</span
            >
          </div>
          <nav v-if="group.universes.length" class="group-actions" aria-label="Universos da série">
            <NuxtLink v-for="universe in group.universes" :key="universe.id" :to="`/books/universes/${universe.id}`">{{
              universe.name
            }}</NuxtLink>
          </nav>
          <div v-if="kind === 'series' && group.hardcoverId" class="group-provider-context">
            <p class="group-muted">
              Vinculada ao Hardcover<template v-if="group.hardcover.primaryAuthor">
                · Autor principal: {{ group.hardcover.primaryAuthor.name }}</template
              >
            </p>
            <p v-if="group.hardcover.isCompleted != null" class="group-muted">
              {{ group.hardcover.isCompleted ? 'Publicação concluída' : 'Publicação não concluída' }}
            </p>
            <p
              v-if="group.hardcover.booksCount != null || group.hardcover.primaryBooksCount != null"
              class="group-muted"
            >
              <template v-if="group.hardcover.booksCount != null"
                >{{ group.hardcover.booksCount }} livros no Hardcover</template
              ><template v-if="group.hardcover.primaryBooksCount != null">
                · {{ group.hardcover.primaryBooksCount }} volumes principais</template
              >
            </p>
          </div>
        </div>
        <div class="group-hero-cover">
          <MediaImage :src="resolveMediaUrl(group.coverUrls[0])" :alt="group.name" :fallback="group.name.slice(0, 1)" />
        </div>
      </header>
      <section v-if="editing" id="group-edit-panel" class="group-edit-panel">
        <form v-owner @submit.prevent="saveDetails">
          <h2>Detalhes</h2>
          <label>Nome<input ref="nameInput" v-model="name" required maxlength="300" :disabled="busy" /></label>
          <label v-if="kind === 'series'"
            >Descrição <span class="group-muted">opcional</span
            ><textarea v-model="description" rows="5" maxlength="30000" :disabled="busy" />
          </label>
          <button class="group-primary" type="submit" :disabled="busy">
            {{ busy ? 'Salvando...' : 'Salvar detalhes' }}
          </button>
          <details class="group-delete">
            <summary>Excluir {{ kind === 'series' ? 'série' : 'universo' }}</summary>
            <p>Livros e leituras serão preservados.</p>
            <label class="group-check"
              ><input v-model="confirmDelete" type="checkbox" :disabled="busy" />Confirmar exclusão</label
            ><button v-owner class="group-danger" type="button" :disabled="busy || !confirmDelete" @click="removeGroup">
              Excluir {{ kind === 'series' ? 'série' : 'universo' }}
            </button>
          </details>
        </form>
        <div class="group-section">
          <form v-owner @submit.prevent="add">
            <h2>Adicionar do acervo</h2>
            <template v-if="kind === 'series'"
              ><label>Buscar livro<input v-model="query" type="search" :disabled="busy" /></label
              ><button v-owner :disabled="busy || !query.trim()" type="button" @click="search">
                Buscar livros
              </button></template
            >
            <label :for="`group-member-${id}`">{{ kind === 'series' ? 'Livro' : 'Série' }}</label
            ><select :id="`group-member-${id}`" v-model="selected" required :disabled="busy">
              <option value="">Selecionar</option>
              <option v-for="item in availableChoices" :key="item.id" :value="item.id">{{ item.name }}</option>
            </select>
            <button class="group-primary" type="submit" :disabled="busy || !selected">Adicionar ao final</button>
          </form>
          <form v-if="kind === 'series'" v-owner @submit.prevent="linkUniverse">
            <label for="group-universe">Universo</label
            ><select id="group-universe" v-model="universeId" required :disabled="busy">
              <option value="">Selecionar</option>
              <option v-for="universe in availableUniverses" :key="universe.id" :value="universe.id">
                {{ universe.name }}
              </option>
            </select>
            <div class="group-actions">
              <button :disabled="busy || !universeId" type="submit">Associar universo</button
              ><NuxtLink to="/books/universes">Criar universo</NuxtLink>
            </div>
          </form>
        </div>
      </section>
      <section v-if="editing && kind === 'series' && group.hardcoverId" class="group-enrichment">
        <div class="group-section-heading">
          <h2>Metadados</h2>
          <button v-owner :disabled="busy" @click="previewMetadata">
            {{ busy ? 'Buscando...' : 'Buscar metadados no Hardcover' }}
          </button>
        </div>
        <form v-if="metadataPreview" v-owner @submit.prevent="applyMetadata">
          <BookSeriesMetadataFields
            v-model="metadataFields"
            :suggestion="metadataPreview.suggested"
            :current="group"
            include-name
            :disabled="busy"
          /><button class="group-primary" type="submit" :disabled="busy || !metadataFields.length">
            Aplicar campos selecionados
          </button>
        </form>
      </section>
      <p v-if="message" role="status" :class="{ 'group-error': failed }">{{ message }}</p>
      <section class="group-section">
        <div class="group-section-heading">
          <h2>Ordem de leitura</h2>
          <span class="group-muted">{{ group.items.length }} {{ kind === 'series' ? 'livros' : 'séries' }}</span>
        </div>
        <p v-if="!group.items.length" class="group-state">
          {{
            kind === 'series'
              ? 'Nenhum livro associado. Use “Editar série” para adicionar.'
              : 'Nenhuma série associada. Use “Editar universo” para adicionar.'
          }}
        </p>
        <ol v-else class="group-members">
          <li v-for="(item, index) in group.items" :key="item.id" class="group-member">
            <span class="group-position" aria-hidden="true">{{ index + 1 }}</span>
            <NuxtLink class="group-member-cover" :to="memberHref(item)"
              ><MediaImage :src="resolveMediaUrl(item.coverUrl)" :alt="item.name" :fallback="item.name.slice(0, 1)"
            /></NuxtLink>
            <NuxtLink class="group-member-title" :to="memberHref(item)"
              ><h3>{{ item.name }}</h3></NuxtLink
            >
            <div v-if="editing" class="group-actions group-member-actions">
              <button
                v-owner
                :disabled="busy || index === 0"
                :aria-label="`Mover ${item.name} para cima`"
                @click="move(index, -1)"
              >
                ↑</button
              ><button
                v-owner
                :disabled="busy || index === group.items.length - 1"
                :aria-label="`Mover ${item.name} para baixo`"
                @click="move(index, 1)"
              >
                ↓</button
              ><button
                v-owner
                class="group-danger"
                :disabled="busy"
                :aria-label="`Remover ${item.name} deste agrupamento`"
                @click="change(`/${kind}/${id}/items/${item.id}`, 'DELETE')"
              >
                Remover
              </button>
            </div>
          </li>
        </ol>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import type {
  BookGroup,
  BookGroupDetails,
  BookGroupKind,
  BookGroupMember,
  BookSeriesMetadataField,
  BookSeriesMetadataPreview,
} from '~/types/book-series'
import { bookGroupError, useBookGroupsApi } from '~/composables/useBookGroupsApi'
import BookSeriesMetadataFields from '~/components/books/BookSeriesMetadataFields.vue'
import '~/assets/css/book-groups.css'
const props = defineProps<{ kind: BookGroupKind; id: string }>()
const api = useBookGroupsApi()
const { resolveMediaUrl } = useMediaUrl()
const {
  data: group,
  error,
  status,
  refresh,
} = await useAsyncData(`book-group-${props.kind}-${props.id}`, () =>
  api<BookGroupDetails>(`/${props.kind}/${props.id}`),
)
const name = ref(group.value?.name || '')
const description = ref(group.value?.description || '')
const editing = ref(false)
const expanded = ref(false)
const nameInput = ref<HTMLInputElement | null>(null)
const busy = ref(false)
const failed = ref(false)
const message = ref('')
const confirmDelete = ref(false)
const query = ref('')
const selected = ref<number | ''>('')
const universeId = ref<number | ''>('')
const choices = ref<{ id: number; name: string }[]>([])
const universes = ref<BookGroup[]>([])
const metadataPreview = ref<BookSeriesMetadataPreview | null>(null)
const metadataFields = ref<BookSeriesMetadataField[]>([])
const availableChoices = computed(() =>
  choices.value.filter((item) => !group.value?.items.some((member) => member.id === item.id)),
)
const availableUniverses = computed(() =>
  universes.value.filter((item) => !group.value?.universes.some((member) => member.id === item.id)),
)
function memberHref(item: BookGroupMember) {
  return props.kind === 'series' ? `/books/${item.slug}` : `/books/series/${item.id}`
}
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  message.value = ''
  failed.value = false
  try {
    await action()
  } catch (cause) {
    failed.value = true
    message.value = bookGroupError(cause)
  } finally {
    busy.value = false
  }
}
async function toggleEditing() {
  editing.value = !editing.value
  if (!editing.value) {
    metadataPreview.value = null
    return
  }
  name.value = group.value?.name || ''
  description.value = group.value?.description || ''
  await nextTick()
  await run(async () => {
    if (props.kind === 'universes') choices.value = await api<BookGroup[]>('/series')
    else universes.value = await api<BookGroup[]>('/universes')
  })
  await nextTick()
  nameInput.value?.focus()
}
async function search() {
  await run(async () => {
    const result = await api<{ books: { bookId: number; title: string }[] }>(
      `/search?q=${encodeURIComponent(query.value)}&limit=50`,
    )
    choices.value = result.books.map((item) => ({ id: item.bookId, name: item.title }))
    selected.value = ''
    if (!availableChoices.value.length) message.value = 'Nenhum outro livro encontrado no acervo.'
  })
}
async function change(path: string, method: 'PUT' | 'POST' | 'DELETE', body?: object) {
  await run(async () => {
    group.value = await api<BookGroupDetails>(path, method, body)
    message.value = 'Alteração salva.'
  })
}
function saveDetails() {
  return change(`/${props.kind}/${props.id}`, 'PUT', {
    name: name.value,
    ...(props.kind === 'series' ? { description: description.value } : {}),
  })
}
async function add() {
  await change(`/${props.kind}/${props.id}/items/${selected.value}`, 'POST')
  if (!failed.value) selected.value = ''
}
async function linkUniverse() {
  await run(async () => {
    await api(`/universes/${universeId.value}/items/${props.id}`, 'POST')
    await refresh()
    universeId.value = ''
    message.value = 'Universo associado.'
  })
}
function move(index: number, direction: number) {
  const ids = group.value?.items.map((item) => item.id) || []
  const next = index + direction
  if (next < 0 || next >= ids.length) return
  const current = ids[index]!
  ids[index] = ids[next]!
  ids[next] = current
  return change(`/${props.kind}/${props.id}/order`, 'PUT', { ids })
}
async function removeGroup() {
  if (confirmDelete.value)
    await run(async () => {
      await api(`/${props.kind}/${props.id}`, 'DELETE')
      await navigateTo(`/books/${props.kind}`)
    })
}
async function previewMetadata() {
  await run(async () => {
    metadataPreview.value = await api<BookSeriesMetadataPreview>(`/series/${props.id}/metadata/preview`, 'POST')
    metadataFields.value = []
  })
}
async function applyMetadata() {
  await run(async () => {
    group.value = await api<BookGroupDetails>(`/series/${props.id}/metadata/apply`, 'POST', {
      fields: metadataFields.value,
    })
    name.value = group.value.name
    description.value = group.value.description || ''
    metadataPreview.value = null
    message.value = 'Metadados selecionados aplicados.'
  })
}
useHead(() => ({ title: `${group.value?.name || 'Livros'} · Media Pulse` }))
</script>
