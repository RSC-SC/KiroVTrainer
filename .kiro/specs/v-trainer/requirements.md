# Requirements Document

## Introduction

O V-Trainer é um aplicativo Android nativo com integração profunda com Wear OS (Galaxy Watch), focado exclusivamente em musculação. O sistema permite que o SmartWatch opere de forma independente do smartphone, enviando dados de treino diretamente para a nuvem via Wi-Fi/LTE. O objetivo é proporcionar uma experiência de uso fluida comparável ao Samsung Health, com foco em atrito zero durante a execução dos treinos.

## Glossary

- **V-Trainer_System**: O sistema completo incluindo aplicativo mobile, aplicativo Wear OS, backend Firebase e banco de dados Firestore
- **Mobile_App**: Aplicativo Android nativo desenvolvido em Kotlin com Jetpack Compose para smartphones
- **Watch_App**: Aplicativo Wear OS desenvolvido em Kotlin com Compose for Wear OS para smartwatches
- **Cloud_Functions**: Backend serverless implementado com Firebase Cloud Functions em TypeScript/Node.js
- **Firestore**: Banco de dados NoSQL do Firebase com suporte offline-first
- **Training_Session**: Uma sessão completa de treino contendo múltiplos exercícios
- **Exercise**: Um exercício específico de musculação com suas séries, repetições e carga
- **Set**: Uma série individual de um exercício contendo repetições, carga e tempo de descanso
- **Workout_Plan**: Planilha de treino contendo divisão de treinos (ex: Treino A, B, C)
- **Training_Log**: Registro histórico de uma sessão de treino concluída
- **Health_Services_API**: API nativa do Android para leitura de sensores de saúde e fitness
- **Rest_Timer**: Cronômetro de descanso entre séries
- **Personal_Record**: Recorde pessoal do usuário em um exercício específico (maior carga ou volume)
- **Volume**: Cálculo de carga total de treino (peso × repetições)
- **Haptic_Feedback**: Vibração tátil do dispositivo para notificações
- **Tile**: Cartão rápido do Wear OS para acesso direto a funcionalidades
- **Offline_Mode**: Modo de operação sem conexão com internet, usando cache local

## Requirements

### Requirement 1: User Profile Management

**User Story:** As a user, I want to create and manage my fitness profile, so that the system can personalize my training experience.

#### Acceptance Criteria

1. THE Mobile_App SHALL allow users to create a profile with name, fitness level (beginner, intermediate, advanced), current weight, and training goal
2. WHEN a user updates their profile, THE V-Trainer_System SHALL synchronize the changes to Firestore within 5 seconds
3. THE Mobile_App SHALL display the user's current profile information on the dashboard
4. WHEN a user authenticates, THE V-Trainer_System SHALL load their profile from Firestore
5. THE V-Trainer_System SHALL support authentication via Google Account or Samsung Account

### Requirement 2: Exercise Library Management

**User Story:** As a user, I want to access a comprehensive library of exercises with instructions, so that I can learn proper form and technique.

#### Acceptance Criteria

1. THE V-Trainer_System SHALL maintain a library of exercises in Firestore with exercise name, target muscle group, instruction text, and media URL
2. THE Mobile_App SHALL display the exercise library with search and filter capabilities by muscle group
3. WHEN a user selects an exercise, THE Mobile_App SHALL display detailed instructions and demonstration media
4. THE V-Trainer_System SHALL support GIF or short video format for exercise demonstrations
5. THE Mobile_App SHALL cache exercise data locally for offline access

### Requirement 3: Workout Plan Creation

**User Story:** As a user, I want to create custom workout plans with exercise splits, so that I can organize my training routine.

#### Acceptance Criteria

