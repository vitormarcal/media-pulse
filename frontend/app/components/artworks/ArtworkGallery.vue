<template>
  <section class="art-viewer" aria-label="Imagens da obra">
    <template v-if="current">
      <figure class="art-viewer-figure">
        <button ref="enlargeButton" type="button" class="art-viewer-image" aria-label="Ampliar imagem" @click="enlarge">
          <img :src="resolveMediaUrl(current.url) || undefined" :alt="current.caption || title" />
        </button>
        <figcaption v-if="current.caption" class="art-muted">{{ current.caption }}</figcaption>
      </figure>
      <div class="art-viewer-toolbar">
        <div class="art-actions">
          <button
            v-if="images.length > 1"
            type="button"
            class="art-button"
            aria-label="Imagem anterior"
            @click="move(-1)"
          >
            ←
          </button>
          <span role="status" aria-live="polite">{{ position + 1 }} / {{ images.length }}</span>
          <button
            v-if="images.length > 1"
            type="button"
            class="art-button"
            aria-label="Próxima imagem"
            @click="move(1)"
          >
            →
          </button>
        </div>
        <div class="art-actions">
          <a v-if="current.sourceUrl" :href="current.sourceUrl" target="_blank" rel="noopener">Origem</a>
          <button type="button" class="art-button" @click="enlarge">Ampliar</button>
        </div>
      </div>
      <div v-if="images.length > 1" class="art-viewer-thumbnails" aria-label="Escolher imagem">
        <button
          v-for="(item, index) in images"
          :key="item.id"
          type="button"
          :aria-label="`Ver imagem ${index + 1}${item.caption ? `: ${item.caption}` : ''}`"
          :aria-pressed="item.id === current.id"
          @click="selectedId = item.id"
        >
          <img :src="resolveMediaUrl(item.url) || undefined" alt="" loading="lazy" />
        </button>
      </div>
      <dialog
        ref="dialog"
        class="art-lightbox"
        aria-label="Imagem ampliada"
        @close="closed"
        @click="backdrop"
        @keydown.left.prevent="move(-1)"
        @keydown.right.prevent="move(1)"
        @keydown.tab="trapFocus"
      >
        <div class="art-lightbox-content">
          <img :src="resolveMediaUrl(current.url) || undefined" :alt="current.caption || title" />
        </div>
        <span class="art-lightbox-count" role="status">{{ position + 1 }} / {{ images.length }}</span>
        <button type="button" class="art-button art-lightbox-close" autofocus @click="dialog?.close()">Fechar</button>
        <button
          v-if="images.length > 1"
          type="button"
          class="art-button art-lightbox-previous"
          aria-label="Imagem anterior"
          @click="move(-1)"
        >
          ←
        </button>
        <button
          v-if="images.length > 1"
          type="button"
          class="art-button art-lightbox-next"
          aria-label="Próxima imagem"
          @click="move(1)"
        >
          →
        </button>
        <p v-if="current.caption" class="art-lightbox-caption" :title="current.caption">{{ current.caption }}</p>
      </dialog>
    </template>
    <div v-else class="art-image-fallback">Sem imagem</div>
  </section>
</template>
<script setup lang="ts">
import type { ArtworkImage } from '~/types/artworks'
const props = defineProps<{ images: ArtworkImage[]; title: string }>()
const { resolveMediaUrl } = useMediaUrl()
const selectedId = ref<number | null>(null)
const current = computed(
  () =>
    props.images.find((item) => item.id === selectedId.value) ||
    props.images.find((item) => item.primary) ||
    props.images[0],
)
const position = computed(() => props.images.findIndex((item) => item.id === current.value?.id))
const dialog = ref<HTMLDialogElement>()
const enlargeButton = ref<HTMLButtonElement>()
let opener: HTMLElement | null = null
let previousOverflow = ''
function move(direction: number) {
  if (!props.images.length) return
  selectedId.value = props.images[(position.value + direction + props.images.length) % props.images.length]!.id
}
function enlarge() {
  if (!dialog.value || dialog.value.open) return
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialog.value.showModal()
}
function closed() {
  document.body.style.overflow = previousOverflow
  if (opener?.isConnected) opener.focus()
  else enlargeButton.value?.focus()
}
function backdrop(event: MouseEvent) {
  if (event.target === dialog.value) dialog.value?.close()
}
function trapFocus(event: KeyboardEvent) {
  const controls = Array.from(dialog.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') || [])
  const first = controls[0]
  const last = controls.at(-1)
  if (!first || !last) return
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
watch(
  () => current.value?.id,
  (id) => {
    if (id == null && dialog.value?.open) dialog.value.close()
  },
)
onBeforeUnmount(() => {
  if (dialog.value?.open) document.body.style.overflow = previousOverflow
})
</script>
