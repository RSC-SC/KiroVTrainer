# V-Trainer Database Architecture

## Room Database Structure

```
┌─────────────────────────────────────────────────────────────┐
│                    VTrainerDatabase                         │
│                   (RoomDatabase)                            │
│                                                             │
│  Version: 1                                                 │
│  Strategy: fallbackToDestructiveMigration                   │
│  Pattern: Singleton with lazy initialization                │
└─────────────────────────────────────────────────────────────┘
                            │
                            │ provides access to
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│WorkoutPlanDao│    │TrainingLogDao│    │ ExerciseDao  │
└──────┬───────┘    └──────┬───────┘    └──────┬───────┘
       │                   │                   │
       │ operates on       │ operates on       │ operates on
       │                   │                   │
       ▼                   ▼                   ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│WorkoutPlan   │    │TrainingLog   │    │  Exercise    │
│   Entity     │    │   Entity     │    │   Entity     │
└──────────────┘    └──────────────┘    └──────────────┘
```

## Entity Details

### WorkoutPlanEntity
**Table:** `workout_plans`

**Fields:**
- `planId` (PK) - Unique identifier
- `userId` - Owner of the plan
- `name` - Plan name
- `description` - Optional description
- `trainingDaysJson` - Serialized training days
- `createdAt` - Creation timestamp
- `updatedAt` - Last update timestamp
- `syncStatus` - SYNCED | PENDING_SYNC | SYNC_FAILED
- `lastSyncAttempt` - Last sync attempt timestamp

### TrainingLogEntity
**Table:** `training_logs`

**Fields:**
- `logId` (PK) - Unique identifier
- `userId` - Owner of the log
- `workoutPlanId` - Associated plan (nullable)
- `workoutDayName` - Name of workout day
- `timestamp` - When workout occurred
- `origin` - Device origin (mobile/watch)
- `exercisesJson` - Serialized exercise logs
- `totalVolume` - Total volume (weight × reps)
- `totalCalories` - Calories burned (nullable)
- `duration` - Workout duration in seconds
- `syncStatus` - SYNCED | PENDING_SYNC | SYNC_FAILED
- `lastSyncAttempt` - Last sync attempt timestamp

### ExerciseEntity
**Table:** `exercises`

**Fields:**
- `exerciseId` (PK) - Unique identifier
- `name` - Exercise name
- `muscleGroup` - Primary muscle group
- `secondaryMusclesJson` - Serialized secondary muscles
- `instructions` - How to perform
- `mediaUrl` - GIF/video URL
- `mediaType` - GIF or VIDEO
- `difficulty` - BEGINNER | INTERMEDIATE | ADVANCED
- `equipmentJson` - Serialized equipment list
- `cachedAt` - Cache timestamp for LRU eviction

## Sync Status Flow

```
┌─────────────┐
│   SYNCED    │ ◄─── Data successfully synchronized with Firestore
└──────┬──────┘
       │
       │ User makes changes offline
       │
       ▼
┌─────────────┐
│PENDING_SYNC │ ◄─── Waiting for network connectivity
└──────┬──────┘
       │
       │ Sync attempt fails
       │
       ▼
┌─────────────┐
│SYNC_FAILED  │ ◄─── Will retry with exponential backoff
└──────┬──────┘
       │
       │ Retry succeeds
       │
       ▼
┌─────────────┐
│   SYNCED    │
└─────────────┘
```

## Database Access Pattern

```
┌──────────────────┐
│  Application     │
│  (VTrainer       │
│   Application)   │
└────────┬─────────┘
         │
         │ lazy initialization
         │
         ▼
┌──────────────────┐
│ VTrainerDatabase │ ◄─── Singleton instance
│   .getInstance() │
└────────┬─────────┘
         │
         │ provides DAOs
         │
         ▼
┌──────────────────┐
│  Repository      │ ◄─── Uses DAOs for data operations
│  Layer           │
└──────────────────┘
```

## Migration Strategy

### Version 1 (Current)
- **Strategy:** `fallbackToDestructiveMigration()`
- **Rationale:** 
  - Initial version with no existing data
  - All data backed up in Firestore
  - Can be re-synced if database recreated
  - Simplifies initial development

### Future Versions
- **Strategy:** Implement proper migrations
- **Example:**
  ```kotlin
  val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(database: SupportSQLiteDatabase) {
          // Add new column
          database.execSQL(
              "ALTER TABLE workout_plans ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0"
          )
      }
  }
  ```

## Thread Safety

The database implementation ensures thread safety through:

1. **Volatile Instance Variable**
   ```kotlin
   @Volatile
   private var INSTANCE: VTrainerDatabase? = null
   ```

2. **Double-Checked Locking**
   ```kotlin
   return INSTANCE ?: synchronized(this) {
       INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
   }
   ```

3. **Coroutine-Safe DAOs**
   - All DAO methods are `suspend` functions
   - Flow-based queries for reactive updates
   - Room handles thread management internally

## Testing Strategy

### Unit Tests
- In-memory database for fast, isolated tests
- Verify database creation
- Verify DAO accessibility
- Verify singleton pattern
- Test on main thread (allowed for testing)

### Integration Tests
- Test with actual Android context
- Verify data persistence
- Test sync status transitions
- Verify query performance

## Performance Considerations

1. **Lazy Initialization**
   - Database created only when first accessed
   - Reduces app startup time

2. **Indexed Queries**
   - Primary keys automatically indexed
   - Consider adding indexes for frequently queried fields

3. **Batch Operations**
   - Use `insertAll()` for bulk inserts
   - More efficient than individual inserts

4. **Flow-Based Queries**
   - Reactive updates without polling
   - Efficient memory usage

## Cache Management

### Exercise Library Cache
- LRU eviction based on `cachedAt` timestamp
- Configurable cache size limit
- Never evict PENDING_SYNC items

### Sync Queue
- Pending items stored with sync status
- Background sync service processes queue
- Exponential backoff for failed syncs

## Security Considerations

1. **User Data Isolation**
   - All queries filter by `userId`
   - Prevents cross-user data access

2. **No Sensitive Data in Logs**
   - Database operations don't log user data
   - Only log errors and sync status

3. **Encrypted Storage** (Future)
   - Consider SQLCipher for encrypted database
   - Protect sensitive user information
