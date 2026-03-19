# Implementation Plan: V-Trainer

## Overview

Este plano de implementação organiza o desenvolvimento do V-Trainer em fases incrementais, começando pela fundação (configuração, modelos de dados, backend) e progredindo para features core (execução de treinos, sincronização) e features avançadas (auto-detect, tiles, notificações). Cada tarefa referencia requisitos específicos e inclui sub-tarefas opcionais para testes de propriedade e testes unitários.

**Stack Tecnológica:**
- Mobile App: Kotlin + Jetpack Compose + Room
- Watch App: Kotlin + Compose for Wear OS + Health Services API
- Backend: Firebase Cloud Functions (TypeScript/Node.js)
- Database: Firestore + Room (cache local)

**Estratégia de Testes:**
- 27 Propriedades de Corretude implementadas como property-based tests
- Testes unitários para casos específicos e edge cases
- Testes marcados com "*" são opcionais e podem ser pulados para MVP mais rápido

## Tasks

### Phase 1: Project Setup and Foundation

- [x] 1. Configure Firebase project and Android projects
  - Create Firebase project with Firestore, Authentication, Cloud Functions, and Cloud Messaging
  - Set up Android mobile app project with Kotlin and Jetpack Compose
  - Set up Wear OS app project with Compose for Wear OS
  - Configure Firebase SDK in both Android projects
  - Set up Cloud Functions project with TypeScript
  - Configure build.gradle files with all necessary dependencies
  - _Requirements: 1.5, 6.2, 17.1_


- [x] 2. Set up Room database schema for local caching
  - [x] 2.1 Create Room entities (WorkoutPlanEntity, TrainingLogEntity, ExerciseEntity)
    - Define entity classes with proper annotations
    - Include syncStatus field for offline-first strategy
    - _Requirements: 12.1, 12.2_
  
  - [x] 2.2 Create DAO interfaces for database operations
    - Define queries for CRUD operations
    - Add queries for sync status filtering
    - _Requirements: 12.1_
  
  - [x] 2.3 Create Room database class and migration strategy
    - Configure database builder with fallback strategies
    - _Requirements: 12.1_

- [x] 3. Define core domain models
  - [x] 3.1 Create domain models (WorkoutPlan, TrainingLog, Exercise, PersonalRecord)
    - Implement data classes with proper validation
    - Include all fields from design document
    - _Requirements: 1.1, 2.1, 3.1, 7.1, 8.4_
  
  - [x] 3.2 Create enum classes (MuscleGroup, MediaType, Difficulty, Equipment, SyncStatus, RecordType)
    - Define all enum values from design
    - _Requirements: 2.1_
  
  - [x] 3.3 Create mapper functions between domain models and Room entities
    - Implement bidirectional mapping with JSON serialization for nested objects
    - _Requirements: 12.1_
  
  - [x] 3.4 Write property test for WorkoutPlan serialization
    - **Property 1: Workout Plan Round-Trip Serialization**
    - **Validates: Requirements 19.4**
    - Test that serializing to JSON then deserializing produces equivalent object
    - Use Kotest Property Testing with custom Arb generators


### Phase 2: Backend Implementation (Cloud Functions)

- [x] 4. Implement training log validation logic
  - [x] 4.1 Create validation functions for TrainingLog data
    - Validate required fields, positive weights, positive integer reps, non-negative rest times
    - Validate timestamps (not more than 24 hours in future)
    - Return descriptive error messages for each validation failure
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5, 20.6_
  
  - [x] 4.2 Write property test for validation rejecting invalid data
    - **Property 2: Training Log Validation Rejects Invalid Data**
    - **Validates: Requirements 20.1, 20.2, 20.3, 20.4, 20.5, 20.6**
    - Use fast-check to generate invalid TrainingLogs
  
  - [x] 4.3 Write property test for validation accepting valid data
    - **Property 3: Training Log Validation Accepts Valid Data**
    - **Validates: Requirements 6.3, 6.4, 20.1**
    - Use fast-check to generate valid TrainingLogs

