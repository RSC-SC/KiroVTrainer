/**
 * Property-Based Tests for Training Log Validation
 * 
 * Tests validation logic using fast-check for property-based testing
 * Feature: v-trainer
 */

import * as fc from "fast-check";
import { validateTrainingLog } from "../../validation/trainingLogValidator";

describe("Training Log Validation", () => {
  /**
   * Property 2: Training Log Validation Rejects Invalid Data
   * **Validates: Requirements 20.1, 20.2, 20.3, 20.4, 20.5, 20.6**
   * 
   * For any TrainingLog with invalid data (negative weights, non-positive reps,
   * negative rest times, or future timestamps beyond 24 hours), the validation
   * SHALL reject it and return a descriptive error message.
   */
  describe("Property 2: Training Log Validation Rejects Invalid Data", () => {
    it("should reject training logs with missing timestamp", () => {
      fc.assert(
        fc.property(
          fc.array(fc.record({
            exerciseId: fc.string({ minLength: 1 }),
            sets: fc.array(fc.record({
              reps: fc.integer({ min: 1 }),
              weight: fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
              restSeconds: fc.integer({ min: 0, max: 600 })
            }), { minLength: 1 })
          }), { minLength: 1 }),
          (exercises) => {
            const invalidLog = {
              // timestamp is missing
              exercises
            };
            
            const result = validateTrainingLog(invalidLog);
            
            return !result.isValid && 
                   result.errors.some(err => err.includes("timestamp is required"));
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should reject training logs with missing exercises array", () => {
      fc.assert(
        fc.property(
          fc.date().filter(d => !isNaN(d.getTime())),
          (date) => {
            const invalidLog = {
              timestamp: date.toISOString()
              // exercises is missing
            };
            
            const result = validateTrainingLog(invalidLog);
            
            return !result.isValid && 
                   result.errors.some(err => err.includes("exercises array is required"));
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should reject training logs with timestamps more than 24 hours in future", () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 25, max: 1000 }), // hours in future (more than 24)
          fc.array(fc.record({
            exerciseId: fc.string({ minLength: 1 }),
            sets: fc.array(fc.record({
              reps: fc.integer({ min: 1 }),
              weight: fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
              restSeconds: fc.integer({ min: 0, max: 600 })
            }), { minLength: 1 })
          }), { minLength: 1 }),
          (hoursInFuture, exercises) => {
            const futureDate = new Date();
            futureDate.setHours(futureDate.getHours() + hoursInFuture);
            
            const invalidLog = {
              timestamp: futureDate.toISOString(),
              exercises
            };
            
            const result = validateTrainingLog(invalidLog);
            
            return !result.isValid && 
                   result.errors.some(err => 
                     err.includes("timestamp cannot be more than 24 hours in the future")
                   );
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should reject training logs with negative or zero weights", () => {
      fc.assert(
        fc.property(
          fc.float({ min: Math.fround(-100), max: Math.fround(0), noNaN: true }), // negative or zero weight
          fc.integer({ min: 1, max: 20 }),
          fc.integer({ min: 0, max: 600 }),
          (invalidWeight, reps, restSeconds) => {
            const invalidLog = {
              timestamp: new Date().toISOString(),
              exercises: [{
                exerciseId: "test-exercise",
                sets: [{
                  reps,
                  weight: invalidWeight,
                  restSeconds
                }]
              }]
            };
            
            const result = validateTrainingLog(invalidLog);
            
            return !result.isValid && 
                   result.errors.some(err => err.includes("weight must be positive"));
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should reject training logs with non-positive reps", () => {
      fc.assert(
        fc.property(
          fc.integer({ min: -100, max: 0 }), // non-positive reps
          fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
          fc.integer({ min: 0, max: 600 }),
          (invalidReps, weight, restSeconds) => {
            const invalidLog = {
              timestamp: new Date().toISOString(),
              exercises: [{
                exerciseId: "test-exercise",
                sets: [{
                  reps: invalidReps,
                  weight,
                  restSeconds
                }]
              }]
            };
            
            const result = validateTrainingLog(invalidLog);
            
            return !result.isValid && 
                   result.errors.some(err => err.includes("reps must be positive integer"));
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should reject training logs with non-integer reps", () => {
      fc.assert(
        fc.property(
          fc.float({ min: Math.fround(0.1), max: Math.fround(20) }).filter(n => !Number.isInteger(n)),
          fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
          fc.integer({ min: 0, max: 600 }),
          (invalidReps, weight, restSeconds) => {
            const invalidLog = {
              timestamp: new Date().toISOString(),
              exercises: [{
                exerciseId: "test-exercise",
                sets: [{
                  reps: invalidReps,
                  weight,
                  restSeconds
                }]
              }]
            };
            
            const result = validateTrainingLog(invalidLog);
            
            return !result.isValid && 
                   result.errors.some(err => err.includes("reps must be positive integer"));
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should reject training logs with negative rest times", () => {
      fc.assert(
        fc.property(
          fc.integer({ min: -600, max: -1 }), // negative rest time
          fc.integer({ min: 1, max: 20 }),
          fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
          (invalidRestSeconds, reps, weight) => {
            const invalidLog = {
              timestamp: new Date().toISOString(),
              exercises: [{
                exerciseId: "test-exercise",
                sets: [{
                  reps,
                  weight,
                  restSeconds: invalidRestSeconds
                }]
              }]
            };
            
            const result = validateTrainingLog(invalidLog);
            
            return !result.isValid && 
                   result.errors.some(err => 
                     err.includes("restSeconds must be non-negative integer")
                   );
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should reject training logs with non-integer rest times", () => {
      fc.assert(
        fc.property(
          fc.float({ min: Math.fround(0), max: Math.fround(600) }).filter(n => !Number.isInteger(n)),
          fc.integer({ min: 1, max: 20 }),
          fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
          (invalidRestSeconds, reps, weight) => {
            const invalidLog = {
              timestamp: new Date().toISOString(),
              exercises: [{
                exerciseId: "test-exercise",
                sets: [{
                  reps,
                  weight,
                  restSeconds: invalidRestSeconds
                }]
              }]
            };
            
            const result = validateTrainingLog(invalidLog);
            
            return !result.isValid && 
                   result.errors.some(err => 
                     err.includes("restSeconds must be non-negative integer")
                   );
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should reject training logs with missing exerciseId", () => {
      fc.assert(
        fc.property(
          fc.array(fc.record({
            reps: fc.integer({ min: 1 }),
            weight: fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
            restSeconds: fc.integer({ min: 0, max: 600 })
          }), { minLength: 1 }),
          (sets) => {
            const invalidLog = {
              timestamp: new Date().toISOString(),
              exercises: [{
                // exerciseId is missing
                sets
              }]
            };
            
            const result = validateTrainingLog(invalidLog);
            
            return !result.isValid && 
                   result.errors.some(err => err.includes("exerciseId is required"));
          }
        ),
        { numRuns: 100 }
      );
    });
  });

  /**
   * Property 3: Training Log Validation Accepts Valid Data
   * **Validates: Requirements 6.3, 6.4, 20.1**
   * 
   * For any TrainingLog with valid data (positive weights, positive integer reps,
   * non-negative rest times, and reasonable timestamps), the validation SHALL
   * accept it and save it to Firestore.
   */
  describe("Property 3: Training Log Validation Accepts Valid Data", () => {
    it("should accept valid training logs with all required fields", () => {
      fc.assert(
        fc.property(
          // Generate valid timestamp (within last 24 hours or up to 24 hours in future)
          fc.integer({ min: -24, max: 24 }).map(hours => {
            const date = new Date();
            date.setHours(date.getHours() + hours);
            return date.toISOString();
          }),
          // Generate valid exercises
          fc.array(
            fc.record({
              exerciseId: fc.string({ minLength: 1, maxLength: 50 }),
              sets: fc.array(
                fc.record({
                  reps: fc.integer({ min: 1, max: 100 }),
                  weight: fc.float({ min: Math.fround(0.1), max: Math.fround(500), noNaN: true }),
                  restSeconds: fc.integer({ min: 0, max: 600 }),
                  heartRate: fc.option(fc.integer({ min: 40, max: 220 }), { nil: undefined })
                }),
                { minLength: 1, maxLength: 10 }
              )
            }),
            { minLength: 1, maxLength: 20 }
          ),
          (timestamp, exercises) => {
            const validLog = {
              timestamp,
              exercises,
              origin: "Galaxy_Watch_4",
              workoutDayName: "Treino A",
              duration: 3600
            };
            
            const result = validateTrainingLog(validLog);
            
            return result.isValid && result.errors.length === 0;
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should accept training logs with zero rest time (valid)", () => {
      fc.assert(
        fc.property(
          fc.integer({ min: 1, max: 20 }),
          fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
          (reps, weight) => {
            const validLog = {
              timestamp: new Date().toISOString(),
              exercises: [{
                exerciseId: "test-exercise",
                sets: [{
                  reps,
                  weight,
                  restSeconds: 0 // Zero is valid (non-negative)
                }]
              }]
            };
            
            const result = validateTrainingLog(validLog);
            
            return result.isValid && result.errors.length === 0;
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should accept training logs with timestamps exactly 24 hours in future", () => {
      fc.assert(
        fc.property(
          fc.array(fc.record({
            exerciseId: fc.string({ minLength: 1 }),
            sets: fc.array(fc.record({
              reps: fc.integer({ min: 1 }),
              weight: fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
              restSeconds: fc.integer({ min: 0, max: 600 })
            }), { minLength: 1 })
          }), { minLength: 1 }),
          (exercises) => {
            const futureDate = new Date();
            futureDate.setHours(futureDate.getHours() + 24);
            
            const validLog = {
              timestamp: futureDate.toISOString(),
              exercises
            };
            
            const result = validateTrainingLog(validLog);
            
            return result.isValid && result.errors.length === 0;
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should accept training logs with optional fields", () => {
      fc.assert(
        fc.property(
          fc.array(fc.record({
            exerciseId: fc.string({ minLength: 1 }),
            sets: fc.array(fc.record({
              reps: fc.integer({ min: 1 }),
              weight: fc.float({ min: Math.fround(0.1), max: Math.fround(500) }),
              restSeconds: fc.integer({ min: 0, max: 600 }),
              heartRate: fc.integer({ min: 40, max: 220 })
            }), { minLength: 1 })
          }), { minLength: 1 }),
          fc.string({ minLength: 1 }),
          fc.string({ minLength: 1 }),
          fc.integer({ min: 1, max: 10000 }),
          fc.integer({ min: 1, max: 2000 }),
          (exercises, origin, workoutDayName, duration, totalCalories) => {
            const validLog = {
              timestamp: new Date().toISOString(),
              exercises,
              origin,
              workoutDayName,
              duration,
              totalCalories
            };
            
            const result = validateTrainingLog(validLog);
            
            return result.isValid && result.errors.length === 0;
          }
        ),
        { numRuns: 100 }
      );
    });

    it("should accept training logs with multiple exercises and sets", () => {
      fc.assert(
        fc.property(
          fc.array(
            fc.record({
              exerciseId: fc.string({ minLength: 1, maxLength: 50 }),
              sets: fc.array(
                fc.record({
                  reps: fc.integer({ min: 1, max: 50 }),
                  weight: fc.float({ min: Math.fround(0.1), max: Math.fround(300) }),
                  restSeconds: fc.integer({ min: 0, max: 300 })
                }),
                { minLength: 1, maxLength: 5 }
              )
            }),
            { minLength: 1, maxLength: 10 }
          ),
          (exercises) => {
            const validLog = {
              timestamp: new Date().toISOString(),
              exercises
            };
            
            const result = validateTrainingLog(validLog);
            
            return result.isValid && result.errors.length === 0;
          }
        ),
        { numRuns: 100 }
      );
    });
  });
});
