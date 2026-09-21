import type { Workout, WorkoutCategory, WorkoutDraft, WorkoutHistory } from '~/types/workouts'

export function useWorkouts() {
  const config = useRuntimeConfig()
  const base = String(config.public.apiBase || '/api')
    .replace(/\/$/, '')
    .replace(/\/api$/, '')
  const endpoint = `${base}/api/workouts`

  function history(category: WorkoutCategory | null, page = 0) {
    return $fetch<WorkoutHistory>(endpoint, { query: { category: category ?? undefined, page, limit: 24 } })
  }

  function create(draft: WorkoutDraft, photo: File | null) {
    const body = new FormData()
    body.append('workout', new Blob([JSON.stringify(draft)], { type: 'application/json' }))
    if (photo) body.append('photo', photo)
    return $fetch<Workout>(endpoint, { method: 'POST', body })
  }

  return { history, create }
}
