import runningImage from '~/assets/images/workouts/running.png'
import ropeImage from '~/assets/images/workouts/rope.png'
import gymImage from '~/assets/images/workouts/gym.png'
import type { WorkoutCategory } from '~/types/workouts'

export const workoutCategories: { value: WorkoutCategory; label: string; image: string }[] = [
  { value: 'RUNNING', label: 'Corrida', image: runningImage },
  { value: 'JUMP_ROPE', label: 'Pular corda', image: ropeImage },
  { value: 'GYM', label: 'Academia', image: gymImage },
]

export function workoutCategory(value: WorkoutCategory) {
  return workoutCategories.find((category) => category.value === value)!
}
