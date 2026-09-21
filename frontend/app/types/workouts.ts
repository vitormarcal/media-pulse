export type WorkoutCategory = 'RUNNING' | 'JUMP_ROPE' | 'GYM'

export interface Workout {
  id: number
  category: WorkoutCategory
  startedAt: string
  durationMinutes: number
  distanceKm: number | null
  jumps: number | null
  location: string | null
  photoUrl: string | null
}

export interface WorkoutHistory {
  items: Workout[]
  nextPage: number | null
}

export interface WorkoutDraft {
  category: WorkoutCategory
  startedAt: string
  durationMinutes: number
  distanceKm?: number
  jumps?: number
  location?: string
}
