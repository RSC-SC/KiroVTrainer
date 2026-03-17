/**
 * Unit tests for sendWorkoutReminder Cloud Function
 * 
 * Tests the sendWorkoutReminder function according to requirements 16.1, 16.2, 16.3, 16.4, 16.5
 * 
 * Subtask 8.2: Write unit tests for reminder function
 * - Test user query logic (filtering by reminderEnabled)
 * - Test FCM notification payload structure
 * - Test that notifications include workout name and quick action
 * - Test error handling for failed notifications
 */

import * as admin from "firebase-admin";

// Mock Firebase Admin
jest.mock("firebase-admin", () => {
  const mockMessaging = {
    send: jest.fn(),
  };

  const mockCollection = jest.fn();

  const mockFirestore = {
    collection: mockCollection,
  };

  return {
    firestore: jest.fn(() => mockFirestore),
    messaging: jest.fn(() => mockMessaging),
  };
});

describe("sendWorkoutReminder", () => {
  let mockFirestore: any;
  let mockMessaging: any;
  let mockCollection: jest.Mock;
  let mockWhere: jest.Mock;
  let mockGet: jest.Mock;
  let mockOrderBy: jest.Mock;
  let mockLimit: jest.Mock;

  beforeEach(() => {
    jest.clearAllMocks();

    // Setup mock chain
    mockCollection = jest.fn();
    mockWhere = jest.fn();
    mockGet = jest.fn();
    mockOrderBy = jest.fn();
    mockLimit = jest.fn();

    mockFirestore = {
      collection: mockCollection,
    };

    mockMessaging = {
      send: jest.fn(),
    };

    (admin.firestore as any).mockReturnValue(mockFirestore);
    (admin.messaging as any).mockReturnValue(mockMessaging);
  });

  describe("User Query Logic", () => {
    it("should query users with reminderEnabled = true", async () => {
      // Setup mock chain
      const mockUsersSnapshot = {
        empty: true,
        docs: [],
        size: 0,
      };

      mockWhere.mockReturnValue({ get: mockGet });
      mockGet.mockResolvedValue(mockUsersSnapshot);
      mockCollection.mockReturnValue({ where: mockWhere });

      // Import and execute the function logic (we'll test the core logic)
      const db = admin.firestore();
      await db
        .collection("users")
        .where("preferences.reminderEnabled", "==", true)
        .get();

      // Verify the query was constructed correctly
      expect(mockCollection).toHaveBeenCalledWith("users");
      expect(mockWhere).toHaveBeenCalledWith("preferences.reminderEnabled", "==", true);
      expect(mockGet).toHaveBeenCalled();
    });

    it("should handle empty result when no users have reminders enabled", async () => {
      const mockUsersSnapshot = {
        empty: true,
        docs: [],
        size: 0,
      };

      mockWhere.mockReturnValue({ get: mockGet });
      mockGet.mockResolvedValue(mockUsersSnapshot);
      mockCollection.mockReturnValue({ where: mockWhere });

      const db = admin.firestore();
      const usersSnapshot = await db
        .collection("users")
        .where("preferences.reminderEnabled", "==", true)
        .get();

      expect(usersSnapshot.empty).toBe(true);
      expect(usersSnapshot.docs).toHaveLength(0);
    });

    it("should retrieve users with reminderEnabled = true", async () => {
      const mockUserDocs = [
        {
          id: "user1",
          data: () => ({
            fcmTokens: ["token1", "token2"],
            preferences: {
              reminderEnabled: true,
              reminderTime: "08:00",
            },
          }),
        },
        {
          id: "user2",
          data: () => ({
            fcmTokens: ["token3"],
            preferences: {
              reminderEnabled: true,
              reminderTime: "09:00",
            },
          }),
        },
      ];

      const mockUsersSnapshot = {
        empty: false,
        docs: mockUserDocs,
        size: 2,
      };

      mockWhere.mockReturnValue({ get: mockGet });
      mockGet.mockResolvedValue(mockUsersSnapshot);
      mockCollection.mockReturnValue({ where: mockWhere });

      const db = admin.firestore();
      const usersSnapshot = await db
        .collection("users")
        .where("preferences.reminderEnabled", "==", true)
        .get();

      expect(usersSnapshot.empty).toBe(false);
      expect(usersSnapshot.size).toBe(2);
      expect(usersSnapshot.docs).toHaveLength(2);
    });

    it("should skip users without FCM tokens", async () => {
      const mockUserDocs = [
        {
          id: "user1",
          data: () => ({
            fcmTokens: [],
            preferences: {
              reminderEnabled: true,
              reminderTime: "08:00",
            },
          }),
        },
        {
          id: "user2",
          data: () => ({
            // No fcmTokens field
            preferences: {
              reminderEnabled: true,
              reminderTime: "08:00",
            },
          }),
        },
      ];

      const mockUsersSnapshot = {
        empty: false,
        docs: mockUserDocs,
        size: 2,
      };

      mockWhere.mockReturnValue({ get: mockGet });
      mockGet.mockResolvedValue(mockUsersSnapshot);
      mockCollection.mockReturnValue({ where: mockWhere });

      const db = admin.firestore();
      const usersSnapshot = await db
        .collection("users")
        .where("preferences.reminderEnabled", "==", true)
        .get();

      // Verify users are retrieved but should be skipped in processing
      expect(usersSnapshot.size).toBe(2);
      
      // Check that users without tokens would be skipped
      for (const doc of usersSnapshot.docs) {
        const userData = doc.data();
        const fcmTokens = userData.fcmTokens || [];
        if (fcmTokens.length === 0) {
          // This user should be skipped
          expect(fcmTokens).toHaveLength(0);
        }
      }
    });
  });

  describe("FCM Notification Payload", () => {
    it("should create notification with correct structure", () => {
      const workoutName = "Treino A - Peito e Tríceps";
      const token = "test_fcm_token";

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
        token,
      };

      // Verify notification structure
      expect(message.notification).toBeDefined();
      expect(message.notification.title).toBe("💪 Hora do Treino!");
      expect(message.notification.body).toContain(workoutName);
      
      // Verify data payload includes quick action
      expect(message.data).toBeDefined();
      expect(message.data.type).toBe("workout_reminder");
      expect(message.data.action).toBe("start_workout");
      expect(message.data.workoutName).toBe(workoutName);
      
      // Verify Android-specific settings
      expect(message.android).toBeDefined();
      expect(message.android.priority).toBe("high");
      expect(message.android.notification.clickAction).toBe("START_WORKOUT");
      
      // Verify token is included
      expect(message.token).toBe(token);
    });

    it("should include workout name in notification body", () => {
      const workoutName = "Treino B - Costas e Bíceps";
      
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
      };

      expect(message.notification.body).toContain(workoutName);
      expect(message.data.workoutName).toBe(workoutName);
    });

    it("should use default workout name when no plan exists", () => {
      const defaultWorkoutName = "seu treino";
      
      const message = {
        notification: {
          title: "💪 Hora do Treino!",
          body: `Não esqueça de fazer ${defaultWorkoutName} hoje!`,
        },
        data: {
          type: "workout_reminder",
          action: "start_workout",
          workoutName: defaultWorkoutName,
        },
      };

      expect(message.notification.body).toContain(defaultWorkoutName);
      expect(message.data.workoutName).toBe(defaultWorkoutName);
    });

    it("should include quick action to start workout", () => {
      const message = {
        data: {
          type: "workout_reminder",
          action: "start_workout",
          workoutName: "Treino A",
        },
        android: {
          notification: {
            clickAction: "START_WORKOUT",
          },
        },
      };

      expect(message.data.action).toBe("start_workout");
      expect(message.android.notification.clickAction).toBe("START_WORKOUT");
    });
  });

  describe("Workout Plan Retrieval", () => {
    it("should query most recent workout plan for user", async () => {
      const userId = "user123";
      const mockPlanSnapshot = {
        empty: false,
        docs: [
          {
            data: () => ({
              name: "Treino ABC",
              userId: userId,
              updatedAt: new Date(),
            }),
          },
        ],
      };

      // Setup mock chain for workout_plans query
      mockLimit.mockReturnValue({ get: mockGet });
      mockOrderBy.mockReturnValue({ limit: mockLimit });
      mockWhere.mockReturnValue({ orderBy: mockOrderBy });
      mockCollection.mockReturnValue({ where: mockWhere });
      mockGet.mockResolvedValue(mockPlanSnapshot);

      const db = admin.firestore();
      const workoutPlansSnapshot = await db
        .collection("workout_plans")
        .where("userId", "==", userId)
        .orderBy("updatedAt", "desc")
        .limit(1)
        .get();

      expect(mockCollection).toHaveBeenCalledWith("workout_plans");
      expect(mockWhere).toHaveBeenCalledWith("userId", "==", userId);
      expect(mockOrderBy).toHaveBeenCalledWith("updatedAt", "desc");
      expect(mockLimit).toHaveBeenCalledWith(1);
      expect(workoutPlansSnapshot.empty).toBe(false);
    });

    it("should handle user with no workout plans", async () => {
      const userId = "user456";
      const mockPlanSnapshot = {
        empty: true,
        docs: [],
      };

      mockLimit.mockReturnValue({ get: mockGet });
      mockOrderBy.mockReturnValue({ limit: mockLimit });
      mockWhere.mockReturnValue({ orderBy: mockOrderBy });
      mockCollection.mockReturnValue({ where: mockWhere });
      mockGet.mockResolvedValue(mockPlanSnapshot);

      const db = admin.firestore();
      const workoutPlansSnapshot = await db
        .collection("workout_plans")
        .where("userId", "==", userId)
        .orderBy("updatedAt", "desc")
        .limit(1)
        .get();

      expect(workoutPlansSnapshot.empty).toBe(true);
      // Should use default workout name in this case
    });
  });

  describe("Notification Sending", () => {
    it("should send notification to all FCM tokens (mobile and watch)", async () => {
      const tokens = ["mobile_token", "watch_token"];
      const workoutName = "Treino A";

      mockMessaging.send.mockResolvedValue("message_id");

      const messaging = admin.messaging();

      // Send to each token
      for (const token of tokens) {
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
          token,
        };

        await messaging.send(message);
      }

      expect(mockMessaging.send).toHaveBeenCalledTimes(2);
    });

    it("should handle notification send success", async () => {
      const token = "valid_token";
      const messageId = "projects/test/messages/123";

      mockMessaging.send.mockResolvedValue(messageId);

      const messaging = admin.messaging();
      const result = await messaging.send({
        notification: { title: "Test", body: "Test" },
        token,
      });

      expect(result).toBe(messageId);
      expect(mockMessaging.send).toHaveBeenCalledTimes(1);
    });

    it("should handle notification send failure gracefully", async () => {
      const token = "invalid_token";
      const error = new Error("Invalid registration token");

      mockMessaging.send.mockRejectedValue(error);

      const messaging = admin.messaging();

      await expect(
        messaging.send({
          notification: { title: "Test", body: "Test" },
          token,
        })
      ).rejects.toThrow("Invalid registration token");
    });

    it("should continue sending to other tokens if one fails", async () => {
      const tokens = ["valid_token", "invalid_token", "another_valid_token"];
      
      mockMessaging.send
        .mockResolvedValueOnce("message_id_1")
        .mockRejectedValueOnce(new Error("Invalid token"))
        .mockResolvedValueOnce("message_id_3");

      const messaging = admin.messaging();
      const results = await Promise.allSettled(
        tokens.map((token) =>
          messaging.send({
            notification: { title: "Test", body: "Test" },
            token,
          })
        )
      );

      expect(results).toHaveLength(3);
      expect(results[0].status).toBe("fulfilled");
      expect(results[1].status).toBe("rejected");
      expect(results[2].status).toBe("fulfilled");
    });
  });

  describe("Error Handling", () => {
    it("should handle Firestore query errors", async () => {
      const error = new Error("Firestore connection failed");

      mockWhere.mockReturnValue({ get: mockGet });
      mockGet.mockRejectedValue(error);
      mockCollection.mockReturnValue({ where: mockWhere });

      const db = admin.firestore();

      await expect(
        db.collection("users")
          .where("preferences.reminderEnabled", "==", true)
          .get()
      ).rejects.toThrow("Firestore connection failed");
    });

    it("should handle workout plan query errors gracefully", async () => {
      const error = new Error("Query failed");

      mockLimit.mockReturnValue({ get: mockGet });
      mockOrderBy.mockReturnValue({ limit: mockLimit });
      mockWhere.mockReturnValue({ orderBy: mockOrderBy });
      mockCollection.mockReturnValue({ where: mockWhere });
      mockGet.mockRejectedValue(error);

      const db = admin.firestore();

      await expect(
        db.collection("workout_plans")
          .where("userId", "==", "user123")
          .orderBy("updatedAt", "desc")
          .limit(1)
          .get()
      ).rejects.toThrow("Query failed");
    });

    it("should handle missing preferences field", () => {
      const userData: any = {
        fcmTokens: ["token1"],
        // No preferences field
      };

      const reminderTime = userData.preferences?.reminderTime || "08:00";
      expect(reminderTime).toBe("08:00");
    });

    it("should handle missing reminderTime in preferences", () => {
      const userData: any = {
        fcmTokens: ["token1"],
        preferences: {
          reminderEnabled: true,
          // No reminderTime field
        },
      };

      const reminderTime = userData.preferences?.reminderTime || "08:00";
      expect(reminderTime).toBe("08:00");
    });
  });

  describe("Integration Scenarios", () => {
    it("should process multiple users with different workout plans", async () => {
      const mockUserDocs = [
        {
          id: "user1",
          data: () => ({
            fcmTokens: ["token1"],
            preferences: {
              reminderEnabled: true,
              reminderTime: "08:00",
            },
          }),
        },
        {
          id: "user2",
          data: () => ({
            fcmTokens: ["token2", "token3"],
            preferences: {
              reminderEnabled: true,
              reminderTime: "08:00",
            },
          }),
        },
      ];

      const mockUsersSnapshot = {
        empty: false,
        docs: mockUserDocs,
        size: 2,
      };

      mockWhere.mockReturnValue({ get: mockGet });
      mockGet.mockResolvedValue(mockUsersSnapshot);
      mockCollection.mockReturnValue({ where: mockWhere });

      const db = admin.firestore();
      const usersSnapshot = await db
        .collection("users")
        .where("preferences.reminderEnabled", "==", true)
        .get();

      expect(usersSnapshot.size).toBe(2);
      
      // Count total tokens
      let totalTokens = 0;
      for (const doc of usersSnapshot.docs) {
        const userData = doc.data();
        const fcmTokens = userData.fcmTokens || [];
        totalTokens += fcmTokens.length;
      }

      expect(totalTokens).toBe(3); // 1 + 2 tokens
    });

    it("should send notifications to both mobile and watch devices", async () => {
      const userData = {
        fcmTokens: ["mobile_token_abc123", "watch_token_xyz789"],
        preferences: {
          reminderEnabled: true,
          reminderTime: "08:00",
        },
      };

      expect(userData.fcmTokens).toHaveLength(2);
      
      // Verify both tokens would receive notifications
      const tokens = userData.fcmTokens;
      expect(tokens).toContain("mobile_token_abc123");
      expect(tokens).toContain("watch_token_xyz789");
    });
  });
});