1. THE Mobile_App SHALL allow users to create workout plans with multiple training days (ex: Treino A, B, C)
2. WHEN creating a workout plan, THE Mobile_App SHALL allow users to add exercises from the library to each training day
3. THE Mobile_App SHALL allow users to configure sets, repetitions, and rest time for each exercise
4. WHEN a workout plan is saved, THE V-Trainer_System SHALL store it in Firestore under the user's workout_plans subcollection
5. THE Mobile_App SHALL display all created workout plans on the dashboard
6. THE Mobile_App SHALL allow users to edit or delete existing workout plans

### Requirement 4: Training Session Execution on Mobile

**User Story:** As a user, I want to execute my workout on my smartphone with easy controls, so that I can track my training efficiently.

#### Acceptance Criteria

1. WHEN a user starts a training session, THE Mobile_App SHALL display the current exercise with configured sets, reps, and weight
2. THE Mobile_App SHALL provide large, easy-to-tap buttons for marking sets as complete
3. WHEN a set is marked complete, THE Mobile_App SHALL automatically start the Rest_Timer with the configured rest duration
4. WHEN the Rest_Timer expires, THE Mobile_App SHALL provide visual and audio notification
5. THE Mobile_App SHALL allow users to adjust weight and repetitions during the training session
6. WHEN a training session is completed, THE Mobile_App SHALL save the Training_Log to Firestore

### Requirement 5: Training Session Execution on Watch

**User Story:** As a user, I want to execute my workout directly on my smartwatch without my phone, so that I can train with minimal equipment.

#### Acceptance Criteria

1. THE Watch_App SHALL allow users to start a training session independently of the Mobile_App
2. THE Watch_App SHALL display the current exercise with large, touch-friendly buttons for marking sets complete
3. WHEN a set is marked complete on the Watch_App, THE Watch_App SHALL automatically start the Rest_Timer
4. WHEN the Rest_Timer expires, THE Watch_App SHALL trigger Haptic_Feedback with a distinct vibration pattern
5. THE Watch_App SHALL allow users to adjust weight and repetitions using optimized watch UI controls
6. WHEN a training session is completed on the Watch_App, THE Watch_App SHALL send the Training_Log directly to Cloud_Functions via Wi-Fi or LTE

### Requirement 6: Direct Watch-to-Cloud Synchronization

**User Story:** As a user, I want my watch to sync training data directly to the cloud, so that I don't need to carry my phone during workouts.

#### Acceptance Criteria

1. WHEN the Watch_App completes a training session, THE Watch_App SHALL send the Training_Log to Cloud_Functions using HTTPS
2. THE Cloud_Functions SHALL authenticate the request using Firebase Auth tokens
3. WHEN Cloud_Functions receives a Training_Log, THE Cloud_Functions SHALL validate the data structure
4. THE Cloud_Functions SHALL save the validated Training_Log to Firestore with server timestamp
5. IF the Watch_App cannot connect to the internet, THEN THE Watch_App SHALL cache the Training_Log locally and retry synchronization when connectivity is restored

### Requirement 7: Training History and Progress Tracking

**User Story:** As a user, I want to view my training history and progress over time, so that I can track my fitness improvements.

#### Acceptance Criteria

1. THE Mobile_App SHALL display a history of all completed training sessions ordered by date
2. WHEN viewing training history, THE Mobile_App SHALL show exercise details including sets, reps, and weight for each session
3. THE Mobile_App SHALL calculate and display total Volume for each training session
4. THE Mobile_App SHALL display weekly and monthly progress charts showing Volume trends
5. WHEN a user achieves a Personal_Record, THE V-Trainer_System SHALL highlight it in the training history

### Requirement 8: Personal Record Detection

**User Story:** As a user, I want the system to automatically detect when I achieve a personal record, so that I can celebrate my progress.

#### Acceptance Criteria

1. WHEN a Training_Log is saved, THE Cloud_Functions SHALL compare the exercise performance against previous Training_Logs
2. IF the weight lifted for an exercise exceeds all previous records, THEN THE Cloud_Functions SHALL mark it as a Personal_Record
3. IF the total Volume for an exercise exceeds all previous records, THEN THE Cloud_Functions SHALL mark it as a Personal_Record
4. THE Cloud_Functions SHALL store Personal_Record data in the user's Firestore document
5. WHEN a Personal_Record is detected, THE V-Trainer_System SHALL send a push notification to both Mobile_App and Watch_App