- [ ] 5. Implement syncWorkout Cloud Function
  - [x] 5.1 Create HTTPS callable function with authentication
    - Validate Firebase Auth token from context
    - Parse and validate request data
    - Save TrainingLog to Firestore with server timestamp
    - Return success or error response
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 17.2_
  
  - [x] 5.2 Write unit tests for syncWorkout function
    - Test authentication validation
    - Test successful save to Firestore
    - Test error responses for invalid data
    - _Requirements: 6.2, 6.3_


- [x] 6. Implement volume calculation and personal record detection
  - [x] 6.1 Create volume calculator utility
    - Calculate total volume as sum of (weight × reps) for all sets
    - _Requirements: 7.3_
  
  - [x] 6.2 Write property test for volume calculation correctness
    - **Property 4: Volume Calculation Correctness**
    - **Validates: Requirements 7.3**
    - Generate random TrainingLogs and verify calculated volume equals expected sum
  
  - [x] 6.3 Create personal record detector utility
    - Compare exercise performance against user's history
    - Detect max weight records
    - Detect max volume records
    - _Requirements: 8.1, 8.2, 8.3_
  
  - [x] 6.4 Write property test for max weight record detection
    - **Property 5: Personal Record Detection for Max Weight**
    - **Validates: Requirements 8.1, 8.2, 8.4**
  
  - [x] 6.5 Write property test for max volume record detection
    - **Property 6: Personal Record Detection for Max Volume**
    - **Validates: Requirements 8.1, 8.3, 8.4**

- [x] 7. Implement calculateProgress Cloud Function
  - [x] 7.1 Create Firestore onCreate trigger for training_logs collection
    - Calculate volume using volume calculator
    - Detect personal records using record detector
    - Update user document with new records
    - Send push notification if record detected
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_
  
  - [x] 7.2 Write unit tests for calculateProgress function
    - Test volume calculation integration
    - Test record detection integration
    - Test Firestore updates
    - _Requirements: 8.1, 8.4_


- [x] 8. Implement sendWorkoutReminder Cloud Function
  - [x] 8.1 Create scheduled function for workout reminders
    - Query users with reminderEnabled = true
    - Check reminder time configuration
    - Send FCM notifications to mobile and watch
    - Include quick action to start workout
    - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5_
  
  - [x] 8.2 Write unit tests for reminder function
    - Test user query logic
    - Test FCM notification payload
    - _Requirements: 16.2, 16.4_

- [x] 9. Configure Firestore security rules
  - Create security rules file with authentication checks
  - Implement user data isolation (userId matches auth.uid)
  - Set read-only access for exercises collection
  - Prevent deletion of user documents
  - Make training logs immutable after creation
  - _Requirements: 17.1, 17.2, 17.3, 17.4_

- [x] 10. Checkpoint - Deploy and test backend
  - Deploy Cloud Functions to Firebase
  - Test syncWorkout function with Postman or curl
  - Verify Firestore security rules with Firebase Emulator
  - Ensure all tests pass, ask the user if questions arise


### Phase 3: Repository Layer and Data Synchronization

- [ ] 11. Implement WorkoutRepository with offline-first strategy
  - [x] 11.1 Create WorkoutRepository interface and implementation
    - Implement getWorkoutPlans() with Flow from Room and Firestore listener
    - Implement saveWorkoutPlan() with local-first save then Firestore sync
    - Implement deleteWorkoutPlan() with local delete then Firestore sync
    - Handle sync failures with PENDING_SYNC status
    - _Requirements: 3.4, 3.6, 12.1, 12.3_
  
  - [x] 11.2 Write property test for workout plan creation preserving exercise count
    - **Property 11: Workout Plan Creation Preserves Exercise Count**
    - **Validates: Requirements 3.1, 3.2, 3.4**
  
  - [x] 11.3 Write property test for workout plan configuration persistence
    - **Property 12: Workout Plan Configuration Persistence**
    - **Validates: Requirements 3.3, 3.4**
  
  - [x] 11.4 Write property test for workout plan deletion
    - **Property 25: Workout Plan Deletion Removes from User's List**
    - **Validates: Requirements 3.6**
  
  - [x] 11.5 Write unit tests for offline-first behavior
    - Test save when Firestore unavailable
    - Test sync retry logic
    - _Requirements: 12.3, 12.5_


