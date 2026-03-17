/**
 * Volume Calculator Utility
 * 
 * Calculates total training volume according to requirement 7.3:
 * - Total volume = sum of (weight × reps) for all sets in all exercises
 * 
 * This utility is used by:
 * - calculateProgress Cloud Function to compute volume for training logs
 * - Property-based tests to verify volume calculation correctness
 */

export interface SetData {
  reps: number;
  weight: number;
  restSeconds?: number;
  heartRate?: number;
  completedAt?: string;
}

export interface ExerciseData {
  exerciseId: string;
  sets: SetData[];
}

export interface TrainingLogData {
  exercises: ExerciseData[];
}

/**
 * Calculates the total volume for a single exercise
 * @param exercise - Exercise data containing sets
 * @returns Total volume for the exercise (sum of weight × reps for all sets)
 */
export function calculateExerciseVolume(exercise: ExerciseData): number {
  if (!exercise.sets || !Array.isArray(exercise.sets)) {
    return 0;
  }
  
  return exercise.sets.reduce((total, set) => {
    const weight = Number(set.weight) || 0;
    const reps = Number(set.reps) || 0;
    return total + (weight * reps);
  }, 0);
}

/**
 * Calculates the total volume for an entire training log
 * @param trainingLog - Training log data containing exercises
 * @returns Total volume for the entire training session (sum of all exercise volumes)
 */
export function calculateTotalVolume(trainingLog: TrainingLogData): number {
  if (!trainingLog.exercises || !Array.isArray(trainingLog.exercises)) {
    return 0;
  }
  
  return trainingLog.exercises.reduce((total, exercise) => {
    return total + calculateExerciseVolume(exercise);
  }, 0);
}

/**
 * Calculates volume for each exercise and returns an array with exercise volumes
 * @param trainingLog - Training log data containing exercises
 * @returns Array of objects with exerciseId and totalVolume
 */
export function calculateExerciseVolumes(trainingLog: TrainingLogData): Array<{ exerciseId: string; totalVolume: number }> {
  if (!trainingLog.exercises || !Array.isArray(trainingLog.exercises)) {
    return [];
  }
  
  return trainingLog.exercises.map(exercise => ({
    exerciseId: exercise.exerciseId,
    totalVolume: calculateExerciseVolume(exercise)
  }));
}
