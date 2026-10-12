<template>
  <form ref="captureForm" class="art-panel" @submit.prevent="save">
    <h2>{{ initial ? 'Editar obra' : 'Adicionar obra' }}</h2>
    <fieldset :disabled="busy">
      <div class="art-fields">
        <label class="art-wide">Título <input v-model="form.title" required maxlength="300" /></label>
        <label
          >Artista
          <select v-model="form.artistId" aria-label="Artista">
            <option :value="null">Não informado</option>
            <option v-for="choice in artists" :key="choice.id" :value="choice.id">{{ choice.name }}</option>
          </select>
        </label>
        <label>Ano <input v-model="form.year" type="number" step="1" /></label>
      </div>
      <label class="art-check"><input v-model="newArtist" type="checkbox" /> Cadastrar novo artista</label>
      <section v-if="newArtist" class="art-subform"><ArtworkArtistFields v-model="artist" /></section>
      <div class="art-fields">
        <label
          >Tipo
          <select v-model="typeChoice" aria-label="Tipo da obra">
            <option value="">Não informado</option>
            <option v-for="type in artworkTypes" :key="type">{{ type }}</option>
            <option value="Outra">Outra</option>
          </select>
        </label>
        <label v-if="typeChoice === 'Outra'">Outro tipo <input v-model="otherType" maxlength="100" /></label>
        <label>Movimento <input v-model="form.movement" maxlength="200" placeholder="Academicismo francês" /></label>
        <label class="art-wide"
          >Técnica <input v-model="form.technique" maxlength="200" placeholder="Óleo sobre tela"
        /></label>
      </div>
      <details class="art-form-details" :open="Boolean(initial?.description)">
        <summary>Descrição</summary>
        <label class="art-section"
          ><span class="art-sr-only">Descrição da obra</span
          ><textarea v-model="form.description" rows="4" maxlength="20000" placeholder="Tema e contexto da obra" />
        </label>
      </details>
      <section v-if="!initial" class="art-section">
        <h3>Primeira experiência</h3>
        <ArtworkExperienceFields v-model="experience" />
      </section>
      <section v-if="!initial" class="art-section">
        <h3>Imagens</h3>
        <div class="art-fields">
          <label
            >Arquivos <input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="selectFiles"
          /></label>
          <label>URLs das imagens <textarea v-model="imageUrls" rows="2" placeholder="Uma URL por linha" /></label>
        </div>
        <p class="art-muted art-caption">JPEG, PNG ou WebP · Até 10 MB por imagem</p>
      </section>
      <p v-if="error" class="art-error" role="alert">{{ error }}</p>
      <p v-if="imageErrors.length" class="art-error" role="alert">
        A obra foi salva. Algumas imagens falharam: {{ imageErrors.join('; ') }}. Tente novamente ou abra a obra para
        adicioná-las.
      </p>
      <div class="art-actions">
        <button class="art-button art-primary" type="submit">
          {{ busy ? 'Salvando…' : savedId ? 'Tentar imagens novamente' : 'Salvar' }}
        </button>
        <NuxtLink v-if="savedId" class="art-button" :to="`/artworks/${savedId}`">Abrir obra salva</NuxtLink>
        <button v-else class="art-button" type="button" @click="$emit('cancel')">Cancelar</button>
      </div>
    </fieldset>
  </form>
</template>
<script setup lang="ts">
import type { Artwork, ArtworkArtist, ArtworkDetails, ArtworkExperienceInput } from '~/types/artworks'
import ArtworkArtistFields from './ArtworkArtistFields.vue'
import ArtworkExperienceFields from './ArtworkExperienceFields.vue'
const props = defineProps<{ initial?: Artwork }>()
const emit = defineEmits<{ saved: [details: ArtworkDetails]; cancel: [] }>()
const { request, run, busy, error } = useArtworkApi()
const { data: artists } = await useAsyncData('artwork-artist-choices', () => request<ArtworkArtist[]>('/artists'))
const form = reactive({
  title: props.initial?.title || '',
  artistId: props.initial?.artistId ?? null,
  year: props.initial?.year ?? ('' as number | string),
  type: props.initial?.type || '',
  movement: props.initial?.movement || '',
  technique: props.initial?.technique || '',
  description: props.initial?.description || '',
})
const artworkTypes = ['Pintura', 'Escultura', 'Fotografia artística', 'Desenho', 'Gravura']
const typeChoice = computed({
  get: () => (!form.type ? '' : artworkTypes.includes(form.type) ? form.type : 'Outra'),
  set: (value: string) => {
    form.type = value === 'Outra' ? 'Outra' : value
  },
})
const otherType = computed({
  get: () => (form.type === 'Outra' ? '' : form.type),
  set: (value: string) => {
    form.type = value || 'Outra'
  },
})
const captureForm = ref<HTMLFormElement>()
onMounted(() => captureForm.value?.querySelector('input')?.focus())
const newArtist = ref(false)
const artist = ref({
  name: '',
  countryOfOrigin: null as string | null,
  bornAt: null as string | null,
  diedAt: null as string | null,
  biography: null as string | null,
})
const experience = ref<ArtworkExperienceInput>({
  experiencedAt: null,
  experienceType: null,
  sourceName: null,
  sourceUrl: null,
  notes: null,
})
const files = ref<File[]>([])
const imageUrls = ref('')
const savedId = ref<number | null>(null)
const imageErrors = ref<string[]>([])
function selectFiles(event: Event) {
  files.value = Array.from((event.target as HTMLInputElement).files || [])
}
async function save() {
  await run(async () => {
    let details: ArtworkDetails
    if (!savedId.value) {
      const body = {
        ...form,
        year: form.year === '' ? null : Number(form.year),
        artistId: newArtist.value ? null : form.artistId,
        artist: newArtist.value
          ? { ...artist.value, bornAt: artist.value.bornAt || null, diedAt: artist.value.diedAt || null }
          : null,
        ...(!props.initial
          ? { experience: { ...experience.value, experiencedAt: experience.value.experiencedAt || null } }
          : {}),
      }
      details = await request<ArtworkDetails>(props.initial ? `/${props.initial.id}` : '', {
        method: props.initial ? 'PUT' : 'POST',
        body,
      })
      savedId.value = details.artwork.id
    } else details = await request<ArtworkDetails>(`/${savedId.value}`)
    imageErrors.value = []
    const remaining: File[] = []
    for (const file of files.value) {
      try {
        const body = new FormData()
        body.append('image', file)
        await request(`/${savedId.value}/images`, { method: 'POST', body })
      } catch {
        remaining.push(file)
        imageErrors.value.push(file.name)
      }
    }
    files.value = remaining
    const failedUrls: string[] = []
    for (const url of imageUrls.value
      .split(/\r?\n/)
      .map((url) => url.trim())
      .filter(Boolean)) {
      try {
        await request(`/${savedId.value}/images/url`, { method: 'POST', body: { url } })
      } catch {
        failedUrls.push(url)
        imageErrors.value.push(url)
      }
    }
    imageUrls.value = failedUrls.join('\n')
    if (!imageErrors.value.length) emit('saved', details)
  })
}
</script>