- [ ] 12. Implement TrainingLogRepository with sync management
  - [x] 12.1 Create TrainingLogRepository interface and implementation
    - Implement saveTrainingLog() with local save and background sync
    - Implement getTrainingHistory() with Flow from Room
    - Implement syncPendingLogs() with exponential backoff retry
    - _Requirements: 4.6, 5.6, 6.5, 12.3, 12.5_
  
  - [x] 12.2 Write property test for offline cache synchronization
    - **Property 15: Offline Cache Synchronization Completeness**
    - **Validates: Requirements 6.5, 12.5**
  
  - [x] 12.3 Write property test for conflict resolution by timestamp
    - **Property 16: Conflict Resolution by Timestamp**
    - **Validates: Requirements 12.6**
  
  - [x] 12.4 Write unit tests for sync retry logic
    - Test exponential backoff
    - Test max retry attempts
    - _Requirements: 6.5_

- [ ] 13. Implement ExerciseRepository with caching
  - [x] 13.1 Create ExerciseRepository interface and implementation
    - Implement getExercises() with Room cache and Firestore fallback
    - Implement searchExercises() with local search
    - Implement filterByMuscleGroup() with local filtering
    - Cache exercise data for offline access
    - _Requirements: 2.1, 2.2, 2.5, 12.1_
  
  - [x] 13.2 Write property test for exercise data completeness
    - **Property 9: Exercise Library Data Completeness**
    - **Validates: Requirements 2.1, 2.4**
  
  - [x] 13.3 Write property test for muscle group filter correctness
    - **Property 10: Exercise Filter by Muscle Group Correctness**
    - **Validates: Requirements 2.2**
  
  - [x] 13.4 Write unit tests for search and filter
    - Test search by name
    - Test filter by muscle group
    - _Requirements: 2.2_


- [x] 14. Implement background sync service
  - Create WorkManager periodic task for syncing pending logs
  - Run every 15 minutes when device has connectivity
  - Use exponential backoff for failed syncs
  - Update sync status in Room after successful sync
  - _Requirements: 6.5, 12.5_

- [x] 15. Checkpoint - Test repository layer
  - Test offline-first behavior by disabling network
  - Verify pending syncs are retried when connectivity restored
  - Test conflict resolution with concurrent updates
  - Ensure all tests pass, ask the user if questions arise

### Phase 4: Mobile App - Core ViewModels and Use Cases

- [x] 16. Implement authentication flow
  - [x] 16.1 Create AuthViewModel with Firebase Authentication
    - Implement Google Sign-In
    - Implement Samsung Account Sign-In
    - Handle authentication state changes
    - _Requirements: 1.5, 17.1_
  
  - [x] 16.2 Write property test for authentication requirement
    - **Property 20: Authentication Required for Data Access**
    - **Validates: Requirements 6.2, 17.1, 17.2**
  
  - [x] 16.3 Write property test for user data isolation
    - **Property 21: User Data Isolation**
    - **Validates: Requirements 17.3**


- [ ] 17. Implement DashboardViewModel
  - [x] 17.1 Create DashboardViewModel with state management
    - Load next scheduled workout from WorkoutRepository
    - Calculate weekly stats (total workouts, volume)
    - Load recent personal records from user document
    - Expose DashboardState as StateFlow
    - _Requirements: 14.1, 14.2, 14.3, 14.4_
  
  - [x] 17.2 Write property test for weekly aggregation correctness
    - **Property 19: Weekly and Monthly Aggregation Correctness**
    - **Validates: Requirements 7.4, 10.5, 14.3**
  
  - [~] 17.3 Write unit tests for DashboardViewModel
    - Test loading next workout
    - Test weekly stats calculation
    - Test loading recent records
    - _Requirements: 14.2, 14.3_

