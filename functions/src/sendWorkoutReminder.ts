import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const sendWorkoutReminder = functions.pubsub
  .schedule("every day 08:00")
  .timeZone("America/Sao_Paulo")
  .onRun(async (context) => {
    try {
      // Get all users with reminders enabled
      const usersSnapshot = await admin.firestore()
        .collection("users")
        .where("preferences.reminderEnabled", "==", true)
        .get();
      
      console.log(`Found ${usersSnapshot.size} users with reminders enabled`);
      
      const notifications: Promise<any>[] = [];
      
      for (const userDoc of usersSnapshot.docs) {
        const userData = userDoc.data();
        const userId = userDoc.id;
        const fcmTokens = userData.fcmTokens || [];
        
        if (fcmTokens.length === 0) {
          continue;
        }
        
        // Get user's most recent workout plan
        const workoutPlansSnapshot = await admin.firestore()
          .collection("workout_plans")
          .where("userId", "==", userId)
          .orderBy("updatedAt", "desc")
          .limit(1)
          .get();
        
        let workoutName = "seu treino";
        if (!workoutPlansSnapshot.empty) {
          const plan = workoutPlansSnapshot.docs[0].data();
          workoutName = plan.name || "seu treino";
        }
        
        // Send notification
        const message = {
          notification: {
            title: "💪 Hora do Treino!",
            body: `Não esqueça de fazer ${workoutName} hoje!`,
          },
          data: {
            type: "workout_reminder",
            workoutName,
          },
          tokens: fcmTokens,
        };
        
        notifications.push(
          admin.messaging().sendMulticast(message)
            .then((response) => {
              console.log(`Sent reminder to user ${userId}: ${response.successCount} successful`);
              return response;
            })
            .catch((error) => {
              console.error(`Error sending reminder to user ${userId}:`, error);
            })
        );
      }
      
      await Promise.all(notifications);
      console.log(`Completed sending ${notifications.length} workout reminders`);
    } catch (error) {
      console.error("Error in sendWorkoutReminder:", error);
    }
  });
