/**
 * Unit tests for Volume Calculator Utility
 * 
 * Tests the volume calculation functions according to requirement 7.3
 */

import * as fc from "fast-check";
import {
  calculateExerciseVolume,
  calculateTotalVolume,
  calculateExerciseVolumes,
  ExerciseData,
  TrainingLogData
} from "../utils/volumeCalculator";

describe("Volume Calculator", () => {
  describe("calculateExerciseVolume", () => {
    it("should calculate volume for a single exercise with one set", () => {
      const exercise: ExerciseData = {
        exerciseId: "bench_press",
        sets: [
          { reps: 10, weight: 60 }
        ]
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(600); // 10 * 60
    });
    
    it("should calculate volume for an exercise with multiple sets", () => {
      const exercise: ExerciseData = {
        exerciseId: "squat",
        sets: [
          { reps: 10, weight: 100 },
          { reps: 8, weight: 110 },
          { reps: 6, weight: 120 }
        ]
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(2600); // (10*100) + (8*110) + (6*120) = 1000 + 880 + 720
    });
    
    it("should handle decimal weights correctly", () => {
      const exercise: ExerciseData = {
        exerciseId: "dumbbell_curl",
        sets: [
          { reps: 12, weight: 12.5 },
          { reps: 10, weight: 15.0 }
        ]
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(300); // (12*12.5) + (10*15) = 150 + 150
    });
    
    it("should return 0 for exercise with no sets", () => {
      const exercise: ExerciseData = {
        exerciseId: "empty_exercise",
        sets: []
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(0);
    });
    
    it("should return 0 for exercise with undefined sets", () => {
      const exercise: any = {
        exerciseId: "no_sets_exercise"
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(0);
    });
    
    it("should handle sets with additional fields (heartRate, restSeconds)", () => {
      const exercise: ExerciseData = {
        exerciseId: "bench_press",
        sets: [
          { reps: 10, weight: 80, restSeconds: 60, heartRate: 145 },
          { reps: 8, weight: 85, restSeconds: 60, heartRate: 150 }
        ]
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(1480); // (10*80) + (8*85) = 800 + 680
    });
  });
  
  describe("calculateTotalVolume", () => {
    it("should calculate total volume for a training log with one exercise", () => {
      const trainingLog: TrainingLogData = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [
              { reps: 10, weight: 60 },
              { reps: 8, weight: 65 }
            ]
          }
        ]
      };
      
      const volume = calculateTotalVolume(trainingLog);
      expect(volume).toBe(1120); // (10*60) + (8*65) = 600 + 520
    });
    
    it("should calculate total volume for a training log with multiple exercises", () => {
      const trainingLog: TrainingLogData = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [
              { reps: 10, weight: 60 },
              { reps: 8, weight: 65 }
            ]
          },
          {
            exerciseId: "squat",
            sets: [
              { reps: 10, weight: 100 },
              { reps: 8, weight: 110 }
            ]
          },
          {
            exerciseId: "deadlift",
            sets: [
              { reps: 5, weight: 140 }
            ]
          }
        ]
      };
      
      const volume = calculateTotalVolume(trainingLog);
      // bench: (10*60) + (8*65) = 1120
      // squat: (10*100) + (8*110) = 1880
      // deadlift: (5*140) = 700
      // total: 3700
      expect(volume).toBe(3700);
    });
    
    it("should return 0 for training log with no exercises", () => {
      const trainingLog: TrainingLogData = {
        exercises: []
      };
      
      const volume = calculateTotalVolume(trainingLog);
      expect(volume).toBe(0);
    });
    
    it("should return 0 for training log with undefined exercises", () => {
      const trainingLog: any = {};
      
      const volume = calculateTotalVolume(trainingLog);
      expect(volume).toBe(0);
    });
    
    it("should handle exercises with zero volume", () => {
      const trainingLog: TrainingLogData = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [
              { reps: 10, weight: 60 }
            ]
          },
          {
            exerciseId: "empty_exercise",
            sets: []
          },
          {
            exerciseId: "squat",
            sets: [
              { reps: 8, weight: 100 }
            ]
          }
        ]
      };
      
      const volume = calculateTotalVolume(trainingLog);
      expect(volume).toBe(1400); // (10*60) + 0 + (8*100) = 600 + 0 + 800
    });
  });
  
  describe("calculateExerciseVolumes", () => {
    it("should return volume for each exercise", () => {
      const trainingLog: TrainingLogData = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [
              { reps: 10, weight: 60 },
              { reps: 8, weight: 65 }
            ]
          },
          {
            exerciseId: "squat",
            sets: [
              { reps: 10, weight: 100 }
            ]
          }
        ]
      };
      
      const volumes = calculateExerciseVolumes(trainingLog);
      
      expect(volumes).toHaveLength(2);
      expect(volumes[0]).toEqual({
        exerciseId: "bench_press",
        totalVolume: 1120 // (10*60) + (8*65)
      });
      expect(volumes[1]).toEqual({
        exerciseId: "squat",
        totalVolume: 1000 // (10*100)
      });
    });
    
    it("should return empty array for training log with no exercises", () => {
      const trainingLog: TrainingLogData = {
        exercises: []
      };
      
      const volumes = calculateExerciseVolumes(trainingLog);
      expect(volumes).toEqual([]);
    });
    
    it("should include exercises with zero volume", () => {
      const trainingLog: TrainingLogData = {
        exercises: [
          {
            exerciseId: "bench_press",
            sets: [{ reps: 10, weight: 60 }]
          },
          {
            exerciseId: "empty_exercise",
            sets: []
          }
        ]
      };
      
      const volumes = calculateExerciseVolumes(trainingLog);
      
      expect(volumes).toHaveLength(2);
      expect(volumes[0].totalVolume).toBe(600);
      expect(volumes[1].totalVolume).toBe(0);
    });
  });
  
  describe("Edge cases", () => {
    it("should handle zero reps", () => {
      const exercise: ExerciseData = {
        exerciseId: "test",
        sets: [
          { reps: 0, weight: 100 }
        ]
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(0);
    });
    
    it("should handle zero weight", () => {
      const exercise: ExerciseData = {
        exerciseId: "bodyweight",
        sets: [
          { reps: 20, weight: 0 }
        ]
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(0);
    });
    
    it("should handle very large numbers", () => {
      const exercise: ExerciseData = {
        exerciseId: "heavy_lift",
        sets: [
          { reps: 1, weight: 500 },
          { reps: 1, weight: 500 }
        ]
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(1000);
    });
    
    it("should handle invalid weight values gracefully", () => {
      const exercise: any = {
        exerciseId: "test",
        sets: [
          { reps: 10, weight: "invalid" },
          { reps: 10, weight: null },
          { reps: 10, weight: undefined }
        ]
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(0); // All invalid weights treated as 0
    });
    
    it("should handle invalid reps values gracefully", () => {
      const exercise: any = {
        exerciseId: "test",
        sets: [
          { reps: "invalid", weight: 100 },
          { reps: null, weight: 100 },
          { reps: undefined, weight: 100 }
        ]
      };
      
      const volume = calculateExerciseVolume(exercise);
      expect(volume).toBe(0); // All invalid reps treated as 0
    });
  });
});

/**
 * Property-Based Tests for Volume Calculator
 * 
 * Property 4: Volume Calculation Correctness
 * Validates: Requirements 7.3
 * 
 * For any TrainingLog, the calculated total volume SHALL equal 
 * the sum of (weight × reps) across all sets in all exercises.
 */
describe("Volume Calculator - Property-Based Tests", () => {
  // Arbitrary generator for a single set
  const arbSet = fc.record({
    reps: fc.integer({ min: 1, max: 50 }),
    weight: fc.double({ min: 0.1, max: 500, noNaN: true }),
    restSeconds: fc.option(fc.integer({ min: 0, max: 300 }), { nil: undefined }),
    heartRate: fc.option(fc.integer({ min: 60, max: 200 }), { nil: undefined })
  });

  // Arbitrary generator for an exercise with sets
  const arbExercise = fc.record({
    exerciseId: fc.string({ minLength: 1, maxLength: 50 }),
    sets: fc.array(arbSet, { minLength: 1, maxLength: 10 })
  });

  // Arbitrary generator for a training log with exercises
  const arbTrainingLog = fc.record({
    exercises: fc.array(arbExercise, { minLength: 1, maxLength: 15 })
  });

  describe("Property 4: Volume Calculation Correctness", () => {
    it("should calculate total volume equal to sum of (weight × reps) for all sets", () => {
      fc.assert(
        fc.property(arbTrainingLog, (trainingLog) => {
          // Calculate expected volume manually
          let expectedVolume = 0;
          for (const exercise of trainingLog.exercises) {
            for (const set of exercise.sets) {
              expectedVolume += set.weight * set.reps;
            }
          }

          // Calculate volume using the function
          const actualVolume = calculateTotalVolume(trainingLog);

          // Allow small floating-point precision differences
          const tolerance = 0.01;
          expect(Math.abs(actualVolume - expectedVolume)).toBeLessThan(tolerance);
        }),
        { numRuns: 1000 }
      );
    });

    it("should calculate exercise volume equal to sum of (weight × reps) for its sets", () => {
      fc.assert(
        fc.property(arbExercise, (exercise) => {
          // Calculate expected volume manually
          let expectedVolume = 0;
          for (const set of exercise.sets) {
            expectedVolume += set.weight * set.reps;
          }

          // Calculate volume using the function
          const actualVolume = calculateExerciseVolume(exercise);

          // Allow small floating-point precision differences
          const tolerance = 0.01;
          expect(Math.abs(actualVolume - expectedVolume)).toBeLessThan(tolerance);
        }),
        { numRuns: 1000 }
      );
    });

    it("should have total volume equal to sum of individual exercise volumes", () => {
      fc.assert(
        fc.property(arbTrainingLog, (trainingLog) => {
          // Calculate sum of individual exercise volumes
          const exerciseVolumes = calculateExerciseVolumes(trainingLog);
          const sumOfExerciseVolumes = exerciseVolumes.reduce(
            (sum, ev) => sum + ev.totalVolume,
            0
          );

          // Calculate total volume
          const totalVolume = calculateTotalVolume(trainingLog);

          // They should be equal
          const tolerance = 0.01;
          expect(Math.abs(totalVolume - sumOfExerciseVolumes)).toBeLessThan(tolerance);
        }),
        { numRuns: 1000 }
      );
    });

    it("should return non-negative volume for any valid training log", () => {
      fc.assert(
        fc.property(arbTrainingLog, (trainingLog) => {
          const volume = calculateTotalVolume(trainingLog);
          expect(volume).toBeGreaterThanOrEqual(0);
        }),
        { numRuns: 1000 }
      );
    });

    it("should return zero volume for training log with empty exercises array", () => {
      const emptyLog: TrainingLogData = { exercises: [] };
      const volume = calculateTotalVolume(emptyLog);
      expect(volume).toBe(0);
    });

    it("should handle training logs with varying number of exercises and sets", () => {
      fc.assert(
        fc.property(
          fc.array(arbExercise, { minLength: 0, maxLength: 20 }),
          (exercises) => {
            const trainingLog: TrainingLogData = { exercises };
            
            // Calculate expected volume
            let expectedVolume = 0;
            for (const exercise of exercises) {
              for (const set of exercise.sets) {
                expectedVolume += set.weight * set.reps;
              }
            }

            const actualVolume = calculateTotalVolume(trainingLog);
            
            const tolerance = 0.01;
            expect(Math.abs(actualVolume - expectedVolume)).toBeLessThan(tolerance);
          }
        ),
        { numRuns: 1000 }
      );
    });
  });
});