- [ ] 18. Implement WorkoutExecutionViewModel
  - [x] 18.1 Create WorkoutExecutionViewModel with workout flow logic
    - Manage current exercise and set progression
    - Handle set completion with automatic rest timer start
    - Allow weight and reps adjustment during session
    - Save TrainingLog when workout completes
    - Track workout duration
    - _Requirements: 4.1, 4.2, 4.3, 4.5, 4.6_
  
  - [~] 18.2 Write property test for rest timer auto-start
    - **Property 7: Rest Timer Auto-Start on Set Completion**
    - **Validates: Requirements 4.3, 5.3, 13.1**
  
  - [~] 18.3 Write property test for in-session adjustment persistence
    - **Property 26: In-Session Weight/Reps Adjustment Persistence**
    - **Validates: Requirements 4.5, 5.5**
  
  - [~] 18.4 Write unit tests for WorkoutExecutionViewModel
    - Test set completion flow
    - Test rest timer countdown
    - Test workout completion and save
    - _Requirements: 4.2, 4.3, 4.6_


- [ ] 19. Implement WorkoutPlanViewModel
  - [~] 19.1 Create WorkoutPlanViewModel for plan creation and editing
    - Load workout plans from WorkoutRepository
    - Handle plan creation with exercise selection
    - Handle plan editing (add/remove exercises, configure sets/reps/rest)
    - Save plans to repository
    - Delete plans from repository
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_
  
  - [~] 19.2 Write unit tests for WorkoutPlanViewModel
    - Test plan creation
    - Test exercise addition
    - Test plan save and delete
    - _Requirements: 3.4, 3.6_

- [ ] 20. Implement TrainingHistoryViewModel
  - [~] 20.1 Create TrainingHistoryViewModel for history display
    - Load training logs from TrainingLogRepository
    - Sort logs by timestamp descending
    - Calculate weekly and monthly progress charts
    - Highlight personal records
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  
  - [~] 20.2 Write property test for chronological ordering
    - **Property 13: Training History Chronological Ordering**
    - **Validates: Requirements 7.1**
  
  - [~] 20.3 Write property test for training log data completeness
    - **Property 14: Training Log Data Completeness in History**
    - **Validates: Requirements 7.2**
  
  - [~] 20.4 Write unit tests for TrainingHistoryViewModel
    - Test history loading
    - Test progress chart data
    - _Requirements: 7.1, 7.4_


- [~] 21. Implement ExerciseLibraryViewModel
  - Create ExerciseLibraryViewModel for exercise browsing
  - Load exercises from ExerciseRepository
  - Implement search functionality
  - Implement muscle group filtering
  - Handle exercise detail display
  - _Requirements: 2.1, 2.2, 2.3, 2.4_

- [~] 22. Checkpoint - Test ViewModels
  - Test all ViewModels with mock repositories
  - Verify state transitions are correct
  - Test error handling in ViewModels
  - Ensure all tests pass, ask the user if questions arise

### Phase 5: Mobile App - UI Implementation

- [ ] 23. Implement authentication screens
  - Create LoginScreen with Google and Samsung sign-in buttons
  - Create ProfileCreationScreen for first-time users
  - Implement navigation from login to dashboard
  - _Requirements: 1.1, 1.5_

- [ ] 24. Implement DashboardScreen
  - [ ] 24.1 Create DashboardScreen composable
    - Display next scheduled workout with prominent "Start Workout" button
    - Show weekly stats summary
    - Display recent personal records
    - Implement navigation to workout execution
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5_
  
  - [ ] 24.2 Write UI tests for DashboardScreen
    - Test "Start Workout" button navigation
    - Test stats display
    - _Requirements: 14.5_