### Requirement 9: Heart Rate Monitoring Integration

**User Story:** As a user, I want my watch to monitor my heart rate during training, so that I can track workout intensity.

#### Acceptance Criteria

1. THE Watch_App SHALL integrate with Health_Services_API to access heart rate sensor data
2. WHILE a training session is active, THE Watch_App SHALL continuously read heart rate data
3. THE Watch_App SHALL record average heart rate for each Set
4. WHEN a training session is completed, THE Watch_App SHALL include heart rate data in the Training_Log
5. THE Mobile_App SHALL display heart rate data in the training history for each exercise

### Requirement 10: Calorie Burn Estimation

**User Story:** As a user, I want to see estimated calories burned during my workout, so that I can track my energy expenditure.

#### Acceptance Criteria

1. THE Watch_App SHALL integrate with Health_Services_API to access calorie burn data
2. WHILE a training session is active, THE Watch_App SHALL track cumulative calories burned
3. WHEN a training session is completed, THE Watch_App SHALL include total calories burned in the Training_Log
4. THE Mobile_App SHALL display calories burned for each training session in the history
5. THE Mobile_App SHALL display weekly and monthly calorie burn totals

### Requirement 11: Wear OS Quick Access Tile

**User Story:** As a user, I want a quick access tile on my watch, so that I can start my workout with one tap.

#### Acceptance Criteria

1. THE Watch_App SHALL provide a Wear OS Tile for quick access
2. THE Tile SHALL display the next scheduled workout or most recent workout plan
3. WHEN a user taps the Tile, THE Watch_App SHALL launch directly into the training session
4. THE Tile SHALL display a summary of the last completed workout including date and exercises
5. THE Tile SHALL update automatically when a new workout is completed

### Requirement 12: Offline-First Data Architecture

**User Story:** As a user, I want to use the app without internet connection, so that I can train anywhere without connectivity concerns.

#### Acceptance Criteria

1. THE Mobile_App SHALL cache all workout plans and exercise library data locally using Room database
2. THE Watch_App SHALL cache the current workout plan locally
3. WHEN offline, THE Mobile_App SHALL allow users to execute training sessions using cached data
4. WHEN offline, THE Watch_App SHALL allow users to execute training sessions using cached data
5. WHEN connectivity is restored, THE V-Trainer_System SHALL automatically synchronize all cached Training_Logs to Firestore
6. THE V-Trainer_System SHALL resolve synchronization conflicts by prioritizing the most recent timestamp

### Requirement 13: Rest Timer with Haptic Feedback

**User Story:** As a user, I want haptic feedback when my rest time is complete, so that I know when to start my next set without looking at the screen.

#### Acceptance Criteria

1. WHEN a Rest_Timer is started, THE Watch_App SHALL display a countdown timer
2. WHEN the Rest_Timer reaches zero, THE Watch_App SHALL trigger a long vibration pattern (minimum 1 second)
3. THE Watch_App SHALL provide a short vibration when a set is marked complete
4. THE Mobile_App SHALL provide haptic feedback when the Rest_Timer expires
5. THE V-Trainer_System SHALL allow users to customize rest timer durations per exercise

### Requirement 14: Dashboard and Quick Start

**User Story:** As a user, I want a clean dashboard with quick access to today's workout, so that I can start training immediately.

#### Acceptance Criteria

1. THE Mobile_App SHALL display a dashboard as the home screen
2. THE dashboard SHALL show the next scheduled workout with a prominent "Start Workout" button
3. THE dashboard SHALL display weekly progress summary including total workouts and Volume
4. THE dashboard SHALL show the most recent Personal_Record achievements
5. WHEN a user taps "Start Workout", THE Mobile_App SHALL launch directly into the training session

