<template>
  <dialog ref="dialog" class="composer" aria-labelledby="workout-title" @cancel="handleCancel" @close="emit('close')">
    <div class="composer-heading">
      <div>
        <p class="eyebrow">Um momento para você</p>
        <h2 id="workout-title">{{ category ? 'Guarde este treino' : 'Como você se movimentou?' }}</h2>
      </div>
      <button type="button" class="close-button" aria-label="Fechar registro" :disabled="saving" @click="close">
        ×
      </button>
    </div>

    <div v-if="!category" class="category-choices">
      <button
        v-for="item in workoutCategories"
        :key="item.value"
        type="button"
        class="category-choice"
        @click="choose(item.value)"
      >
        <img :src="item.image" alt="" width="280" height="280" />
        <strong>{{ item.label }}</strong>
      </button>
    </div>

    <form v-else class="entry" @submit.prevent="save">
      <fieldset :disabled="saving" class="entry-fields">
        <div class="memory">
          <img :src="preview || workoutCategory(category).image" :alt="preview ? 'Foto escolhida para o treino' : ''" />
          <div class="memory-actions">
            <button type="button" class="soft-button" @click="fileInput?.click()">
              {{ photo ? 'Trocar foto' : 'Adicionar foto' }}
            </button>
            <button v-if="photo" type="button" class="soft-button" @click="removePhoto">Remover foto</button>
          </div>
          <input
            ref="fileInput"
            class="file-input"
            type="file"
            accept="image/jpeg,image/png"
            aria-label="Foto do treino"
            @change="selectPhoto"
          />
          <p class="photo-hint">Uma lembrança, se quiser. JPEG ou PNG, até 10 MB.</p>
        </div>

        <div class="details">
          <div class="activity-line">
            <h3>{{ workoutCategory(category).label }}</h3>
            <button type="button" class="text-button" @click="category = null">Trocar atividade</button>
          </div>
          <div class="when">
            <label><span>Dia</span><input v-model="date" aria-label="Dia do treino" type="date" required /></label>
            <label
              ><span>Horário de início</span><input v-model="time" aria-label="Horário de início" type="time" required
            /></label>
          </div>
          <div class="metrics">
            <label class="metric">
              <span>Duração</span>
              <div>
                <input
                  ref="durationInput"
                  v-model="duration"
                  aria-label="Duração em minutos"
                  type="number"
                  inputmode="numeric"
                  min="1"
                  max="2147483647"
                  step="1"
                  placeholder="30"
                  required
                /><span>min</span>
              </div>
            </label>
            <label v-if="category === 'RUNNING'" class="metric">
              <span>Distância</span>
              <div>
                <input
                  v-model="distance"
                  aria-label="Distância em quilômetros"
                  type="number"
                  inputmode="decimal"
                  min="0.001"
                  max="9999999.999"
                  step="0.001"
                  placeholder="5"
                  required
                /><span>km</span>
              </div>
            </label>
            <label v-if="category === 'JUMP_ROPE'" class="metric">
              <span>Pulos <small>opcional</small></span>
              <div>
                <input
                  v-model="jumps"
                  aria-label="Quantidade de pulos, opcional"
                  type="number"
                  inputmode="numeric"
                  min="1"
                  max="2147483647"
                  step="1"
                  placeholder="—"
                />
              </div>
            </label>
          </div>
          <div class="duration-shortcuts" aria-label="Atalhos de duração">
            <button
              v-for="minutes in [15, 30, 45, 60]"
              :key="minutes"
              type="button"
              class="soft-button"
              @click="duration = String(minutes)"
            >
              {{ minutes }} min
            </button>
          </div>
          <label v-if="showLocation" class="location">
            <span>Local <small>opcional</small></span>
            <input
              ref="locationInput"
              v-model="location"
              type="text"
              maxlength="200"
              placeholder="Parque, academia, sua casa…"
            />
          </label>
          <button v-else type="button" class="text-button add-location" @click="revealLocation">
            + Adicionar local
          </button>
        </div>
      </fieldset>
      <p v-if="error" class="error-message" role="alert">{{ error }}</p>
      <div class="save-row">
        <button type="submit" class="primary-button" :disabled="saving">
          {{ saving ? 'Guardando…' : 'Guardar treino' }}
        </button>
      </div>
    </form>
  </dialog>
</template>

<script setup lang="ts">
import type { Workout, WorkoutCategory, WorkoutDraft } from '~/types/workouts'
import { workoutCategories, workoutCategory } from '~/utils/workouts'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; saved: [workout: Workout] }>()
const api = useWorkouts()
const dialog = ref<HTMLDialogElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const durationInput = ref<HTMLInputElement | null>(null)
const locationInput = ref<HTMLInputElement | null>(null)
const category = ref<WorkoutCategory | null>(null)
const date = ref('')
const time = ref('')
const duration = ref('')
const distance = ref('')
const jumps = ref('')
const location = ref('')
const showLocation = ref(false)
const photo = ref<File | null>(null)
const preview = ref('')
const saving = ref(false)
const error = ref('')

watch(
  () => props.open,
  async (open) => {
    await nextTick()
    if (open) {
      if (!date.value) {
        const now = new Date()
        const pad = (n: number) => String(n).padStart(2, '0')
        date.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
        time.value = `${pad(now.getHours())}:${pad(now.getMinutes())}`
      }
      dialog.value?.showModal()
    } else dialog.value?.close()
  },
)

async function choose(value: WorkoutCategory) {
  category.value = value
  distance.value = ''
  jumps.value = ''
  error.value = ''
  await nextTick()
  durationInput.value?.focus()
}

function close() {
  if (!saving.value) dialog.value?.close()
}

