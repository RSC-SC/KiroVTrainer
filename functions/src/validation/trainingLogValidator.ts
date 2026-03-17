/**
 * Training Log Validation Module
 * 
 * Validates training log data according to requirements 20.1-20.6:
 * - Required fields validation
 * - Positive weights validation
 * - Positive integer reps validation
 * - Non-negative rest times validation
 * - Timestamp validation (not more than 24 hours in future)
 */

export interface ValidationResult {
  isValid: boolean;
  errors: string[];
}

export interface TrainingLogSet {
  reps: number;
  weight: number;
  restSeconds: number;
  heartRate?: number;
}

export interface TrainingLogExercise {
  exerciseId: string;
  sets: TrainingLogSet[];
}

export interface TrainingLog {
  timestamp: string;
  exercises: TrainingLogExercise[];
  origin?: string;
  workoutPlanId?: string;
  workoutDayName?: string;
  duration?: number;
  totalCalories?: number;
}

/**
 * Validates a training log according to all requirements
 * @param log - The training log to validate
 * @returns ValidationResult with isValid flag and array of error messages
 */
export function validateTrainingLog(log: any): ValidationResult {
  const errors: string[] = [];
  
  // Requirement 20.1: Validate required fields
  if (!log.timestamp) {
    errors.push("timestamp is required");
  }
  
  if (!log.exercises || !Array.isArray(log.exercises)) {
    errors.push("exercises array is required");
    // Return early if exercises is not an array to avoid further errors
    return {
      isValid: errors.length === 0,
      errors
    };
  }
  
  // Requirement 20.6: Validate timestamp (not more than 24 hours in future)
  if (log.timestamp) {
    const timestamp = new Date(log.timestamp);
    const now = new Date();
    const maxFutureTime = new Date(now.getTime() + 24 * 60 * 60 * 1000);
    
    if (isNaN(timestamp.getTime())) {
      errors.push("timestamp must be a valid date");
    } else if (timestamp > maxFutureTime) {
      errors.push("timestamp cannot be more than 24 hours in the future");
    }
  }
  
  // Validate each exercise
  log.exercises.forEach((exercise: any, index: number) => {
    // Requirement 20.1: Validate exerciseId is present
    if (!exercise.exerciseId) {
      errors.push(`exercises[${index}].exerciseId is required`);
    }
    
    // Validate sets array exists
    if (!exercise.sets || !Array.isArray(exercise.sets)) {
      errors.push(`exercises[${index}].sets array is required`);
      return; // Skip set validation if sets is not an array
    }
    
    // Validate each set
    exercise.sets.forEach((set: any, setIndex: number) => {
      // Requirement 20.2: Validate positive weights
      if (typeof set.weight !== "number" || set.weight <= 0) {
        errors.push(`exercises[${index}].sets[${setIndex}].weight must be positive`);
      }
      
      // Requirement 20.3: Validate positive integer reps
      if (!Number.isInteger(set.reps) || set.reps <= 0) {
        errors.push(`exercises[${index}].sets[${setIndex}].reps must be positive integer`);
      }
      
      // Requirement 20.4: Validate non-negative rest times
      if (!Number.isInteger(set.restSeconds) || set.restSeconds < 0) {
        errors.push(`exercises[${index}].sets[${setIndex}].restSeconds must be non-negative integer`);
      }
    });
  });
  
  return {
    isValid: errors.length === 0,
    errors
  };
}
