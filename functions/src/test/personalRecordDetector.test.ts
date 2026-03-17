/**
 * Tests for Personal Record Detector Utility
 * 
 * Tests the personal record detection functions according to requirements 8.1, 8.2, 8.3
 */

import * as fc from "fast-check";
import {
  getMaxWeight,
  detectPersonalRecord,
  detectPersonalRecords,
  updateHistoricalData,
  ExerciseData,
  TrainingLogData,
  HistoricalExerciseData,
  RecordType
} from "../utils/personalRecordDetector";

describe("Personal Record Detector", () => {
  describe("getMaxWeight", () => {
    it("should return the maximum weight from all sets", () => {
      const exercise: ExerciseData = {
        exerciseId: "bench_press",
        sets: [
          { reps: 10, weight: 60 },
          { reps: 8, weight: 70 },
          { reps: 6, weight: 80 }
        ]
      };

      const maxWeight = getMaxWeight(exercise);
      expect(maxWeight).toBe(80);
    });

    it("should return 0 for exercise with no sets", () => {
      const exercise: ExerciseData = {
        exerciseId: "empty",
        sets: []
      };

      const maxWeight = getMaxWeight(exercise);
      expect(maxWeight).toBe(0);
    });

    it("should handle single set", () => {
      const exercise: ExerciseData = {
        exerciseId: "deadlift",
        sets: [{ reps: 5, weight: 150 }]
      };

      const maxWeight = getMaxWeight(exercise);
      expect(maxWeight).toBe(150);
    });
  });

  describe("detectPersonalRecord", () => {
    it("should detect max weight record when weight exceeds historical max", () => {
      const exercise: ExerciseData = {
        exerciseId: "bench_press",
        sets: [
          { reps: 10, weight: 85 }
        ]
      };

      const historical: HistoricalExerciseData = {
        exerciseId: "bench_press",
        maxWeight: 80,
        maxVolume: 1000
      };

      const result = detectPersonalRecord(exercise, historical);

      expect(result.isPersonalRecord).toBe(true);
      expect(result.recordType).toBe(RecordType.MAX_WEIGHT);
      expect(result.newMaxWeight).toBe(85);
      expect(result.previousMaxWeight).toBe(80);
    });

    it("should detect max volume record when volume exceeds historical max", () => {
      const exercise: ExerciseData = {
        exerciseId: "squat",
        sets: [
          { reps: 10, weight: 100 },
          { reps: 10, weight: 100 },
          { reps: 10, weight: 100 }
        ]
      };

      const historical: HistoricalExerciseData = {
        exerciseId: "squat",
        maxWeight: 120,
        maxVolume: 2500
      };

      const result = detectPersonalRecord(exercise, historical);

      expect(result.isPersonalRecord).toBe(true);
      expect(result.recordType).toBe(RecordType.MAX_VOLUME);
      expect(result.newMaxVolume).toBe(3000);
      expect(result.previousMaxVolume).toBe(2500);
    });

    it("should detect both records when both weight and volume exceed historical max", () => {
      const exercise: ExerciseData = {
        exerciseId: "deadlift",
        sets: [
          { reps: 5, weight: 200 },
          { reps: 5, weight: 200 }
        ]
      };

      const historical: HistoricalExerciseData = {
        exerciseId: "deadlift",
        maxWeight: 180,
        maxVolume: 1500
      };

      const result = detectPersonalRecord(exercise, historical);

      expect(result.isPersonalRecord).toBe(true);
      expect(result.recordType).toBe(RecordType.BOTH);
      expect(result.newMaxWeight).toBe(200);
      expect(result.newMaxVolume).toBe(2000);
    });

    it("should not detect record when performance does not exceed historical max", () => {
      const exercise: ExerciseData = {
        exerciseId: "bench_press",
        sets: [
          { reps: 10, weight: 70 }
        ]
      };

      const historical: HistoricalExerciseData = {
        exerciseId: "bench_press",
        maxWeight: 80,
        maxVolume: 1000
      };

      const result = detectPersonalRecord(exercise, historical);

      expect(result.isPersonalRecord).toBe(false);
      expect(result.recordType).toBeUndefined();
      expect(result.newMaxWeight).toBeUndefined();
      expect(result.newMaxVolume).toBeUndefined();
    });
  });

  describe("detectPersonalRecords", () => {
    it("should detect records for multiple exercises", () => {
      const trainingLog: TrainingLogData = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 90 }]
          },
          {
            exerciseId: "squat",
            sets: [{ reps: 10, weight: 100 }]
          }
        ]
      };

      const historical = new Map<string, HistoricalExerciseData>([
        ["bench_press", { exerciseId: "bench_press", maxWeight: 85, maxVolume: 800 }],
        ["squat", { exerciseId: "squat", maxWeight: 110, maxVolume: 900 }]
      ]);

      const results = detectPersonalRecords(trainingLog, historical);

      expect(results).toHaveLength(2);
      expect(results[0].isPersonalRecord).toBe(true);
      expect(results[0].recordType).toBe(RecordType.BOTH);
      expect(results[1].isPersonalRecord).toBe(true);
      expect(results[1].recordType).toBe(RecordType.MAX_VOLUME);
    });

    it("should treat first-time exercises as personal records", () => {
      const trainingLog: TrainingLogData = {
        exercises: [
          {
            exerciseId: "new_exercise",
            sets: [{ reps: 10, weight: 50 }]
          }
        ]
      };

      const historical = new Map<string, HistoricalExerciseData>();

      const results = detectPersonalRecords(trainingLog, historical);

      expect(results).toHaveLength(1);
      expect(results[0].isPersonalRecord).toBe(true);
      expect(results[0].recordType).toBe(RecordType.BOTH);
      expect(results[0].newMaxWeight).toBe(50);
      expect(results[0].newMaxVolume).toBe(500);
      expect(results[0].previousMaxWeight).toBe(0);
      expect(results[0].previousMaxVolume).toBe(0);
    });
  });

  describe("updateHistoricalData", () => {
    it("should update historical data with new records", () => {
      const historical = new Map<string, HistoricalExerciseData>([
        ["bench_press", { exerciseId: "bench_press", maxWeight: 80, maxVolume: 1000 }]
      ]);

      const trainingLog: TrainingLogData = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 90 }]
          }
        ]
      };

      const updated = updateHistoricalData(historical, trainingLog);

      const benchData = updated.get("bench_press");
      expect(benchData?.maxWeight).toBe(90);
      expect(benchData?.maxVolume).toBe(1000);
    });

    it("should add new exercises to historical data", () => {
      const historical = new Map<string, HistoricalExerciseData>();

      const trainingLog: TrainingLogData = {
        exercises: [
          {
            exerciseId: "new_exercise",
            sets: [{ reps: 10, weight: 50 }]
          }
        ]
      };

      const updated = updateHistoricalData(historical, trainingLog);

      expect(updated.size).toBe(1);
      const newData = updated.get("new_exercise");
      expect(newData?.maxWeight).toBe(50);
      expect(newData?.maxVolume).toBe(500);
    });
  });
});

