/**
 * Unit tests for calculateProgress Cloud Function
 * 
 * Tests the calculateProgress function according to requirements 8.1, 8.2, 8.3, 8.4, 8.5
 * 
 * Subtask 7.2: Write unit tests for calculateProgress function
 * - Test volume calculation integration
 * - Test record detection integration
 * - Test Firestore updates
 */

import * as admin from "firebase-admin";
import functionsTest from "firebase-functions-test";
import { calculateProgress } from "../calculateProgress";

// Initialize Firebase Functions Test
const test = functionsTest();

// Mock Firebase Admin
jest.mock("firebase-admin", () => {
  const mockFirestore = {
    collection: jest.fn(),
    FieldValue: {
      serverTimestamp: jest.fn(() => "MOCK_TIMESTAMP")
    }
  };

  const mockMessaging = {
    send: jest.fn()
  };

  return {
    firestore: jest.fn(() => mockFirestore),
    messaging: jest.fn(() => mockMessaging),
    initializeApp: jest.fn()
  };
});

describe("calculateProgress Cloud Function", () => {
  let mockFirestore: any;
  let mockMessaging: any;
  let mockCollection: jest.Mock;
  let mockDoc: jest.Mock;
  let mockGet: jest.Mock;
  let mockUpdate: jest.Mock;
  let mockWhere: jest.Mock;

  beforeEach(() => {
    jest.clearAllMocks();

    // Setup Firestore mocks
    mockGet = jest.fn();
    mockUpdate = jest.fn();
    mockWhere = jest.fn();
    mockDoc = jest.fn();
    mockCollection = jest.fn();

    mockFirestore = admin.firestore();
    mockMessaging = admin.messaging();

    // Setup mock chain for Firestore queries
    mockWhere.mockReturnThis();
    mockGet.mockResolvedValue({
      forEach: jest.fn(),
      docs: []
    });

    mockCollection.mockReturnValue({
      where: mockWhere,
      doc: mockDoc,
      get: mockGet
    });

    mockDoc.mockReturnValue({
      get: mockGet,
      update: mockUpdate
    });

    mockFirestore.collection = mockCollection;
  });

  afterAll(() => {
    test.cleanup();
  });

  describe("Volume Calculation Integration", () => {
    it("should calculate volume for each exercise using volume calculator", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [
              { reps: 10, weight: 60, restSeconds: 60 },
              { reps: 8, weight: 65, restSeconds: 60 }
            ]
          },
          {
            exerciseId: "squat",
            sets: [
              { reps: 12, weight: 100, restSeconds: 90 }
            ]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: {
          update: mockUpdate
        }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs query to return empty (no history)
      mockGet.mockResolvedValue({
        forEach: jest.fn(),
        docs: []
      });

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockUpdate).toHaveBeenCalledWith({
        exercises: expect.arrayContaining([
          expect.objectContaining({
            exerciseId: "bench_press",
            totalVolume: 1120, // (10*60) + (8*65) = 600 + 520 = 1120
            sets: mockTrainingLog.exercises[0].sets
          }),
          expect.objectContaining({
            exerciseId: "squat",
            totalVolume: 1200, // 12*100 = 1200
            sets: mockTrainingLog.exercises[1].sets
          })
        ])
      });
    });

    it("should handle exercises with no sets gracefully", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: []
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: mockUpdate }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      mockGet.mockResolvedValue({ forEach: jest.fn(), docs: [] });

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockUpdate).toHaveBeenCalledWith({
        exercises: expect.arrayContaining([
          expect.objectContaining({
            exerciseId: "bench_press",
            totalVolume: 0
          })
        ])
      });
    });
  });

  describe("Personal Record Detection Integration", () => {
    it("should detect max weight personal record", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [
              { reps: 10, weight: 80, restSeconds: 60 }, // New max weight
              { reps: 8, weight: 75, restSeconds: 60 }
            ]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: mockUpdate }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs with lower max weight
      const mockPreviousLog = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 70, restSeconds: 60 }],
            totalVolume: 700
          }
        ]
      };

      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({ data: () => mockPreviousLog });
        },
        docs: [{ data: () => mockPreviousLog }]
      });

      // Mock user document for PR update
      mockGet.mockResolvedValueOnce({
        data: () => ({ personalRecords: {} })
      });

      // Act
      await calculateProgress(mockEvent as any);

      // Assert - Training log should be updated with PR flag
      expect(mockUpdate).toHaveBeenCalledWith({
        exercises: expect.arrayContaining([
          expect.objectContaining({
            exerciseId: "bench_press",
            isPersonalRecord: true,
            recordType: expect.stringMatching(/max_weight|both/)
          })
        ])
      });
    });

    it("should detect max volume personal record", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [
              { reps: 15, weight: 60, restSeconds: 60 }, // More reps = higher volume
              { reps: 15, weight: 60, restSeconds: 60 }
            ]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: mockUpdate }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs with lower volume
      const mockPreviousLog = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 60, restSeconds: 60 }],
            totalVolume: 600
          }
        ]
      };

      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({ data: () => mockPreviousLog });
        },
        docs: [{ data: () => mockPreviousLog }]
      });

      mockGet.mockResolvedValueOnce({
        data: () => ({ personalRecords: {} })
      });

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockUpdate).toHaveBeenCalledWith({
        exercises: expect.arrayContaining([
          expect.objectContaining({
            exerciseId: "bench_press",
            isPersonalRecord: true,
            recordType: expect.stringMatching(/max_volume|both/)
          })
        ])
      });
    });

    it("should not mark as PR when performance is lower than history", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [
              { reps: 10, weight: 50, restSeconds: 60 } // Lower than previous
            ]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: mockUpdate }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs with higher performance
      const mockPreviousLog = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 70, restSeconds: 60 }],
            totalVolume: 700
          }
        ]
      };

      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({ data: () => mockPreviousLog });
        },
        docs: [{ data: () => mockPreviousLog }]
      });

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockUpdate).toHaveBeenCalledWith({
        exercises: expect.arrayContaining([
          expect.objectContaining({
            exerciseId: "bench_press",
            isPersonalRecord: false,
            recordType: null
          })
        ])
      });
    });

    it("should mark first-time exercise as PR", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "new_exercise",
            sets: [
              { reps: 10, weight: 50, restSeconds: 60 }
            ]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: mockUpdate }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock empty history (no previous logs)
      mockGet.mockResolvedValueOnce({
        forEach: jest.fn(),
        docs: []
      });

      mockGet.mockResolvedValueOnce({
        data: () => ({ personalRecords: {} })
      });

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockUpdate).toHaveBeenCalledWith({
        exercises: expect.arrayContaining([
          expect.objectContaining({
            exerciseId: "new_exercise",
            isPersonalRecord: true,
            recordType: "both"
          })
        ])
      });
    });
  });

  describe("Firestore Updates", () => {
    it("should update training log document with volume and PR data", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 60, restSeconds: 60 }]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: mockUpdate }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      mockGet.mockResolvedValue({ forEach: jest.fn(), docs: [] });

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockSnapshot.ref.update).toHaveBeenCalledTimes(1);
      expect(mockSnapshot.ref.update).toHaveBeenCalledWith({
        exercises: expect.any(Array)
      });
    });

    it("should update user document with personal records", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 80, restSeconds: 60 }]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: jest.fn() }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs with lower weight
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({
            data: () => ({
              exercises: [
                {
                  exerciseId: "bench_press",
                  sets: [{ reps: 10, weight: 70, restSeconds: 60 }],
                  totalVolume: 700
                }
              ]
            })
          });
        },
        docs: [{}]
      });

      // Mock user document
      const mockUserUpdate = jest.fn();
      mockGet.mockResolvedValueOnce({
        data: () => ({ personalRecords: {} })
      });

      mockDoc.mockReturnValue({
        get: mockGet,
        update: mockUserUpdate
      });

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockUserUpdate).toHaveBeenCalledWith(
        expect.objectContaining({
          personalRecords: expect.objectContaining({
            bench_press: expect.objectContaining({
              maxWeight: 80,
              maxVolume: 800,
              achievedAt: mockTrainingLog.timestamp
            })
          }),
          updatedAt: "MOCK_TIMESTAMP"
        })
      );
    });

    it("should preserve existing personal records when updating", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "squat",
            sets: [{ reps: 10, weight: 100, restSeconds: 90 }]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: jest.fn() }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({
            data: () => ({
              exercises: [
                {
                  exerciseId: "squat",
                  sets: [{ reps: 10, weight: 90, restSeconds: 90 }],
                  totalVolume: 900
                }
              ]
            })
          });
        },
        docs: [{}]
      });

      // Mock user document with existing PR for different exercise
      const mockUserUpdate = jest.fn();
      mockGet.mockResolvedValueOnce({
        data: () => ({
          personalRecords: {
            bench_press: {
              maxWeight: 80,
              maxVolume: 800,
              achievedAt: "2024-01-01T00:00:00Z"
            }
          }
        })
      });

      mockDoc.mockReturnValue({
        get: mockGet,
        update: mockUserUpdate
      });

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockUserUpdate).toHaveBeenCalledWith(
        expect.objectContaining({
          personalRecords: expect.objectContaining({
            bench_press: {
              maxWeight: 80,
              maxVolume: 800,
              achievedAt: "2024-01-01T00:00:00Z"
            },
            squat: expect.objectContaining({
              maxWeight: 100,
              maxVolume: 1000
            })
          })
        })
      );
    });
  });

  describe("FCM Notification Sending", () => {
    it("should send FCM notification when personal record is detected", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 80, restSeconds: 60 }]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: jest.fn() }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs with lower weight (to trigger PR)
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({
            data: () => ({
              exercises: [
                {
                  exerciseId: "bench_press",
                  sets: [{ reps: 10, weight: 70, restSeconds: 60 }],
                  totalVolume: 700
                }
              ]
            })
          });
        },
        docs: [{}]
      });

      // Mock user document with FCM tokens
      mockGet.mockResolvedValueOnce({
        data: () => ({
          personalRecords: {},
          fcmTokens: ["token1", "token2"]
        })
      });

      // Mock exercise document for notification text
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({
            id: "bench_press",
            data: () => ({ name: "Supino Reto" })
          });
        },
        docs: [{ id: "bench_press", data: () => ({ name: "Supino Reto" }) }]
      });

      const mockSend = jest.fn().mockResolvedValue({ messageId: "msg123" });
      mockMessaging.send = mockSend;

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockSend).toHaveBeenCalledTimes(2); // Once for each token
      expect(mockSend).toHaveBeenCalledWith(
        expect.objectContaining({
          notification: expect.objectContaining({
            title: "🎉 Novo Recorde Pessoal!",
            body: expect.stringContaining("Supino Reto")
          }),
          data: expect.objectContaining({
            type: "personal_record",
            recordCount: "1"
          }),
          token: expect.any(String)
        })
      );
    });

    it("should not send notification when no personal records detected", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 60, restSeconds: 60 }]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: jest.fn() }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs with same or higher performance
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({
            data: () => ({
              exercises: [
                {
                  exerciseId: "bench_press",
                  sets: [{ reps: 10, weight: 70, restSeconds: 60 }],
                  totalVolume: 700
                }
              ]
            })
          });
        },
        docs: [{}]
      });

      const mockSend = jest.fn();
      mockMessaging.send = mockSend;

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockSend).not.toHaveBeenCalled();
    });

    it("should handle missing FCM tokens gracefully", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 80, restSeconds: 60 }]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: jest.fn() }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs with lower weight (to trigger PR)
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({
            data: () => ({
              exercises: [
                {
                  exerciseId: "bench_press",
                  sets: [{ reps: 10, weight: 70, restSeconds: 60 }],
                  totalVolume: 700
                }
              ]
            })
          });
        },
        docs: [{}]
      });

      // Mock user document without FCM tokens
      mockGet.mockResolvedValueOnce({
        data: () => ({
          personalRecords: {},
          fcmTokens: []
        })
      });

      const mockSend = jest.fn();
      mockMessaging.send = mockSend;

      // Act & Assert - Should not throw error
      await expect(calculateProgress(mockEvent as any)).resolves.not.toThrow();
      expect(mockSend).not.toHaveBeenCalled();
    });

    it("should send notification with multiple exercise names when multiple PRs detected", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 80, restSeconds: 60 }]
          },
          {
            exerciseId: "squat",
            sets: [{ reps: 10, weight: 120, restSeconds: 90 }]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: jest.fn() }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs with lower performance for both exercises
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({
            data: () => ({
              exercises: [
                {
                  exerciseId: "bench_press",
                  sets: [{ reps: 10, weight: 70, restSeconds: 60 }],
                  totalVolume: 700
                },
                {
                  exerciseId: "squat",
                  sets: [{ reps: 10, weight: 100, restSeconds: 90 }],
                  totalVolume: 1000
                }
              ]
            })
          });
        },
        docs: [{}]
      });

      // Mock user document with FCM token
      mockGet.mockResolvedValueOnce({
        data: () => ({
          personalRecords: {},
          fcmTokens: ["token1"]
        })
      });

      // Mock exercise documents
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({ id: "bench_press", data: () => ({ name: "Supino Reto" }) });
          callback({ id: "squat", data: () => ({ name: "Agachamento" }) });
        },
        docs: [
          { id: "bench_press", data: () => ({ name: "Supino Reto" }) },
          { id: "squat", data: () => ({ name: "Agachamento" }) }
        ]
      });

      const mockSend = jest.fn().mockResolvedValue({ messageId: "msg123" });
      mockMessaging.send = mockSend;

      // Act
      await calculateProgress(mockEvent as any);

      // Assert
      expect(mockSend).toHaveBeenCalledWith(
        expect.objectContaining({
          notification: expect.objectContaining({
            body: expect.stringMatching(/Supino Reto.*Agachamento|Agachamento.*Supino Reto/)
          }),
          data: expect.objectContaining({
            recordCount: "2"
          })
        })
      );
    });
  });

  describe("Error Handling", () => {
    it("should handle missing userId gracefully", async () => {
      // Arrange
      const mockTrainingLog = {
        // userId is missing
        timestamp: new Date().toISOString(),
        exercises: []
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: jest.fn() }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Act & Assert - Should not throw error
      await expect(calculateProgress(mockEvent as any)).resolves.not.toThrow();
      expect(mockSnapshot.ref.update).not.toHaveBeenCalled();
    });

    it("should handle empty snapshot gracefully", async () => {
      // Arrange
      const mockEvent = {
        data: null,
        params: { logId: "log123" }
      };

      // Act & Assert - Should not throw error
      await expect(calculateProgress(mockEvent as any)).resolves.not.toThrow();
    });

    it("should continue execution even if Firestore query fails", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 60, restSeconds: 60 }]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: jest.fn() }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock Firestore query to throw error
      mockGet.mockRejectedValue(new Error("Firestore error"));

      // Act & Assert - Should not throw error, but should log it
      await expect(calculateProgress(mockEvent as any)).resolves.not.toThrow();
    });

    it("should continue execution even if notification sending fails", async () => {
      // Arrange
      const mockTrainingLog = {
        userId: "user123",
        timestamp: new Date().toISOString(),
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 80, restSeconds: 60 }]
          }
        ]
      };

      const mockSnapshot = {
        data: () => mockTrainingLog,
        ref: { update: jest.fn() }
      };

      const mockEvent = {
        data: mockSnapshot,
        params: { logId: "log123" }
      };

      // Mock previous logs with lower weight (to trigger PR)
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({
            data: () => ({
              exercises: [
                {
                  exerciseId: "bench_press",
                  sets: [{ reps: 10, weight: 70, restSeconds: 60 }],
                  totalVolume: 700
                }
              ]
            })
          });
        },
        docs: [{}]
      });

      // Mock user document with FCM token
      mockGet.mockResolvedValueOnce({
        data: () => ({
          personalRecords: {},
          fcmTokens: ["token1"]
        })
      });

      // Mock exercise documents
      mockGet.mockResolvedValueOnce({
        forEach: (callback: any) => {
          callback({ id: "bench_press", data: () => ({ name: "Supino Reto" }) });
        },
        docs: [{ id: "bench_press", data: () => ({ name: "Supino Reto" }) }]
      });

      // Mock FCM send to fail
      const mockSend = jest.fn().mockRejectedValue(new Error("FCM error"));
      mockMessaging.send = mockSend;

      // Act & Assert - Should not throw error
      await expect(calculateProgress(mockEvent as any)).resolves.not.toThrow();
    });
  });
});
