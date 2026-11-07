<template>
  <section v-if="images.length" class="game-gallery" :aria-label="viewerOnly ? 'Prévia de imagens' : 'Galeria do jogo'">
    <template v-if="!viewerOnly">
      <SectionHeading
        eyebrow="Imagens"
        title="Galeria"
        :summary="`${images.length} ${images.length === 1 ? 'imagem' : 'imagens'}`"
      />
      <div class="gallery-masonry">
        <button
          v-for="(image, index) in images"
          :key="image"
          type="button"
          class="gallery-image"
          :aria-label="`Ampliar imagem ${index + 1} de ${title}`"
          @click="open(index)"
        >
          <MediaImage :src="resolveMediaUrl(image)" :alt="`Imagem ${index + 1} de ${title}`" loading="lazy" />
          <span class="image-hint" aria-hidden="true">Ampliar ↗</span>
        </button>
      </div>
    </template>
    <dialog
      ref="dialog"
      class="gallery-lightbox"
      :class="{ 'with-selection': $slots.selection }"
      :aria-label="`Galeria de ${title}`"
      @close="closed"
      @click="backdrop"
      @keydown.left.prevent="move(-1)"
      @keydown.right.prevent="move(1)"
      @keydown.tab="trapFocus"
    >
      <template v-if="current">
        <div class="lightbox-image">
          <MediaImage :src="resolveMediaUrl(current)" :alt="`Imagem ${position + 1} de ${title}`" />
        </div>
        <div class="lightbox-heading">
          <p>
            {{ title }} <span role="status" aria-live="polite">{{ position + 1 }} / {{ images.length }}</span>
          </p>
          <button type="button" class="viewer-control" autofocus @click="dialog?.close()">Fechar ✕</button>
        </div>
        <button
          v-if="images.length > 1"
          type="button"
          class="viewer-control previous"
          aria-label="Imagem anterior"
          @click="move(-1)"
        >
          ←
        </button>
        <button
          v-if="images.length > 1"
          type="button"
          class="viewer-control next"
          aria-label="Próxima imagem"
          @click="move(1)"
        >
          →
        </button>
        <div v-if="$slots.selection" class="lightbox-selection"><slot name="selection" :index="position" /></div>
        <div v-if="images.length > 1" class="lightbox-thumbnails" aria-label="Escolher imagem">
          <button
            v-for="(image, index) in images"
            :key="image"
            type="button"
            :aria-label="`Ver imagem ${index + 1}`"
            :aria-pressed="index === position"
            @click="position = index"
          >
            <MediaImage
              :src="resolveMediaUrl(previewImages?.[index] || image)"
              alt=""
              loading="lazy"
              referrerpolicy="no-referrer"
            />
          </button>
        </div>
      </template>
    </dialog>
  </section>