- [ ] 25. Implement WorkoutExecutionScreen
  - [ ] 25.1 Create WorkoutExecutionScreen composable
    - Display current exercise with large, easy-to-tap buttons (min 56dp)
    - Show set progression and completed sets
    - Display rest timer countdown with visual feedback
    - Allow weight and reps adjustment with number pickers
    - Provide haptic feedback on set completion and timer expiration
    - Show workout completion summary
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 13.2, 18.4_
  
  - [ ] 25.2 Write property test for touch target minimum size on mobile
    - **Property 24: Touch Target Minimum Size on Mobile**
    - **Validates: Requirements 18.4**
  
  - [ ] 25.3 Write property test for haptic feedback on timer expiration
    - **Property 8: Haptic Feedback on Timer Expiration**
    - **Validates: Requirements 5.4, 13.2, 13.4**
  
  - [ ] 25.4 Write UI tests for WorkoutExecutionScreen
    - Test set completion flow
    - Test rest timer display
    - Test weight/reps adjustment
    - _Requirements: 4.2, 4.3, 4.5_

- [ ] 26. Implement WorkoutPlanEditorScreen
  - [ ] 26.1 Create WorkoutPlanEditorScreen composable
    - Display form for plan name and description
    - Show training days with exercise lists
    - Implement exercise selection from library
    - Allow configuration of sets, reps, rest time per exercise
    - Provide save and delete actions
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.6_
  
  - [ ] 26.2 Write UI tests for WorkoutPlanEditorScreen
    - Test plan creation flow
    - Test exercise addition
    - _Requirements: 3.2, 3.4_


- [ ] 27. Implement TrainingHistoryScreen
  - [ ] 27.1 Create TrainingHistoryScreen composable
    - Display list of completed workouts ordered by date
    - Show exercise details for each session
    - Display volume and calorie data
    - Highlight personal records with visual indicators
    - Show weekly and monthly progress charts
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_
  
  - [ ] 27.2 Write UI tests for TrainingHistoryScreen
    - Test history list display
    - Test personal record highlighting
    - _Requirements: 7.1, 7.5_

- [ ] 28. Implement ExerciseLibraryScreen
  - [ ] 28.1 Create ExerciseLibraryScreen composable
    - Display exercise list with search bar
    - Implement muscle group filter chips
    - Show exercise cards with name and muscle group
    - Display exercise detail dialog with instructions and media
    - Support GIF and video playback
    - _Requirements: 2.1, 2.2, 2.3, 2.4_
  
  - [ ] 28.2 Write UI tests for ExerciseLibraryScreen
    - Test search functionality
    - Test muscle group filtering
    - _Requirements: 2.2_

- [ ] 29. Implement responsive layouts and accessibility
  - Test layouts on 5", 6", and 7" screens
  - Ensure all touch targets meet minimum size requirements
  - Add content descriptions for accessibility
  - Test high contrast mode for outdoor visibility
  - _Requirements: 18.1, 18.2, 18.4, 18.5_

- [ ] 30. Checkpoint - Test mobile app end-to-end
  - Test complete workout flow from dashboard to history
  - Test offline mode with network disabled
  - Verify sync when connectivity restored
  - Test all navigation flows
  - Ensure all tests pass, ask the user if questions arise


### Phase 6: Watch App - Core Implementation

- [ ] 31. Implement Health Services API integration
  - [ ] 31.1 Create HeartRateMonitor class
    - Request health permissions
    - Start continuous heart rate monitoring during workout
    - Emit heart rate values via Flow
    - Calculate average heart rate per set
    - Handle sensor unavailability gracefully
    - _Requirements: 9.1, 9.2, 9.3_
  
  - [ ] 31.2 Write property test for heart rate data inclusion
    - **Property 17: Heart Rate Data Inclusion in Training Logs**
    - **Validates: Requirements 9.2, 9.3, 9.4, 9.5**
  
  - [ ] 31.3 Write unit tests for HeartRateMonitor
    - Test permission handling
    - Test sensor unavailability
    - _Requirements: 9.1, 9.2_

