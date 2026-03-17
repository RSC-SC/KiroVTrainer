import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { calculateExerciseVolumes, TrainingLogData } from "./utils/volumeCalculator";
import { detectPersonalRecords } from "./utils/personalRecordDetector";

/**
 * Cloud Function that triggers when a new training log is created.
 * Implements volume calculation and personal record detection according to requirements 8.1-8.5.
 * 
 * Responsibilities:
 * - Calculate volume using volume calculator utility
 * - Detect personal records using record detector utility
 * - Update training log document with PR flags (isPersonalRecord, recordType)
 * - Update user's personalRecords map in their user document
 * - Send FCM push notifications when PRs are detected
 */
export const calculateProgress = onDocumentCreated("training_logs/{logId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) {
    console.error("[calculateProgress] Error: Snapshot is empty");
    return;
  }

  const logData = snapshot.data();
  const userId = logData.userId;
  const logId = event.params.logId;

  if (!userId) {
    console.error("[calculateProgress] Error: userId is missing from training log");
    return;
  }

  try {
    // 1. Calculate volume for each exercise using volume calculator utility
    const exerciseVolumes = calculateExerciseVolumes(logData as TrainingLogData);
    console.log(`[calculateProgress] Calculated volumes for ${exerciseVolumes.length} exercises`);

    // 2. Query previous training logs to build historical data
    const previousLogs = await admin.firestore()
      .collection("training_logs")
      .where("userId", "==", userId)
      .where("timestamp", "<", logData.timestamp)
      .get();

    // Build map of historical max weight and volume per exercise
    const historicalData = new Map<string, { exerciseId: string; maxWeight: number; maxVolume: number }>();

    previousLogs.forEach((doc) => {
      const log = doc.data();
      if (log.exercises && Array.isArray(log.exercises)) {
        log.exercises.forEach((exercise: any) => {
          const exerciseId = exercise.exerciseId;
          
          // Get max weight from sets
          const maxWeight = exercise.sets && exercise.sets.length > 0
            ? Math.max(...exercise.sets.map((s: any) => Number(s.weight) || 0))
            : 0;
          
          // Get volume (use stored totalVolume if available, otherwise calculate)
          const volume = exercise.totalVolume || 0;

          const current = historicalData.get(exerciseId);
          if (!current) {
            historicalData.set(exerciseId, { exerciseId, maxWeight, maxVolume: volume });
          } else {
            historicalData.set(exerciseId, {
              exerciseId,
              maxWeight: Math.max(current.maxWeight, maxWeight),
              maxVolume: Math.max(current.maxVolume, volume)
            });
          }
        });
      }
    });

    console.log(`[calculateProgress] Built historical data for ${historicalData.size} exercises`);

    // 3. Detect personal records using record detector utility
    const recordResults = detectPersonalRecords(logData as TrainingLogData, historicalData);
    const personalRecords = recordResults.filter(r => r.isPersonalRecord);

    console.log(`[calculateProgress] Detected ${personalRecords.length} personal records`);

    // 4. Update training log with volume and PR flags
    const updatedExercises = logData.exercises.map((exercise: any, index: number) => {
      const volumeData = exerciseVolumes[index];
      const recordData = recordResults[index];

      return {
        ...exercise,
        totalVolume: volumeData.totalVolume,
        isPersonalRecord: recordData.isPersonalRecord,
        recordType: recordData.recordType || null
      };
    });

    await snapshot.ref.update({
      exercises: updatedExercises
    });

    console.log(`[calculateProgress] Updated training log ${logId} with volume and PR data`);

    // 5. Update user's personalRecords map if any PRs were detected
    if (personalRecords.length > 0) {
      const userRef = admin.firestore().collection("users").doc(userId);
      const userDoc = await userRef.get();

      const existingRecords = userDoc.data()?.personalRecords || {};

      // Update personal records with new maxes
      personalRecords.forEach((record) => {
        const currentMaxWeight = record.newMaxWeight || record.previousMaxWeight || 0;
        const currentMaxVolume = record.newMaxVolume || record.previousMaxVolume || 0;

        existingRecords[record.exerciseId] = {
          maxWeight: currentMaxWeight,
          maxVolume: currentMaxVolume,
          achievedAt: logData.timestamp
        };
      });

      await userRef.update({
        personalRecords: existingRecords,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      console.log(`[calculateProgress] Updated user ${userId} personal records`);

      // 6. Send push notification for personal records
      await sendPersonalRecordNotification(userId, personalRecords, logData.timestamp);
    }

    console.log(`[calculateProgress] Successfully processed training log ${logId} for user ${userId}`);
  } catch (error) {
    console.error("[calculateProgress] Error processing training log:", error);
    // Don't throw - we don't want to fail the log creation
  }
});

/**
 * Sends FCM push notification to user when personal records are detected.
 * Implements requirement 8.5.
 * 
 * @param userId - User ID to send notification to
 * @param records - Array of personal record results
 * @param timestamp - Timestamp of the training log
 */
async function sendPersonalRecordNotification(
  userId: string,
  records: Array<{ exerciseId: string; recordType?: string }>,
  timestamp: any
): Promise<void> {
  try {
    // Get user's FCM tokens
    const userDoc = await admin.firestore().collection("users").doc(userId).get();
    const fcmTokens = userDoc.data()?.fcmTokens || [];

    if (fcmTokens.length === 0) {
      console.log(`[sendPersonalRecordNotification] No FCM tokens found for user ${userId}`);
      return;
    }

    // Get exercise names for the notification
    const exerciseIds = records.map(r => r.exerciseId);
    const exerciseDocs = await admin.firestore()
      .collection("exercises")
      .where(admin.firestore.FieldPath.documentId(), "in", exerciseIds)
      .get();

    const exerciseNames = new Map<string, string>();
    exerciseDocs.forEach((doc) => {
      exerciseNames.set(doc.id, doc.data().name);
    });

    // Build notification text with exercise names and record types
    const recordText = records.map((r) => {
      const name = exerciseNames.get(r.exerciseId) || r.exerciseId;
      return name;
    }).join(", ");

    // Create FCM message
    const message = {
      notification: {
        title: "🎉 Novo Recorde Pessoal!",
        body: `Parabéns! Você bateu seu recorde em: ${recordText}`
      },
      data: {
        type: "personal_record",
        recordCount: String(records.length),
        exerciseIds: exerciseIds.join(","),
        timestamp: String(timestamp)
      }
    };

    // Send to all user's devices (mobile and watch)
    const sendPromises = fcmTokens.map((token: string) =>
      admin.messaging().send({ ...message, token }).catch((error) => {
        console.error(`[sendPersonalRecordNotification] Failed to send to token ${token}:`, error);
        return null;
      })
    );

    await Promise.all(sendPromises);
    console.log(`[sendPersonalRecordNotification] Sent notification to user ${userId} for ${records.length} records`);
  } catch (error) {
    console.error("[sendPersonalRecordNotification] Error sending notification:", error);
    // Don't throw - notification failure shouldn't fail the entire function
  }
}