</template>
<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
const props = defineProps<{ images: string[]; title: string; viewerOnly?: boolean; previewImages?: string[] }>()
const { resolveMediaUrl } = useMediaUrl()
const position = ref(0)
const current = computed(() => props.images[position.value])
const dialog = ref<HTMLDialogElement>()
let opener: HTMLElement | null = null
let previousOverflow = ''
function open(index: number) {
  if (!dialog.value || dialog.value.open || !props.images[index]) return
  position.value = index
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  dialog.value.showModal()
}
function move(direction: number) {
  if (props.images.length) position.value = (position.value + direction + props.images.length) % props.images.length
}
function closed() {
  document.body.style.overflow = previousOverflow
  if (opener?.isConnected) opener.focus()
}
function trapFocus(event: KeyboardEvent) {
  const controls = Array.from(
    dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled)') || [],
  )
  const first = controls[0]
  const last = controls.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last?.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first?.focus()
  }
}
function backdrop(event: MouseEvent) {
  if (event.target === dialog.value) dialog.value?.close()
}
watch(
  () => props.images.length,
  (length) => {
    if (!length && dialog.value?.open) dialog.value.close()
    if (position.value >= length) position.value = 0
  },
)
onBeforeUnmount(() => {
  if (dialog.value?.open) document.body.style.overflow = previousOverflow
})
defineExpose({ open })
</script>
<style scoped>
.game-gallery {
  display: grid;
  gap: 24px;
}
.gallery-masonry {
  columns: 4;
  column-gap: 16px;
}
.gallery-image {
  position: relative;
  display: block;
  width: 100%;
  padding: 0;
  margin: 0 0 16px;
  break-inside: avoid;
  border: 0;
  border-radius: 20px;
  overflow: hidden;
  background: var(--base-color-surface-warm);
  cursor: zoom-in;
}
.gallery-image :deep(img) {
  display: block;
  width: 100%;
  height: auto;
}
.image-hint {
  position: absolute;
  right: 12px;
  bottom: 12px;
  padding: 8px 12px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.75rem;
  font-weight: 700;
  opacity: 0;
}
.gallery-image:hover .image-hint,
.gallery-image:focus-visible .image-hint {
  opacity: 1;
}
.gallery-image:focus-visible,
.viewer-control:focus-visible,
.lightbox-thumbnails button:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
.gallery-lightbox {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100dvh;
  max-width: none;
  max-height: none;
  margin: 0;
  padding: 0;
  border: 0;
  background: #211f1b;
  color: #fff;
}
.gallery-lightbox::backdrop {
  background: #211f1b;
}
.lightbox-image {
  position: absolute;
  inset: 80px 72px 108px;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
  pointer-events: none;
}
.lightbox-image :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.with-selection .lightbox-image {
  bottom: 168px;
}
.lightbox-selection {
  position: absolute;
  bottom: 108px;
  left: 50%;
  transform: translateX(-50%);
}
.lightbox-heading {
  position: absolute;
  inset: 16px 24px auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.lightbox-heading p {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin: 0;
  font-size: 0.88rem;
  font-weight: 700;
}
.lightbox-heading span {
  font-size: 0.75rem;
  font-weight: 400;
  color: #e5e5e0;
  white-space: nowrap;
}
.viewer-control {
  min-width: 44px;
  min-height: 44px;
  border: 0;
  border-radius: 16px;
  padding: 10px 14px;
  background: #e5e5e0;
  color: #211922;
  cursor: pointer;
  font: inherit;
  font-size: 0.88rem;
}
.previous,
.next {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  font-size: 1.25rem;
}
.previous {
  left: 16px;
}
.next {
  right: 16px;
}
.lightbox-thumbnails {
  position: absolute;
  inset: auto 24px 16px;
  display: flex;
  gap: 8px;
  justify-content: safe center;
  overflow-x: auto;
  padding: 6px;
}
.lightbox-thumbnails button {
  flex: 0 0 64px;
  height: 64px;
  border: 2px solid transparent;
  border-radius: 12px;
  padding: 4px;
  background: #33332e;
  cursor: pointer;
}
.lightbox-thumbnails button[aria-pressed='true'] {
  border-color: #fff;
}
.lightbox-thumbnails :deep(img) {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 6px;
}
@media (max-width: 1100px) {
  .gallery-masonry {
    columns: 3;
  }
}
@media (max-width: 768px) {
  .gallery-masonry {
    columns: 2;
  }
}
@media (max-width: 575px) {
  .gallery-masonry {
    columns: 1;
  }
  .lightbox-heading {
    inset: 12px 12px auto;
  }
  .lightbox-heading p {
    flex-direction: column;
    gap: 4px;
  }
  .lightbox-image {
    inset: 80px 8px 100px;
  }
  .previous,
  .next {
    top: auto;
    bottom: 110px;
    transform: none;
  }
  .previous {
    left: 12px;
  }
  .next {
    right: 12px;
  }
  .lightbox-thumbnails {
    left: 8px;
    right: 8px;
  }
}
</style>