- [ ] 32. Implement CalorieTracker class
  - [ ] 32.1 Create CalorieTracker with Health Services API
    - Start calorie tracking during workout
    - Track cumulative calories burned
    - Provide total calories at workout completion
    - _Requirements: 10.1, 10.2, 10.3_
  
  - [ ] 32.2 Write property test for calorie data inclusion
    - **Property 18: Calorie Data Inclusion in Training Logs**
    - **Validates: Requirements 10.2, 10.3, 10.4**
  
  - [ ] 32.3 Write unit tests for CalorieTracker
    - Test tracking start and stop
    - Test total calculation
    - _Requirements: 10.2, 10.3_


- [ ] 33. Implement direct watch-to-cloud sync
  - [ ] 33.1 Create DirectSyncManager for Cloud Functions communication
    - Implement HTTPS client with Firebase Auth token injection
    - Create request/response models for syncWorkout function
    - Implement exponential backoff retry logic (max 3 attempts)
    - Cache failed syncs locally with PENDING_SYNC status
    - _Requirements: 6.1, 6.2, 6.5_
  
  - [ ] 33.2 Write unit tests for DirectSyncManager
    - Test successful sync
    - Test retry logic with network failures
    - Test local caching on failure
    - _Requirements: 6.1, 6.5_

- [ ] 34. Implement WatchWorkoutViewModel
  - [ ] 34.1 Create WatchWorkoutViewModel with workout execution logic
    - Manage current exercise and set progression
    - Integrate HeartRateMonitor and CalorieTracker
    - Handle set completion with automatic rest timer
    - Trigger haptic feedback on set completion and timer expiration
    - Allow weight and reps adjustment
    - Save TrainingLog and sync via DirectSyncManager
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_
  
  - [ ] 34.2 Write unit tests for WatchWorkoutViewModel
    - Test workout flow with health data integration
    - Test haptic feedback triggers
    - Test direct sync on completion
    - _Requirements: 5.3, 5.4, 5.6_


- [ ] 35. Implement WatchWorkoutScreen
  - [ ] 35.1 Create WatchWorkoutScreen composable for Wear OS
    - Design UI optimized for round and square watch faces
    - Display current exercise with large touch targets (min 48dp)
    - Show set progression and heart rate
    - Display rest timer countdown with circular progress
    - Show calories burned
    - Provide weight and reps adjustment with rotary input support
    - Display sync status indicator
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 18.2, 18.3_
  
  - [ ] 35.2 Write property test for touch target minimum size on watch
    - **Property 23: Touch Target Minimum Size on Watch**
    - **Validates: Requirements 18.3**
  
  - [ ] 35.3 Write UI tests for WatchWorkoutScreen
    - Test set completion flow
    - Test rest timer display
    - Test heart rate display
    - _Requirements: 5.2, 5.3_

- [ ] 36. Implement haptic feedback system
  - Create HapticFeedbackManager for watch
  - Implement short vibration pattern for set completion
  - Implement long vibration pattern (min 1 second) for timer expiration
  - Integrate with WatchWorkoutViewModel
  - _Requirements: 5.4, 13.2, 13.3_

- [ ] 37. Checkpoint - Test watch app core functionality
  - Test workout execution on physical watch or emulator
  - Verify heart rate and calorie tracking
  - Test direct sync to Cloud Functions
  - Verify haptic feedback patterns
  - Ensure all tests pass, ask the user if questions arise


### Phase 7: Advanced Features

- [ ] 38. Implement Wear OS Tile
  - [ ] 38.1 Create WorkoutTile service
    - Display next scheduled workout or last completed workout
    - Show workout summary (date, exercises)
    - Implement tap action to launch workout directly
    - Update tile automatically after workout completion
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_
  
  - [ ] 38.2 Write unit tests for WorkoutTile
    - Test tile data loading
    - Test tile update triggers
    - _Requirements: 11.2, 11.5_

