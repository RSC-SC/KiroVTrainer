import { onSchedule } from "firebase-functions/v2/scheduler";
import * as admin from "firebase-admin";

/**
 * Sends daily workout reminders to users with reminders enabled.
 * Runs daily at 08:00 by default.
 * 
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.5
 */
export const sendWorkoutReminder = onSchedule("every day 08:00", async (event) => {
    console.log("[V-Trainer] Starting daily workout reminder routine.");

    const db = admin.firestore();
    const messaging = admin.messaging();

    try {
        // Query users with reminderEnabled = true
        const usersSnapshot = await db
            .collection("users")
            .where("preferences.reminderEnabled", "==", true)
            .get();

        if (usersSnapshot.empty) {
            console.log("No users with reminders enabled found.");
            return;
        }

        console.log(`Found ${usersSnapshot.size} users with reminders enabled.`);

        const notificationPromises: Promise<void>[] = [];

        for (const userDoc of usersSnapshot.docs) {
            const userData = userDoc.data();
            const userId = userDoc.id;
            const fcmTokens = userData.fcmTokens || [];

            // Check if current time matches user's reminder time
            // For now, we send to all users since the function runs at 08:00
            // In a production system, you'd check the reminderTime against current time
            // and schedule multiple functions for different times

            if (fcmTokens.length === 0) {
                console.log(`User ${userId} has no FCM tokens, skipping.`);
                continue;
            }

            // Get user's most recent workout plan to include workout name
            let workoutName = "seu treino";
            try {
                const workoutPlansSnapshot = await db
                    .collection("workout_plans")
                    .where("userId", "==", userId)
                    .orderBy("updatedAt", "desc")
                    .limit(1)
                    .get();

                if (!workoutPlansSnapshot.empty) {
                    const plan = workoutPlansSnapshot.docs[0].data();
                    workoutName = plan.name || "seu treino";
                }
            } catch (error) {
                console.error(`Error fetching workout plan for user ${userId}:`, error);
                // Continue with default workout name
            }

            // Send notification to both mobile and watch (all FCM tokens)
            const message = {
                notification: {
                    title: "💪 Hora do Treino!",
                    body: `Não esqueça de fazer ${workoutName} hoje!`,
                },
                data: {
                    type: "workout_reminder",
                    action: "start_workout",
                    workoutName,
                },
                android: {
                    priority: "high" as const,
                    notification: {
                        sound: "default",
                        clickAction: "START_WORKOUT",
                    },
                },
            };

            // Send to each token (mobile and watch)
            for (const token of fcmTokens) {
                const sendPromise = messaging
                    .send({ ...message, token })
                    .then(() => {
                        console.log(`Sent reminder to user ${userId} on token ${token.substring(0, 10)}...`);
                    })
                    .catch((error: Error) => {
                        console.error(`Error sending reminder to user ${userId} on token ${token.substring(0, 10)}...:`, error.message);
                    });

                notificationPromises.push(sendPromise);
            }
        }

        await Promise.all(notificationPromises);
        console.log(`Completed sending ${notificationPromises.length} workout reminders.`);
    } catch (error) {
        console.error("[V-Trainer] Critical error sending reminders:", error);
        throw error;
    }
});
