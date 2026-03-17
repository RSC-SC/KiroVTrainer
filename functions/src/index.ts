import * as admin from "firebase-admin";

// Initialize Firebase Admin
admin.initializeApp();

// Export Cloud Functions
export { syncWorkout } from "./syncWorkout";
export { calculateProgress } from "./calculateProgress";
export { sendWorkoutReminder } from "./sendWorkoutReminder";
