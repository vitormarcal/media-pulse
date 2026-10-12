<template>
  <section class="art-panel">
    <h2>{{ artistId ? 'Retrato' : 'Galeria' }}</h2>
    <div v-if="!artistId" class="art-gallery">
      <figure v-for="image in images" :key="image.id">
        <a :href="resolveMediaUrl(image.url) || undefined" target="_blank" rel="noopener"
          ><img
            :src="resolveMediaUrl(image.url) || undefined"
            :alt="image.caption || title || 'Imagem da obra'"
            loading="lazy"
        /></a>
        <figcaption v-if="image.caption">{{ image.caption }}</figcaption>
        <a v-if="image.sourceUrl" :href="image.sourceUrl" target="_blank" rel="noopener">Origem</a>
        <div class="art-actions">
          <button class="art-button" :disabled="busy || image.primary" @click="changePrimary(image.id)">
            {{ image.primary ? 'Principal' : 'Usar como principal' }}
          </button>
          <button class="art-button" :disabled="busy" @click="remove(image.id)">Remover</button>
        </div>
      </figure>
    </div>
    <form @submit.prevent="add">
      <fieldset :disabled="busy">
        <div class="art-fields">
          <label
            >{{ artistId ? 'Arquivo' : 'Arquivos' }}
            <input :key="fileKey" type="file" accept="image/jpeg,image/png" :multiple="!artistId" @change="selectFiles"
          /></label>
          <label>URL da imagem <input v-model="url" type="url" maxlength="4000" /></label>
          <label v-if="!artistId" class="art-wide">Legenda <input v-model="caption" maxlength="500" /></label>
        </div>
        <p class="art-muted">JPEG ou PNG, até 10 MB. Use a URL direta da imagem.</p>
        <div class="art-actions">
          <button class="art-button" :disabled="!files.length && !url">
            {{ busy ? 'Adicionando…' : 'Adicionar imagem' }}</button
          ><button v-if="artistId" type="button" class="art-button" @click="removePortrait">Remover retrato</button>
        </div>
      </fieldset>
    </form>
    <p v-if="error" class="art-error" role="alert">{{ error }}</p>
    <p v-if="success" class="art-success" role="status">{{ success }}</p>
  </section>
</template>
<script setup lang="ts">
import type { ArtworkImage } from '~/types/artworks'
const props = defineProps<{ artworkId?: number; artistId?: number; images?: ArtworkImage[]; title?: string }>()
const emit = defineEmits<{ changed: [] }>()
const { request, run, busy, error, success } = useArtworkApi()
const { resolveMediaUrl } = useMediaUrl()
const url = ref('')
const caption = ref('')
const files = ref<File[]>([])
const fileKey = ref(0)
const path = computed(() => (props.artistId ? `/artists/${props.artistId}/portrait` : `/${props.artworkId}/images`))
function selectFiles(event: Event) {
  files.value = Array.from((event.target as HTMLInputElement).files || [])
}
async function add() {
  await run(async () => {
    try {
      while (files.value.length) {
        const body = new FormData()
        body.append('image', files.value[0]!)
        await request(path.value, {
          method: 'POST',
          body,
          query: props.artistId ? undefined : { caption: caption.value || undefined },
        })
        files.value.shift()
      }
      fileKey.value++
      if (url.value.trim()) {
        await request(`${path.value}/url`, {
          method: 'POST',
          body: { url: url.value.trim(), caption: caption.value || null },
        })
        url.value = ''
      }
      caption.value = ''
    } finally {
      emit('changed')
    }
  })
}
async function changePrimary(id: number) {
  await run(async () => {
    await request(`/${props.artworkId}/images/${id}/primary`, { method: 'PUT' })
    emit('changed')
  })
}
async function remove(id: number) {
  if (!window.confirm('Remover esta imagem da galeria?')) return
  await run(async () => {
    await request(`/${props.artworkId}/images/${id}`, { method: 'DELETE' })
    emit('changed')
  })
}
async function removePortrait() {
  await run(async () => {
    await request(path.value, { method: 'DELETE' })
    emit('changed')
  })
}
</script>