- [ ] 39. Implement auto-detect workout feature
  - [ ] 39.1 Create WorkoutAutoDetectService
    - Monitor accelerometer for repetitive movement patterns
    - Detect strength training patterns (threshold: 30 seconds)
    - Display notification with quick action to start workout
    - Implement rate limiting (max 1 notification per hour)
    - Add enable/disable setting in preferences
    - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5_
  
  - [ ] 39.2 Write property test for auto-detect rate limiting
    - **Property 22: Auto-Detect Notification Rate Limiting**
    - **Validates: Requirements 15.4**
  
  - [ ] 39.3 Write unit tests for WorkoutAutoDetectService
    - Test movement pattern detection
    - Test notification trigger
    - Test rate limiting logic
    - _Requirements: 15.2, 15.4_


- [ ] 40. Implement push notifications
  - [ ] 40.1 Configure Firebase Cloud Messaging in both apps
    - Set up FCM in mobile and watch apps
    - Handle notification permissions
    - Implement notification receivers
    - _Requirements: 8.5, 16.2, 16.3_
  
  - [ ] 40.2 Write property test for notification content completeness
    - **Property 27: Notification Content Completeness**
    - **Validates: Requirements 15.3, 16.4**
  
  - [ ] 40.3 Write unit tests for notification handling
    - Test notification display
    - Test quick actions
    - _Requirements: 16.3, 16.4_

- [ ] 41. Implement user preferences and settings
  - Create SettingsScreen for mobile app
  - Add reminder configuration (enable/disable, time selection)
  - Add auto-detect toggle
  - Add profile editing (weight, fitness level, goal)
  - Sync preferences to Firestore
  - _Requirements: 1.1, 1.2, 15.5, 16.1, 16.5_

- [ ] 42. Implement workout plan import/export
  - [ ] 42.1 Create import/export functionality
    - Implement JSON export for workout plans
    - Implement JSON import with validation
    - Add share functionality for exported plans
    - _Requirements: 19.1, 19.2, 19.3, 19.5, 19.6_
  
  - [ ] 42.2 Write unit tests for import/export
    - Test export format
    - Test import validation
    - Test error handling for invalid JSON
    - _Requirements: 19.2, 19.5_


- [ ] 43. Checkpoint - Test advanced features
  - Test Wear OS Tile on physical watch
  - Test auto-detect with actual workout movements
  - Test push notifications on both devices
  - Test workout plan import/export
  - Ensure all tests pass, ask the user if questions arise

### Phase 8: Polish and Optimization

- [ ] 44. Implement error handling and user feedback
  - Add error dialogs for network failures
  - Show offline mode indicator in UI
  - Display sync status for pending operations
  - Add loading states for all async operations
  - Implement retry actions for failed operations
  - _Requirements: 12.3, 12.5_

- [ ] 45. Optimize performance and battery usage
  - Implement cache size limits with LRU eviction
  - Optimize Firestore queries with proper indexing
  - Reduce sensor polling frequency when appropriate
  - Test battery consumption during 1-hour workout
  - Optimize image loading and caching
  - _Requirements: 9.2, 10.2_

- [ ] 46. Implement data seeding for exercise library
  - Create script to populate Firestore with exercise data
  - Include exercises for all major muscle groups
  - Add exercise instructions and media URLs
  - Ensure all exercises have complete data
  - _Requirements: 2.1_


- [ ] 47. Implement comprehensive logging and monitoring
  - Set up Firebase Crashlytics for crash reporting
  - Add structured logging for network errors
  - Track sync success/failure rates
  - Monitor validation error frequency
  - Add performance monitoring for critical paths
  - Ensure no sensitive data (weights, personal info) is logged
  - _Requirements: 17.4_