function handleCancel(event: Event) {
  if (saving.value) event.preventDefault()
}

async function revealLocation() {
  showLocation.value = true
  await nextTick()
  locationInput.value?.focus()
}

function removePhoto() {
  if (preview.value) URL.revokeObjectURL(preview.value)
  preview.value = ''
  photo.value = null
  if (fileInput.value) fileInput.value.value = ''
}

function selectPhoto(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 10 * 1024 * 1024 || !file.size) {
    error.value = 'Escolha uma foto JPEG ou PNG de até 10 MB.'
    input.value = ''
    return
  }
  removePhoto()
  photo.value = file
  preview.value = URL.createObjectURL(file)
  error.value = ''
}

async function save() {
  if (!category.value || saving.value) return
  error.value = ''
  const started = new Date(`${date.value}T${time.value}`)
  if (Number.isNaN(started.getTime())) {
    error.value = 'Confira o dia e o horário do treino.'
    return
  }
  const draft: WorkoutDraft = {
    category: category.value,
    startedAt: started.toISOString(),
    durationMinutes: Number(duration.value),
    ...(category.value === 'RUNNING' ? { distanceKm: Number(distance.value) } : {}),
    ...(category.value === 'JUMP_ROPE' && jumps.value !== '' ? { jumps: Number(jumps.value) } : {}),
    ...(location.value.trim() ? { location: location.value.trim() } : {}),
  }
  saving.value = true
  try {
    const workout = await api.create(draft, photo.value)
    emit('saved', workout)
    removePhoto()
    category.value = null
    date.value = ''
    duration.value = ''
    distance.value = ''
    jumps.value = ''
    location.value = ''
    showLocation.value = false
    dialog.value?.close()
  } catch (cause: unknown) {
    const response = cause as { status?: number; data?: { detail?: string; message?: string } }
    error.value =
      response.status === 413
        ? 'A foto excede o limite de envio. Escolha uma imagem menor.'
        : response.data?.detail ||
          response.data?.message ||
          'Não foi possível guardar o treino. Seus dados continuam aqui; tente novamente.'
  } finally {
    saving.value = false
  }
}

onBeforeUnmount(() => {
  if (preview.value) URL.revokeObjectURL(preview.value)
})
</script>

<style scoped>
.composer {
  width: min(880px, calc(100vw - 24px));
  max-height: calc(100dvh - 32px);
  padding: 28px;
  border: 0;
  border-radius: 32px;
  color: var(--base-color-text-primary);
  background: var(--base-color-surface);
}
.composer::backdrop {
  background: rgb(33 25 34 / 45%);
}
.composer-heading,
.activity-line,
.save-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.composer-heading {
  margin-bottom: 24px;
}
.eyebrow {
  margin: 0 0 8px;
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
}
h2 {
  margin: 0;
  font-size: clamp(1.5rem, 4vw, 1.85rem);
  letter-spacing: -0.04em;
}
h3 {
  margin: 0;
  font-size: 1.4rem;
}
button {
  cursor: pointer;
}
button:disabled {
  cursor: wait;
  opacity: 0.6;
}
.close-button {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 50%;
  background: var(--base-color-surface-warm);
  font-size: 1.6rem;
}
.category-choices {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.category-choice {
  padding: 0 0 16px;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
  color: inherit;
  text-align: left;
}
.category-choice:hover {
  border-color: var(--base-color-text-muted);
}
.category-choice img {
  width: 100%;
  height: auto;
  aspect-ratio: 1;
  object-fit: cover;
}
.category-choice strong {
  display: block;
  padding: 14px 14px 0;
}
.entry-fields {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 28px;
  padding: 0;
  margin: 0;
  border: 0;
  min-width: 0;
}
.memory {
  min-width: 0;
}
.memory > img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: 20px;
}
.memory-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}
.photo-hint {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
  margin-bottom: 0;
}
.file-input {
  display: none;
}
.details {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}
.text-button {
  border: 0;
  padding: 8px 0;
  background: transparent;
  color: var(--base-color-text-secondary);
  text-decoration: underline;
  text-underline-offset: 4px;
  font-size: 0.8rem;
}
.when,
.metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
label {
  display: grid;
  gap: 8px;
  min-width: 0;
}
label > span {
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
}
input {
  width: 100%;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  color: inherit;
  background: var(--base-color-surface-strong);
}
input:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 2px;
}
.metric {
  padding: 14px;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
}
.metric > div {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.metric input {
  border: 0;
  border-radius: 0;
  padding: 4px 0;
  font-size: 1.9rem;
  background: transparent;
}
.metric > div > span,
small {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
}
.duration-shortcuts {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: -8px;
}
.soft-button {
  border: 0;
  border-radius: 16px;
  padding: 10px 12px;
  background: var(--base-color-surface-warm);
  color: inherit;
  font-size: 0.8rem;
}
.add-location {
  align-self: flex-start;
}
.save-row {
  justify-content: flex-end;
  margin-top: 24px;
}
.primary-button {
  padding: 14px 24px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-brand-red);
  color: white;
  font-weight: 600;
}
.error-message {
  color: #9e0a0a;
}
@media (max-width: 575px) {
  .composer {
    padding: 20px;
    border-radius: 24px;
  }
  .category-choices {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  .category-choice {
    display: flex;
    align-items: center;
    padding: 0;
  }
  .category-choice img {
    width: 92px;
    height: 92px;
  }
  .category-choice strong {
    padding: 16px;
  }
  .entry-fields {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .memory > img {
    max-height: 170px;
    aspect-ratio: 2;
  }
  .save-row button {
    width: 100%;
  }
}
</style>