### Requirement 15: Auto-Detect Training Start

**User Story:** As a user, I want my watch to suggest starting a workout when it detects training movements, so that I don't forget to track my session.

#### Acceptance Criteria

1. THE Watch_App SHALL monitor accelerometer data for repetitive movement patterns
2. WHEN repetitive movement patterns consistent with strength training are detected for more than 30 seconds, THE Watch_App SHALL display a notification suggesting to start a training session
3. THE notification SHALL include a quick action button to start the most recent workout plan
4. THE Watch_App SHALL not trigger auto-detect notifications more than once per hour
5. THE V-Trainer_System SHALL allow users to enable or disable auto-detect functionality

### Requirement 16: Push Notifications and Reminders

**User Story:** As a user, I want to receive reminders for scheduled workouts, so that I stay consistent with my training routine.

#### Acceptance Criteria

1. THE Mobile_App SHALL allow users to configure workout reminder times
2. WHEN a reminder time is reached, THE Cloud_Functions SHALL send a push notification via Firebase Cloud Messaging
3. THE push notification SHALL be delivered to both Mobile_App and Watch_App
4. THE notification SHALL include the scheduled workout name and a quick action to start the session
5. THE V-Trainer_System SHALL allow users to snooze or dismiss reminders

### Requirement 17: Data Privacy and Security

**User Story:** As a user, I want my training data to be secure and private, so that my personal information is protected.

#### Acceptance Criteria

1. THE V-Trainer_System SHALL require authentication before accessing any user data
2. THE Cloud_Functions SHALL validate Firebase Auth tokens for all API requests
3. THE Firestore SHALL enforce security rules ensuring users can only access their own data
4. THE V-Trainer_System SHALL encrypt all data in transit using HTTPS/TLS
5. THE V-Trainer_System SHALL not share user data with third parties without explicit consent

### Requirement 18: Responsive UI for Different Screen Sizes

**User Story:** As a user, I want the app to work well on different Android devices, so that I have a consistent experience across my devices.

#### Acceptance Criteria

1. THE Mobile_App SHALL adapt layout for screen sizes from 5 inches to 7 inches
2. THE Watch_App SHALL adapt layout for round and square watch faces
3. THE Watch_App SHALL ensure all touch targets are minimum 48dp for easy tapping during workouts
4. THE Mobile_App SHALL ensure all touch targets in training mode are minimum 56dp
5. THE V-Trainer_System SHALL maintain readability in bright sunlight conditions with high contrast UI elements

### Requirement 19: Exercise Configuration Parser

**User Story:** As a developer, I want to parse workout plan configurations, so that users can import and export their training routines.

#### Acceptance Criteria

1. WHEN a valid workout plan JSON is provided, THE V-Trainer_System SHALL parse it into a Workout_Plan object
2. WHEN an invalid workout plan JSON is provided, THE V-Trainer_System SHALL return a descriptive error message
3. THE V-Trainer_System SHALL provide a formatter to export Workout_Plan objects to valid JSON
4. FOR ALL valid Workout_Plan objects, parsing then formatting then parsing SHALL produce an equivalent object (round-trip property)
5. THE Mobile_App SHALL allow users to export workout plans as JSON files
6. THE Mobile_App SHALL allow users to import workout plans from JSON files

### Requirement 20: Training Log Data Validation

**User Story:** As a developer, I want to validate training log data, so that only correct data is stored in the database.

#### Acceptance Criteria

1. WHEN a Training_Log is received by Cloud_Functions, THE Cloud_Functions SHALL validate that all required fields are present
2. THE Cloud_Functions SHALL validate that weight values are positive numbers
3. THE Cloud_Functions SHALL validate that repetition values are positive integers
4. THE Cloud_Functions SHALL validate that rest time values are non-negative integers
5. IF validation fails, THEN THE Cloud_Functions SHALL return an error response with specific validation failure details
6. THE Cloud_Functions SHALL reject Training_Logs with timestamps more than 24 hours in the future

