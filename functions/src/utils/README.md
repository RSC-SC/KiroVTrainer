# Volume Calculator Utility

## Overview

The volume calculator utility provides functions to calculate training volume according to requirement 7.3 of the V-Trainer specification.

**Volume Formula:** Total volume = sum of (weight × reps) for all sets in all exercises

## Usage

### Import

```typescript
import {
  calculateExerciseVolume,
  calculateTotalVolume,
  calculateExerciseVolumes
} from "./utils/volumeCalculator";
```

### Calculate Volume for a Single Exercise

```typescript
const exercise = {
  exerciseId: "bench_press",
  sets: [
    { reps: 10, weight: 60 },
    { reps: 8, weight: 65 },
    { reps: 6, weight: 70 }
  ]
};

const volume = calculateExerciseVolume(exercise);
// Result: 1470 (10*60 + 8*65 + 6*70)
```

### Calculate Total Volume for a Training Log

```typescript
const trainingLog = {
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
    }
  ]
};

const totalVolume = calculateTotalVolume(trainingLog);
// Result: 3000 (bench: 1120 + squat: 1880)
```

### Calculate Volume for Each Exercise

```typescript
const trainingLog = {
  exercises: [
    {
      exerciseId: "bench_press",
      sets: [{ reps: 10, weight: 60 }]
    },
    {
      exerciseId: "squat",
      sets: [{ reps: 10, weight: 100 }]
    }
  ]
};

const exerciseVolumes = calculateExerciseVolumes(trainingLog);
// Result: [
//   { exerciseId: "bench_press", totalVolume: 600 },
//   { exerciseId: "squat", totalVolume: 1000 }
// ]
```

## Integration with Cloud Functions

### Example: Using in calculateProgress Function

```typescript
import { calculateTotalVolume, calculateExerciseVolumes } from "./utils/volumeCalculator";

export const calculateProgress = onDocumentCreated("training_logs/{logId}", async (event) => {
  const snapshot = event.data;
  if (!snapshot) return;

  const workoutData = snapshot.data();
  
  // Calculate total volume using the utility
  const totalVolume = calculateTotalVolume(workoutData);
  
  // Calculate volume per exercise
  const exerciseVolumes = calculateExerciseVolumes(workoutData);
  
  // Update training log with calculated volumes
  await snapshot.ref.update({
    totalVolume: totalVolume,
    exercises: workoutData.exercises.map((exercise: any, index: number) => ({
      ...exercise,
      totalVolume: exerciseVolumes[index].totalVolume
    }))
  });
  
  // ... rest of the function
});
```

## API Reference

### `calculateExerciseVolume(exercise: ExerciseData): number`

Calculates the total volume for a single exercise.

**Parameters:**
- `exercise`: Exercise data containing sets with reps and weight

**Returns:** Total volume for the exercise (sum of weight × reps for all sets)

**Example:**
```typescript
const volume = calculateExerciseVolume({
  exerciseId: "bench_press",
  sets: [
    { reps: 10, weight: 60 },
    { reps: 8, weight: 65 }
  ]
});
// Returns: 1120
```

### `calculateTotalVolume(trainingLog: TrainingLogData): number`

Calculates the total volume for an entire training log.

**Parameters:**
- `trainingLog`: Training log data containing exercises

**Returns:** Total volume for the entire training session

**Example:**
```typescript
const totalVolume = calculateTotalVolume({
  exercises: [
    {
      exerciseId: "bench_press",
      sets: [{ reps: 10, weight: 60 }]
    },
    {
      exerciseId: "squat",
      sets: [{ reps: 10, weight: 100 }]
    }
  ]
});
// Returns: 1600
```

### `calculateExerciseVolumes(trainingLog: TrainingLogData): Array<{ exerciseId: string; totalVolume: number }>`

Calculates volume for each exercise and returns an array with exercise volumes.

**Parameters:**
- `trainingLog`: Training log data containing exercises

**Returns:** Array of objects with exerciseId and totalVolume

**Example:**
```typescript
const volumes = calculateExerciseVolumes({
  exercises: [
    {
      exerciseId: "bench_press",
      sets: [{ reps: 10, weight: 60 }]
    }
  ]
});
// Returns: [{ exerciseId: "bench_press", totalVolume: 600 }]
```

## Error Handling

The volume calculator handles edge cases gracefully:

- **Empty sets:** Returns 0
- **Undefined/null values:** Treats as 0
- **Invalid numbers:** Converts to 0 using `Number()` coercion
- **Missing fields:** Returns 0 for missing exercises or sets arrays

## Testing

The volume calculator has comprehensive unit tests covering:
- Single and multiple set calculations
- Decimal weights
- Edge cases (zero values, invalid data)
- Multiple exercises
- Empty data structures

Run tests with:
```bash
npm test -- volumeCalculator.test.ts
```

## Requirements

This utility implements requirement 7.3 from the V-Trainer specification:
- **Requirement 7.3:** Calculate and display total Volume for each training session

## Related Files

- Implementation: `functions/src/utils/volumeCalculator.ts`
- Tests: `functions/src/test/volumeCalculator.test.ts`
- Usage: `functions/src/calculateProgress.ts`