- [ ] 48. Final integration testing
  - [ ] 48.1 Test complete user journey from signup to workout completion
    - Create account → create workout plan → execute workout on watch → view history on mobile
    - Verify data appears correctly across all devices
    - _Requirements: All_
  
  - [ ] 48.2 Test offline scenarios
    - Complete workout offline on watch → verify sync when online
    - Create plan offline on mobile → verify sync when online
    - Test conflict resolution with concurrent edits
    - _Requirements: 12.1, 12.3, 12.5, 12.6_
  
  - [ ] 48.3 Test cross-device synchronization
    - Create plan on mobile → verify appears on watch
    - Complete workout on watch → verify appears in mobile history
    - Update profile on mobile → verify syncs to watch
    - _Requirements: 1.2, 3.4, 6.4_
  
  - [ ] 48.4 Test security and data isolation
    - Verify users cannot access other users' data
    - Test authentication token expiration handling
    - Verify Firestore security rules enforcement
    - _Requirements: 17.1, 17.2, 17.3_


- [ ] 49. Final checkpoint - Complete system verification
  - Run all unit tests and property-based tests
  - Verify all 27 correctness properties pass
  - Test on multiple device configurations (different phones and watches)
  - Perform usability testing with real users
  - Verify performance metrics meet targets (sync <2s, UI <100ms, battery <10%/hour)
  - Ensure all tests pass, ask the user if questions arise

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP delivery
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation and provide opportunities for user feedback
- Property-based tests validate universal correctness properties across all inputs
- Unit tests validate specific examples, edge cases, and integration points
- The implementation follows an incremental approach: foundation → core features → advanced features → polish
- All 27 correctness properties from the design document are covered by property-based test tasks
- Backend (TypeScript) and mobile/watch apps (Kotlin) are developed in parallel where possible
- Offline-first architecture ensures no data loss and seamless user experience

## Testing Summary

**Property-Based Tests (27 properties):**
- Property 1: Workout Plan Round-Trip Serialization (Task 3.4)
- Property 2: Training Log Validation Rejects Invalid Data (Task 4.2)
- Property 3: Training Log Validation Accepts Valid Data (Task 4.3)
- Property 4: Volume Calculation Correctness (Task 6.2)
- Property 5: Personal Record Detection for Max Weight (Task 6.4)
- Property 6: Personal Record Detection for Max Volume (Task 6.5)
- Property 7: Rest Timer Auto-Start on Set Completion (Task 18.2)
- Property 8: Haptic Feedback on Timer Expiration (Task 25.3)
- Property 9: Exercise Library Data Completeness (Task 13.2)
- Property 10: Exercise Filter by Muscle Group Correctness (Task 13.3)
- Property 11: Workout Plan Creation Preserves Exercise Count (Task 11.2)
- Property 12: Workout Plan Configuration Persistence (Task 11.3)
- Property 13: Training History Chronological Ordering (Task 20.2)
- Property 14: Training Log Data Completeness in History (Task 20.3)
- Property 15: Offline Cache Synchronization Completeness (Task 12.2)
- Property 16: Conflict Resolution by Timestamp (Task 12.3)
- Property 17: Heart Rate Data Inclusion in Training Logs (Task 31.2)
- Property 18: Calorie Data Inclusion in Training Logs (Task 32.2)
- Property 19: Weekly and Monthly Aggregation Correctness (Task 17.2)
- Property 20: Authentication Required for Data Access (Task 16.2)
- Property 21: User Data Isolation (Task 16.3)
- Property 22: Auto-Detect Notification Rate Limiting (Task 39.2)
- Property 23: Touch Target Minimum Size on Watch (Task 35.2)
- Property 24: Touch Target Minimum Size on Mobile (Task 25.2)
- Property 25: Workout Plan Deletion Removes from User's List (Task 11.4)
- Property 26: In-Session Weight/Reps Adjustment Persistence (Task 18.3)
- Property 27: Notification Content Completeness (Task 40.2)

**Unit Test Coverage:**
- Backend validation and business logic
- Repository offline-first behavior
- ViewModel state management
- UI component interactions
- Health Services API integration
- Sync retry logic and error handling

