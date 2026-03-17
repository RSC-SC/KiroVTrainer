/**
 * Personal Record Detector Utility
 *
 * Detects personal records (PRs) by comparing exercise performance against user's history.
 * Implements requirements 8.1, 8.2, 8.3 for max weight and max volume record detection.
 *
 * This utility is used by:
 * - calculateProgress Cloud Function to detect PRs when training logs are saved
 * - Property-based tests to verify PR detection correctness
 */

import { calculateExerciseVolume } from "./volumeCalculator";

/**
 * Represents a set within an exercise
 */
export interface SetData {
  reps: number;
  weight: number;
  restSeconds?: number;
  heartRate?: number;
}

/**
 * Represents an exercise with its sets
 */
export interface ExerciseData {
  exerciseId: string;
  sets: SetData[];
}

/**
 * Represents a training log with exercises
 */
export interface TrainingLogData {
  exercises: ExerciseData[];
  timestamp?: string;
}

/**
 * Represents historical exercise data for comparison
 */
export interface HistoricalExerciseData {
  exerciseId: string;
  maxWeight: number;
  maxVolume: number;
}

/**
 * Record type enumeration
 */
export enum RecordType {
  MAX_WEIGHT = "max_weight",
  MAX_VOLUME = "max_volume",
  BOTH = "both"
}

/**
 * Result of personal record detection for an exercise
 */
export interface PersonalRecordResult {
  exerciseId: string;
  isPersonalRecord: boolean;
  recordType?: RecordType;
  newMaxWeight?: number;
  newMaxVolume?: number;
  previousMaxWeight?: number;
  previousMaxVolume?: number;
}

/**
 * Gets the maximum weight used in any single set of an exercise
 * @param exercise - Exercise data containing sets
 * @returns Maximum weight across all sets
 */
export function getMaxWeight(exercise: ExerciseData): number {
  if (!exercise.sets || !Array.isArray(exercise.sets) || exercise.sets.length === 0) {
    return 0;
  }

  return exercise.sets.reduce((max, set) => {
    const weight = Number(set.weight) || 0;
    return Math.max(max, weight);
  }, 0);
}

/**
 * Detects if an exercise achieves a personal record compared to historical data
 * @param exercise - Current exercise data
 * @param historicalData - Historical max weight and volume for this exercise
 * @returns PersonalRecordResult indicating if a PR was achieved and what type
 */
export function detectPersonalRecord(
  exercise: ExerciseData,
  historicalData: HistoricalExerciseData
): PersonalRecordResult {
  const currentMaxWeight = getMaxWeight(exercise);
  const currentVolume = calculateExerciseVolume(exercise);

  const isMaxWeightRecord = currentMaxWeight > historicalData.maxWeight;
  const isMaxVolumeRecord = currentVolume > historicalData.maxVolume;

  let recordType: RecordType | undefined;
  if (isMaxWeightRecord && isMaxVolumeRecord) {
    recordType = RecordType.BOTH;
  } else if (isMaxWeightRecord) {
    recordType = RecordType.MAX_WEIGHT;
  } else if (isMaxVolumeRecord) {
    recordType = RecordType.MAX_VOLUME;
  }

  return {
    exerciseId: exercise.exerciseId,
    isPersonalRecord: isMaxWeightRecord || isMaxVolumeRecord,
    recordType,
    newMaxWeight: isMaxWeightRecord ? currentMaxWeight : undefined,
    newMaxVolume: isMaxVolumeRecord ? currentVolume : undefined,
    previousMaxWeight: historicalData.maxWeight,
    previousMaxVolume: historicalData.maxVolume
  };
}

/**
 * Detects personal records for all exercises in a training log
 * @param trainingLog - Current training log
 * @param historicalData - Map of exerciseId to historical max weight and volume
 * @returns Array of PersonalRecordResult for each exercise
 */
export function detectPersonalRecords(
  trainingLog: TrainingLogData,
  historicalData: Map<string, HistoricalExerciseData>
): PersonalRecordResult[] {
  if (!trainingLog.exercises || !Array.isArray(trainingLog.exercises)) {
    return [];
  }

  return trainingLog.exercises.map(exercise => {
    const history = historicalData.get(exercise.exerciseId);
    
    // If no historical data exists, this is the first time doing this exercise
    // We consider it a PR by default
    if (!history) {
      const currentMaxWeight = getMaxWeight(exercise);
      const currentVolume = calculateExerciseVolume(exercise);
      
      return {
        exerciseId: exercise.exerciseId,
        isPersonalRecord: true,
        recordType: RecordType.BOTH,
        newMaxWeight: currentMaxWeight,
        newMaxVolume: currentVolume,
        previousMaxWeight: 0,
        previousMaxVolume: 0
      };
    }

    return detectPersonalRecord(exercise, history);
  });
}

/**
 * Updates historical data with new records from a training log
 * @param historicalData - Current historical data map
 * @param trainingLog - New training log to process
 * @returns Updated historical data map
 */
export function updateHistoricalData(
  historicalData: Map<string, HistoricalExerciseData>,
  trainingLog: TrainingLogData
): Map<string, HistoricalExerciseData> {
  const updated = new Map(historicalData);

  if (!trainingLog.exercises || !Array.isArray(trainingLog.exercises)) {
    return updated;
  }

  for (const exercise of trainingLog.exercises) {
    const currentMaxWeight = getMaxWeight(exercise);
    const currentVolume = calculateExerciseVolume(exercise);
    
    const existing = updated.get(exercise.exerciseId);
    
    if (!existing) {
      // First time seeing this exercise
      updated.set(exercise.exerciseId, {
        exerciseId: exercise.exerciseId,
        maxWeight: currentMaxWeight,
        maxVolume: currentVolume
      });
    } else {
      // Update if new records achieved
      updated.set(exercise.exerciseId, {
        exerciseId: exercise.exerciseId,
        maxWeight: Math.max(existing.maxWeight, currentMaxWeight),
        maxVolume: Math.max(existing.maxVolume, currentVolume)
      });
    }
  }

  return updated;
}