/**
 * Property-Based Tests for Personal Record Detector
 * 
 * Property 5: Personal Record Detection for Max Weight
 * Validates: Requirements 8.1, 8.2, 8.4
 * 
 * For any exercise in a TrainingLog, if the weight used exceeds all previous 
 * weights for that exercise in the user's history, the system SHALL mark it 
 * as a PersonalRecord with recordType MAX_WEIGHT.
 * 
 * Property 6: Personal Record Detection for Max Volume
 * Validates: Requirements 8.1, 8.3, 8.4
 * 
 * For any exercise in a TrainingLog, if the total volume exceeds all previous 
 * volumes for that exercise in the user's history, the system SHALL mark it 
 * as a PersonalRecord with recordType MAX_VOLUME.
 */
describe("Personal Record Detector - Property-Based Tests", () => {
  // Arbitrary generator for a single set
  const arbSet = fc.record({
    reps: fc.integer({ min: 1, max: 50 }),
    weight: fc.double({ min: 0.1, max: 500, noNaN: true }),
    restSeconds: fc.option(fc.integer({ min: 0, max: 300 }), { nil: undefined }),
    heartRate: fc.option(fc.integer({ min: 60, max: 200 }), { nil: undefined })
  });

  // Arbitrary generator for an exercise with sets
  const arbExercise = fc.record({
    exerciseId: fc.constantFrom("bench_press", "squat", "deadlift", "curl", "row"),
    sets: fc.array(arbSet, { minLength: 1, maxLength: 10 })
  });

  describe("Property 5: Max Weight Record Detection", () => {
    it("should detect max weight record when current weight exceeds historical max", () => {
      fc.assert(
        fc.property(
          arbExercise,
          fc.double({ min: 0.1, max: 400, noNaN: true }),
          fc.integer({ min: 100, max: 5000 }),
          (exercise, historicalMaxWeight, historicalMaxVolume) => {
            // Find the actual max weight in the current exercise
            const currentMaxWeight = Math.max(...exercise.sets.map(s => s.weight));

            // Ensure historical max weight is less than current max weight
            const adjustedHistoricalMaxWeight = Math.min(
              historicalMaxWeight,
              currentMaxWeight - 0.1
            );

            const historical: HistoricalExerciseData = {
              exerciseId: exercise.exerciseId,
              maxWeight: adjustedHistoricalMaxWeight,
              maxVolume: historicalMaxVolume
            };

            const result = detectPersonalRecord(exercise, historical);

            // Should detect a personal record
            expect(result.isPersonalRecord).toBe(true);
            
            // Should include MAX_WEIGHT in the record type
            expect(
              result.recordType === RecordType.MAX_WEIGHT ||
              result.recordType === RecordType.BOTH
            ).toBe(true);

            // New max weight should be greater than historical
            expect(result.newMaxWeight).toBeGreaterThan(adjustedHistoricalMaxWeight);
          }
        ),
        { numRuns: 500 }
      );
    });

    it("should not detect max weight record when current weight does not exceed historical max", () => {
      fc.assert(
        fc.property(
          arbExercise,
          fc.integer({ min: 100, max: 5000 }),
          (exercise, historicalMaxVolume) => {
            // Find the actual max weight in the current exercise
            const currentMaxWeight = Math.max(...exercise.sets.map(s => s.weight));

            // Set historical max weight higher than current
            const historicalMaxWeight = currentMaxWeight + 10;

            const historical: HistoricalExerciseData = {
              exerciseId: exercise.exerciseId,
              maxWeight: historicalMaxWeight,
              maxVolume: historicalMaxVolume
            };

            const result = detectPersonalRecord(exercise, historical);

            // If a record is detected, it should not be MAX_WEIGHT only
            if (result.isPersonalRecord) {
              expect(result.recordType).not.toBe(RecordType.MAX_WEIGHT);
            }

            // New max weight should not be set if no weight record
            if (result.recordType !== RecordType.MAX_WEIGHT && result.recordType !== RecordType.BOTH) {
              expect(result.newMaxWeight).toBeUndefined();
            }
          }
        ),
        { numRuns: 500 }
      );
    });
  });

  describe("Property 6: Max Volume Record Detection", () => {
    it("should detect max volume record when current volume exceeds historical max", () => {
      fc.assert(
        fc.property(
          arbExercise,
          fc.double({ min: 0.1, max: 400, noNaN: true }),
          (exercise, historicalMaxWeight) => {
            // Calculate current volume
            const currentVolume = exercise.sets.reduce(
              (sum, set) => sum + (set.weight * set.reps),
              0
            );

            // Set historical max volume less than current volume
            const historicalMaxVolume = Math.max(0, currentVolume - 100);

            const historical: HistoricalExerciseData = {
              exerciseId: exercise.exerciseId,
              maxWeight: historicalMaxWeight,
              maxVolume: historicalMaxVolume
            };

            const result = detectPersonalRecord(exercise, historical);

            // Should detect a personal record
            expect(result.isPersonalRecord).toBe(true);
            
            // Should include MAX_VOLUME in the record type
            expect(
              result.recordType === RecordType.MAX_VOLUME ||
              result.recordType === RecordType.BOTH
            ).toBe(true);

            // New max volume should be greater than historical
            expect(result.newMaxVolume).toBeGreaterThan(historicalMaxVolume);
          }
        ),
        { numRuns: 500 }
      );
    });

    it("should not detect max volume record when current volume does not exceed historical max", () => {
      fc.assert(
        fc.property(
          arbExercise,
          fc.double({ min: 0.1, max: 400, noNaN: true }),
          (exercise, historicalMaxWeight) => {
            // Calculate current volume
            const currentVolume = exercise.sets.reduce(
              (sum, set) => sum + (set.weight * set.reps),
              0
            );

            // Set historical max volume higher than current
            const historicalMaxVolume = currentVolume + 500;

            const historical: HistoricalExerciseData = {
              exerciseId: exercise.exerciseId,
              maxWeight: historicalMaxWeight,
              maxVolume: historicalMaxVolume
            };

            const result = detectPersonalRecord(exercise, historical);

            // If a record is detected, it should not be MAX_VOLUME only
            if (result.isPersonalRecord) {
              expect(result.recordType).not.toBe(RecordType.MAX_VOLUME);
            }

            // New max volume should not be set if no volume record
            if (result.recordType !== RecordType.MAX_VOLUME && result.recordType !== RecordType.BOTH) {
              expect(result.newMaxVolume).toBeUndefined();
            }
          }
        ),
        { numRuns: 500 }
      );
    });
  });

  describe("Property: First-time exercises are always personal records", () => {
    it("should mark any first-time exercise as a personal record", () => {
      fc.assert(
        fc.property(arbExercise, (exercise) => {
          const historical = new Map<string, HistoricalExerciseData>();
          const trainingLog: TrainingLogData = { exercises: [exercise] };

          const results = detectPersonalRecords(trainingLog, historical);

          expect(results).toHaveLength(1);
          expect(results[0].isPersonalRecord).toBe(true);
          expect(results[0].recordType).toBe(RecordType.BOTH);
          expect(results[0].previousMaxWeight).toBe(0);
          expect(results[0].previousMaxVolume).toBe(0);
        }),
        { numRuns: 500 }
      );
    });
  });

  describe("Property: Historical data updates preserve or increase records", () => {
    it("should never decrease max weight or max volume when updating historical data", () => {
      fc.assert(
        fc.property(
          fc.array(arbExercise, { minLength: 1, maxLength: 5 }),
          (exercises) => {
            let historical = new Map<string, HistoricalExerciseData>();

            // Process each exercise as a training log
            for (const exercise of exercises) {
              const trainingLog: TrainingLogData = { exercises: [exercise] };
              const previousData = historical.get(exercise.exerciseId);

              historical = updateHistoricalData(historical, trainingLog);

              const updatedData = historical.get(exercise.exerciseId);
              expect(updatedData).toBeDefined();

              if (previousData) {
                // Max weight should never decrease
                expect(updatedData!.maxWeight).toBeGreaterThanOrEqual(previousData.maxWeight);
                // Max volume should never decrease
                expect(updatedData!.maxVolume).toBeGreaterThanOrEqual(previousData.maxVolume);
              }
            }
          }
        ),
        { numRuns: 500 }
      );
    });
  });
});
