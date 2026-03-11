import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

interface PersonalRecord {
  exerciseId: string;
  maxWeight: number;
  maxVolume: number;
  achievedAt: admin.firestore.Timestamp;
}

export const calculateProgress = functions.firestore
  .document("training_logs/{logId}")
  .onCreate(async (snapshot, context) => {
    const logData = snapshot.data();
    const userId = logData.userId;
    const logId = context.params.logId;
    
    try {
      // Get user's previous training logs
      const previousLogs = await admin.firestore()
        .collection("training_logs")
        .where("userId", "==", userId)
        .where("timestamp", "<", logData.timestamp)
        .get();
      
      // Build map of previous records per exercise
      const previousRecords = new Map<string, { maxWeight: number; maxVolume: number }>();
      
      previousLogs.forEach((doc) => {
        const log = doc.data();
        log.exercises.forEach((exercise: any) => {
          const exerciseId = exercise.exerciseId;
          const maxWeight = Math.max(...exercise.sets.map((s: any) => s.weight));
          const volume = exercise.totalVolume;
          
          const current = previousRecords.get(exerciseId) || { maxWeight: 0, maxVolume: 0 };
          previousRecords.set(exerciseId, {
            maxWeight: Math.max(current.maxWeight, maxWeight),
            maxVolume: Math.max(current.maxVolume, volume),
          });
        });
      });
      
      // Check for personal records in current log
      const newRecords: PersonalRecord[] = [];
      const updatedExercises = logData.exercises.map((exercise: any) => {
        const exerciseId = exercise.exerciseId;
        const maxWeight = Math.max(...exercise.sets.map((s: any) => s.weight));
        const volume = exercise.totalVolume;
        
        const previous = previousRecords.get(exerciseId);
        let isPersonalRecord = false;
        let recordType = null;
        
        if (!previous || maxWeight > previous.maxWeight) {
          isPersonalRecord = true;
          recordType = "max_weight";
          newRecords.push({
            exerciseId,
            maxWeight,
            maxVolume: previous ? Math.max(previous.maxVolume, volume) : volume,
            achievedAt: logData.timestamp,
          });
        } else if (volume > previous.maxVolume) {
          isPersonalRecord = true;
          recordType = "max_volume";
          newRecords.push({
            exerciseId,
            maxWeight: previous.maxWeight,
            maxVolume: volume,
            achievedAt: logData.timestamp,
          });
        }
        
        return {
          ...exercise,
          isPersonalRecord,
          recordType,
        };
      });
      
      // Update training log with personal record flags
      await snapshot.ref.update({
        exercises: updatedExercises,
      });
      
      // Update user's personal records
      if (newRecords.length > 0) {
        const userRef = admin.firestore().collection("users").doc(userId);
        const userDoc = await userRef.get();
        
        const personalRecords = userDoc.data()?.personalRecords || {};
        
        newRecords.forEach((record) => {
          personalRecords[record.exerciseId] = {
            maxWeight: record.maxWeight,
            maxVolume: record.maxVolume,
            achievedAt: record.achievedAt,
          };
        });
        
        await userRef.update({
          personalRecords,
          updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        
        // Send push notification for personal records
        await sendPersonalRecordNotification(userId, newRecords);
      }
      
      console.log(`Processed training log ${logId} for user ${userId}. Found ${newRecords.length} personal records.`);
    } catch (error) {
      console.error("Error calculating progress:", error);
      // Don't throw - we don't want to fail the log creation
    }
  });

async function sendPersonalRecordNotification(
  userId: string,
  records: PersonalRecord[]
): Promise<void> {
  try {
    // Get user's FCM tokens (would be stored in user document)
    const userDoc = await admin.firestore().collection("users").doc(userId).get();
    const fcmTokens = userDoc.data()?.fcmTokens || [];
    
    if (fcmTokens.length === 0) {
      console.log(`No FCM tokens found for user ${userId}`);
      return;
    }
    
    // Get exercise names
    const exerciseIds = records.map((r) => r.exerciseId);
    const exerciseDocs = await admin.firestore()
      .collection("exercises")
      .where(admin.firestore.FieldPath.documentId(), "in", exerciseIds)
      .get();
    
    const exerciseNames = new Map<string, string>();
    exerciseDocs.forEach((doc) => {
      exerciseNames.set(doc.id, doc.data().name);
    });
    
    // Create notification message
    const recordText = records.map((r) => {
      const name = exerciseNames.get(r.exerciseId) || r.exerciseId;
      return `${name}`;
    }).join(", ");
    
    const message = {
      notification: {
        title: "🎉 Novo Recorde Pessoal!",
        body: `Parabéns! Você bateu seu recorde em: ${recordText}`,
      },
      data: {
        type: "personal_record",
        recordCount: String(records.length),
      },
      tokens: fcmTokens,
    };
    
    await admin.messaging().sendMulticast(message);
    console.log(`Sent personal record notification to user ${userId}`);
  } catch (error) {
    console.error("Error sending notification:", error);
  }
}
