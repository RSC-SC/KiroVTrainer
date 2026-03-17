import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { validateTrainingLog } from "./validation/trainingLogValidator";

// Request interface matching the design document
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

/**
 * HTTPS callable function to sync training logs from mobile/watch apps.
 * 
 * Requirements: 6.1, 6.2, 6.3, 6.4, 17.2
 * 
 * - Validates Firebase Auth token from context (6.2, 17.2)
 * - Parses and validates request data (6.3)
 * - Saves TrainingLog to Firestore with server timestamp (6.4)
 * - Returns success or error response (6.1)
 */
export const syncWorkout = onCall<SyncWorkoutRequest>(async (request) => {
    // 1. Validate Firebase Auth token (Requirement 6.2, 17.2)
    if (!request.auth) {
        throw new HttpsError(
            "unauthenticated",
            "O dispositivo deve estar autenticado para sincronizar treinos."
        );
    }

    const userId = request.auth.uid;
    const trainingLog = request.data.trainingLog;

    // 2. Parse and validate request data (Requirement 6.3)
    if (!trainingLog) {
        throw new HttpsError(
            "invalid-argument",
            "Os dados do treino estão ausentes."
        );
    }

    // Validate training log structure and data
    const validation = validateTrainingLog(trainingLog);
    if (!validation.isValid) {
        throw new HttpsError(
            "invalid-argument",
            "Validação do treino falhou",
            { errors: validation.errors }
        );
    }

    console.log(`[V-Trainer] Sincronizando treino para o usuário: ${userId}`);

    try {
        const db = admin.firestore();

        // 3. Calculate total volume for the workout (Requirement 6.3)
        const totalVolume = trainingLog.exercises.reduce((total, exercise) => {
            const exerciseVolume = exercise.sets.reduce((sum, set) => {
                return sum + (set.weight * set.reps);
            }, 0);
            return total + exerciseVolume;
        }, 0);

        // 4. Prepare training log document with server timestamp (Requirement 6.4)
        const logId = db.collection("training_logs").doc().id;
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

        // 5. Save to Firestore with server timestamp (Requirement 6.4)
        await db.collection("training_logs").doc(logId).set(logData);

        // 6. Update user's last workout timestamp (Requirement 6.4)
        await db.collection("users").doc(userId).update({
            lastWorkoutAt: admin.firestore.FieldValue.serverTimestamp()
        });

        // 7. Return success response (Requirement 6.1)
        return {
            success: true,
            logId,
            message: "Treino sincronizado com sucesso!",
            totalVolume
        };

    } catch (error) {
        console.error("[V-Trainer] Erro ao salvar treino no Firestore:", error);
        throw new HttpsError(
            "internal",
            "Erro interno ao processar a sincronização.",
            { error: String(error) }
        );
    }
});
