import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

interface SyncWorkoutRequest {
  trainingLog: {
    timestamp: string;
    origin: string;
    workoutPlanId?: string;
    workoutDayName: string;
    duration: number;
    totalCalories?: number;
    exercises: Array<{
      exerciseId: string;
      sets: Array<{
        setNumber: number;
        reps: number;
        weight: number;
        restSeconds: number;
        heartRate?: number;
        completedAt: string;
      }>;
    }>;
  };
}

interface ValidationResult {
  isValid: boolean;
  errors: string[];
}

function validateTrainingLog(log: any): ValidationResult {
  const errors: string[] = [];
  
  // Required fields
  if (!log.timestamp) errors.push("timestamp is required");
  if (!log.origin) errors.push("origin is required");
  if (!log.workoutDayName) errors.push("workoutDayName is required");
  if (!log.exercises || !Array.isArray(log.exercises)) {
    errors.push("exercises array is required");
  }
  
  // Timestamp validation
  if (log.timestamp) {
    const timestamp = new Date(log.timestamp);
    const now = new Date();
    const maxFutureTime = new Date(now.getTime() + 24 * 60 * 60 * 1000);
    if (timestamp > maxFutureTime) {
      errors.push("timestamp cannot be more than 24 hours in the future");
    }
  }
  
  // Exercise validation
  log.exercises?.forEach((exercise: any, index: number) => {
    if (!exercise.exerciseId) {
      errors.push(`exercises[${index}].exerciseId is required`);
    }
    
    if (!exercise.sets || !Array.isArray(exercise.sets)) {
      errors.push(`exercises[${index}].sets array is required`);
    }
    
    exercise.sets?.forEach((set: any, setIndex: number) => {
      if (typeof set.weight !== "number" || set.weight <= 0) {
        errors.push(`exercises[${index}].sets[${setIndex}].weight must be positive`);
      }
      if (!Number.isInteger(set.reps) || set.reps <= 0) {
        errors.push(`exercises[${index}].sets[${setIndex}].reps must be positive integer`);
      }
      if (!Number.isInteger(set.restSeconds) || set.restSeconds < 0) {
        errors.push(`exercises[${index}].sets[${setIndex}].restSeconds must be non-negative integer`);
      }
    });
  });
  
  return {
    isValid: errors.length === 0,
    errors,
  };
}

export const syncWorkout = functions.https.onCall(
  async (data: SyncWorkoutRequest, context) => {
    // Authenticate request
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "User must be authenticated to sync workouts"
      );
    }
    
    const userId = context.auth.uid;
    const { trainingLog } = data;
    
    // Validate training log
    const validation = validateTrainingLog(trainingLog);
    if (!validation.isValid) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Training log validation failed",
        { errors: validation.errors }
      );
    }
    
    try {
      // Calculate total volume
      const totalVolume = trainingLog.exercises.reduce((total, exercise) => {
        const exerciseVolume = exercise.sets.reduce((sum, set) => {
          return sum + (set.weight * set.reps);
        }, 0);
        return total + exerciseVolume;
      }, 0);
      
      // Prepare training log document
      const logId = admin.firestore().collection("training_logs").doc().id;
      const logData = {
        logId,
        userId,
        workoutPlanId: trainingLog.workoutPlanId || null,
        workoutDayName: trainingLog.workoutDayName,
        timestamp: admin.firestore.Timestamp.fromDate(new Date(trainingLog.timestamp)),
        origin: trainingLog.origin,
        duration: trainingLog.duration,
        totalCalories: trainingLog.totalCalories || null,
        exercises: trainingLog.exercises.map((exercise) => ({
          exerciseId: exercise.exerciseId,
          sets: exercise.sets.map((set) => ({
            setNumber: set.setNumber,
            reps: set.reps,
            weight: set.weight,
            restSeconds: set.restSeconds,
            heartRate: set.heartRate || null,
            completedAt: admin.firestore.Timestamp.fromDate(new Date(set.completedAt)),
          })),
          totalVolume: exercise.sets.reduce((sum, set) => sum + (set.weight * set.reps), 0),
        })),
        totalVolume,
        syncStatus: "synced",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      };
      
      // Save to Firestore
      await admin.firestore().collection("training_logs").doc(logId).set(logData);
      
      return {
        success: true,
        logId,
        message: "Training log synced successfully",
      };
    } catch (error) {
      console.error("Error syncing workout:", error);
      throw new functions.https.HttpsError(
        "internal",
        "Failed to sync training log",
        { error: String(error) }
      );
    }
  }
);
