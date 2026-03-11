# V-Trainer - Design Document

## Overview

O V-Trainer é um sistema de treinamento de musculação que integra aplicativos Android nativos (mobile e Wear OS) com backend Firebase, permitindo operação independente do smartwatch através de sincronização direta com a nuvem. O sistema prioriza uma experiência de atrito zero durante os treinos, com foco em usabilidade, sincronização offline-first e feedback háptico inteligente.

### Objetivos do Design

- **Independência do Watch**: O smartwatch deve operar completamente independente do smartphone, sincronizando dados diretamente com Firebase via Wi-Fi/LTE
- **Offline-First**: Ambos os dispositivos devem funcionar sem conexão, sincronizando automaticamente quando a conectividade for restaurada
- **Atrito Zero**: Interface otimizada para uso durante exercícios físicos, com botões grandes e feedback háptico claro
- **Sincronização em Tempo Real**: Dados de treino devem aparecer instantaneamente em todos os dispositivos do usuário
- **Escalabilidade**: Arquitetura preparada para crescimento de usuários e funcionalidades

### Tecnologias Principais

- **Frontend Mobile**: Kotlin + Jetpack Compose
- **Frontend Wear OS**: Kotlin + Compose for Wear OS
- **Backend**: Firebase Cloud Functions (TypeScript/Node.js)
- **Database**: Firestore (NoSQL) com suporte offline
- **Cache Local**: Room Database
- **Sensores**: Health Services API do Android
- **Autenticação**: Firebase Authentication (Google/Samsung Account)
- **Notificações**: Firebase Cloud Messaging (FCM)

## Architecture

### System Architecture

O V-Trainer segue uma arquitetura de três camadas com sincronização bidirecional:

```
┌─────────────────┐         ┌─────────────────┐
│   Mobile App    │         │   Watch App     │
│  (Jetpack       │         │  (Compose for   │
│   Compose)      │         │   Wear OS)      │
└────────┬────────┘         └────────┬────────┘
         │                           │
         │ HTTPS/REST                │ HTTPS/REST
         │                           │ (Wi-Fi/LTE)
         └───────────┬───────────────┘
                     │
         ┌───────────▼────────────┐
         │  Firebase Cloud        │
         │  Functions             │
         │  (TypeScript/Node.js)  │
         └───────────┬────────────┘
                     │
         ┌───────────▼────────────┐
         │  Firestore Database    │
         │  (NoSQL)               │
         └────────────────────────┘
```

### Architectural Patterns

**1. MVVM (Model-View-ViewModel)**
- Separação clara entre UI (Compose), lógica de apresentação (ViewModel) e dados (Repository)
- ViewModels gerenciam estado da UI e comunicação com repositórios
- Compose observa StateFlow/LiveData para atualizações reativas

**2. Repository Pattern**
- Camada de abstração entre ViewModels e fontes de dados (Firestore, Room)
- Gerencia estratégia offline-first: tenta Firestore primeiro, fallback para Room
- Centraliza lógica de sincronização e resolução de conflitos

**3. Offline-First Strategy**
- Dados são sempre salvos localmente primeiro (Room)
- Sincronização com Firestore acontece em background
- Conflitos resolvidos por timestamp (last-write-wins)

**4. Clean Architecture Layers**
```
Presentation Layer (Compose UI + ViewModels)
        ↓
Domain Layer (Use Cases + Business Logic)
        ↓
Data Layer (Repositories + Data Sources)
        ↓
External Layer (Firestore, Room, Health Services API)
```

### Communication Patterns

**Mobile ↔ Firestore**
- Leitura: Firestore SDK com listeners em tempo real
- Escrita: Batch writes para operações atômicas
- Cache: Firestore persistence habilitada + Room para dados críticos

**Watch ↔ Cloud Functions**
- Protocolo: HTTPS REST com Firebase Auth tokens
- Payload: JSON compactado para economizar bateria
- Retry Logic: Exponential backoff com máximo de 3 tentativas
- Fallback: Cache local em Room até conectividade ser restaurada

**Watch ↔ Mobile (Opcional)**
- Data Layer API para comunicação direta quando ambos estão próximos
- Usado apenas para sincronização de configurações rápidas
- Não é crítico para operação do sistema

## Components and Interfaces

### Mobile App Components

#### 1. Dashboard Screen
```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onStartWorkout: (WorkoutPlan) -> Unit
)
```

**Responsabilidades:**
- Exibir próximo treino agendado
- Mostrar resumo semanal (volume total, treinos completados)
- Listar recordes pessoais recentes
- Botão "Start Workout" proeminente

**Estado:**
```kotlin
data class DashboardState(
    val nextWorkout: WorkoutPlan?,
    val weeklyStats: WeeklyStats,
    val recentRecords: List<PersonalRecord>,
    val isLoading: Boolean
)
```

#### 2. Workout Execution Screen
```kotlin
@Composable
fun WorkoutExecutionScreen(
    viewModel: WorkoutViewModel,
    workoutPlan: WorkoutPlan
)
```

**Responsabilidades:**
- Exibir exercício atual com séries, reps e carga
- Botões grandes (min 56dp) para marcar série completa
- Cronômetro de descanso automático
- Permitir ajuste de peso/reps durante execução
- Salvar Training_Log ao finalizar

**Estado:**
```kotlin
data class WorkoutExecutionState(
    val currentExercise: Exercise,
    val currentSet: Int,
    val completedSets: List<SetLog>,
    val restTimerSeconds: Int?,
    val isRestTimerActive: Boolean
)
```

#### 3. Workout Plan Editor Screen
```kotlin
@Composable
fun WorkoutPlanEditorScreen(
    viewModel: WorkoutPlanViewModel,
    planId: String?
)
```

**Responsabilidades:**
- Criar/editar planilhas de treino
- Adicionar exercícios da biblioteca
- Configurar séries, reps e tempo de descanso
- Salvar no Firestore

#### 4. Training History Screen
```kotlin
@Composable
fun TrainingHistoryScreen(
    viewModel: HistoryViewModel
)
```

**Responsabilidades:**
- Listar treinos completados ordenados por data
- Exibir detalhes de cada sessão
- Gráficos de progresso (volume semanal/mensal)
- Destacar recordes pessoais

#### 5. Exercise Library Screen
```kotlin
@Composable
fun ExerciseLibraryScreen(
    viewModel: ExerciseLibraryViewModel
)
```

**Responsabilidades:**
- Listar exercícios com busca e filtro por grupo muscular
- Exibir instruções e mídia demonstrativa (GIF/vídeo)
- Cache local para acesso offline

### Watch App Components

#### 1. Workout Execution Screen (Watch)
```kotlin
@Composable
fun WatchWorkoutScreen(
    viewModel: WatchWorkoutViewModel
)
```

**Responsabilidades:**
- UI otimizada para telas pequenas (round/square)
- Botões grandes (min 48dp) para uso durante exercício
- Integração com Health Services API para HR e calorias
- Haptic feedback ao completar série e ao fim do descanso
- Sincronização direta com Cloud Functions

**Estado:**
```kotlin
data class WatchWorkoutState(
    val currentExercise: Exercise,
    val currentSet: Int,
    val heartRate: Int?,
    val caloriesBurned: Int,
    val restTimerSeconds: Int?,
    val syncStatus: SyncStatus
)
```

#### 2. Workout Tile
```kotlin
class WorkoutTile : TileService()
```

**Responsabilidades:**
- Exibir próximo treino ou último treino completado
- Ação rápida para iniciar treino com um toque
- Atualizar automaticamente após treinos

#### 3. Auto-Detect Service
```kotlin
class WorkoutAutoDetectService : Service()
```

**Responsabilidades:**
- Monitorar acelerômetro para padrões de musculação
- Notificar usuário após 30s de movimento repetitivo
- Limitar notificações a 1 por hora
- Permitir ativação/desativação nas configurações

### Backend Components (Cloud Functions)

#### 1. syncWorkout Function
```typescript
export const syncWorkout = functions.https.onCall(
    async (data: SyncWorkoutRequest, context: CallableContext)
): Promise<SyncWorkoutResponse>
```

**Responsabilidades:**
- Autenticar requisição via Firebase Auth token
- Validar estrutura do Training_Log
- Salvar no Firestore com server timestamp
- Retornar confirmação ou erro

**Request Schema:**
```typescript
interface SyncWorkoutRequest {
    trainingLog: {
        timestamp: string;
        origin: string;
        exercises: Array<{
            exerciseId: string;
            sets: Array<{
                reps: number;
                weight: number;
                rest: number;
                heartRate?: number;
            }>;
        }>;
        totalCalories?: number;
    };
}
```

#### 2. calculateProgress Function
```typescript
export const calculateProgress = functions.firestore
    .document('training_logs/{logId}')
    .onCreate(async (snapshot, context)): Promise<void>
```

**Responsabilidades:**
- Calcular volume total do treino (peso × reps)
- Comparar com treinos anteriores para detectar recordes
- Atualizar documento do usuário com Personal_Records
- Enviar notificação push se recorde for detectado

#### 3. sendWorkoutReminder Function
```typescript
export const sendWorkoutReminder = functions.pubsub
    .schedule('every day 08:00')
    .onRun(async (context): Promise<void>)
```

**Responsabilidades:**
- Verificar usuários com lembretes configurados
- Enviar notificação via FCM para mobile e watch
- Incluir quick action para iniciar treino

### Repository Layer

#### WorkoutRepository
```kotlin
interface WorkoutRepository {
    suspend fun getWorkoutPlans(): Flow<List<WorkoutPlan>>
    suspend fun saveWorkoutPlan(plan: WorkoutPlan): Result<Unit>
    suspend fun deleteWorkoutPlan(planId: String): Result<Unit>
}

class WorkoutRepositoryImpl(
    private val firestore: FirebaseFirestore,
    private val roomDao: WorkoutDao
) : WorkoutRepository
```

**Estratégia Offline-First:**
1. Salvar localmente em Room
2. Tentar sincronizar com Firestore
3. Se falhar, marcar para retry em background
4. Listener do Firestore atualiza Room quando dados mudam

#### TrainingLogRepository
```kotlin
interface TrainingLogRepository {
    suspend fun saveTrainingLog(log: TrainingLog): Result<Unit>
    suspend fun getTrainingHistory(): Flow<List<TrainingLog>>
    suspend fun syncPendingLogs(): Result<Int>
}
```

#### ExerciseRepository
```kotlin
interface ExerciseRepository {
    suspend fun getExercises(): Flow<List<Exercise>>
    suspend fun searchExercises(query: String): List<Exercise>
    suspend fun filterByMuscleGroup(group: MuscleGroup): List<Exercise>
}
```

### Health Services Integration

#### HeartRateMonitor
```kotlin
class HeartRateMonitor(
    private val healthServicesClient: HealthServicesClient
) {
    fun startMonitoring(): Flow<Int>
    fun stopMonitoring()
}
```

**Responsabilidades:**
- Iniciar leitura contínua de HR durante treino
- Emitir valores via Flow para ViewModel
- Calcular média por série
- Parar monitoramento ao finalizar treino

#### CalorieTracker
```kotlin
class CalorieTracker(
    private val healthServicesClient: HealthServicesClient
) {
    fun startTracking(): Flow<Int>
    fun getTotalCalories(): Int
    fun reset()
}
```

## Data Models

### Firestore Schema

#### Collection: users
```json
{
  "userId": "auth_uid_string",
  "name": "João Silva",
  "fitnessLevel": "intermediate",
  "currentWeight": 75.5,
  "trainingGoal": "muscle_gain",
  "createdAt": "2026-03-10T10:00:00Z",
  "updatedAt": "2026-03-15T14:30:00Z",
  "personalRecords": {
    "supino_reto": {
      "maxWeight": 80,
      "maxVolume": 1200,
      "achievedAt": "2026-03-14T16:00:00Z"
    }
  },
  "preferences": {
    "reminderEnabled": true,
    "reminderTime": "08:00",
    "autoDetectEnabled": true
  }
}
```

**Indexes:**
- `userId` (primary key)
- `updatedAt` (for sync queries)

#### Collection: exercises
```json
{
  "exerciseId": "supino_reto",
  "name": "Supino Reto",
  "muscleGroup": "chest",
  "secondaryMuscles": ["triceps", "shoulders"],
  "instructions": "Deite-se no banco...",
  "mediaUrl": "https://storage.googleapis.com/v-trainer/exercises/supino_reto.gif",
  "mediaType": "gif",
  "difficulty": "intermediate",
  "equipment": ["barbell", "bench"]
}
```

**Indexes:**
- `exerciseId` (primary key)
- `muscleGroup` (for filtering)
- `difficulty` (for filtering)

#### Collection: workout_plans
```json
{
  "planId": "plan_uuid",
  "userId": "auth_uid_string",
  "name": "Treino ABC",
  "description": "Divisão de 3 dias focada em hipertrofia",
  "createdAt": "2026-03-10T10:00:00Z",
  "updatedAt": "2026-03-12T15:00:00Z",
  "trainingDays": [
    {
      "dayName": "Treino A - Peito e Tríceps",
      "exercises": [
        {
          "exerciseId": "supino_reto",
          "order": 1,
          "sets": 4,
          "reps": 12,
          "restSeconds": 60,
          "notes": "Foco em controle excêntrico"
        },
        {
          "exerciseId": "supino_inclinado",
          "order": 2,
          "sets": 3,
          "reps": 10,
          "restSeconds": 60
        }
      ]
    },
    {
      "dayName": "Treino B - Costas e Bíceps",
      "exercises": []
    }
  ]
}
```

**Indexes:**
- `planId` (primary key)
- `userId` (for user-specific queries)
- `updatedAt` (for sync)

#### Collection: training_logs
```json
{
  "logId": "log_uuid",
  "userId": "auth_uid_string",
  "workoutPlanId": "plan_uuid",
  "workoutDayName": "Treino A - Peito e Tríceps",
  "timestamp": "2026-03-15T16:00:00Z",
  "origin": "Galaxy_Watch_4",
  "duration": 3600,
  "totalCalories": 450,
  "exercises": [
    {
      "exerciseId": "supino_reto",
      "sets": [
        {
          "setNumber": 1,
          "reps": 12,
          "weight": 60,
          "restSeconds": 60,
          "heartRate": 145,
          "completedAt": "2026-03-15T16:05:00Z"
        },
        {
          "setNumber": 2,
          "reps": 10,
          "weight": 65,
          "restSeconds": 60,
          "heartRate": 152,
          "completedAt": "2026-03-15T16:07:00Z"
        }
      ],
      "totalVolume": 1370,
      "isPersonalRecord": true,
      "recordType": "max_weight"
    }
  ],
  "totalVolume": 5240,
  "syncStatus": "synced",
  "createdAt": "2026-03-15T16:00:00Z"
}
```

**Indexes:**
- `logId` (primary key)
- `userId` + `timestamp` (composite, for history queries)
- `userId` + `syncStatus` (composite, for pending syncs)

### Room Database Schema (Local Cache)

#### Entity: WorkoutPlanEntity
```kotlin
@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey val planId: String,
    val userId: String,
    val name: String,
    val description: String?,
    val trainingDaysJson: String, // JSON serializado
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus,
    val lastSyncAttempt: Long?
)

enum class SyncStatus {
    SYNCED,
    PENDING_SYNC,
    SYNC_FAILED
}
```

#### Entity: TrainingLogEntity
```kotlin
@Entity(tableName = "training_logs")
data class TrainingLogEntity(
    @PrimaryKey val logId: String,
    val userId: String,
    val workoutPlanId: String?,
    val workoutDayName: String,
    val timestamp: Long,
    val origin: String,
    val exercisesJson: String, // JSON serializado
    val totalVolume: Int,
    val totalCalories: Int?,
    val duration: Int,
    val syncStatus: SyncStatus,
    val lastSyncAttempt: Long?
)
```

#### Entity: ExerciseEntity
```kotlin
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val exerciseId: String,
    val name: String,
    val muscleGroup: String,
    val secondaryMusclesJson: String,
    val instructions: String,
    val mediaUrl: String,
    val mediaType: String,
    val difficulty: String,
    val equipmentJson: String,
    val cachedAt: Long
)
```

### Domain Models (Kotlin)

#### WorkoutPlan
```kotlin
data class WorkoutPlan(
    val planId: String,
    val userId: String,
    val name: String,
    val description: String?,
    val trainingDays: List<TrainingDay>,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class TrainingDay(
    val dayName: String,
    val exercises: List<PlannedExercise>
)

data class PlannedExercise(
    val exerciseId: String,
    val order: Int,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val notes: String?
)
```

#### TrainingLog
```kotlin
data class TrainingLog(
    val logId: String,
    val userId: String,
    val workoutPlanId: String?,
    val workoutDayName: String,
    val timestamp: Instant,
    val origin: String,
    val duration: Int,
    val totalCalories: Int?,
    val exercises: List<ExerciseLog>,
    val totalVolume: Int
)

data class ExerciseLog(
    val exerciseId: String,
    val sets: List<SetLog>,
    val totalVolume: Int,
    val isPersonalRecord: Boolean = false,
    val recordType: RecordType? = null
)

data class SetLog(
    val setNumber: Int,
    val reps: Int,
    val weight: Double,
    val restSeconds: Int,
    val heartRate: Int?,
    val completedAt: Instant
)

enum class RecordType {
    MAX_WEIGHT,
    MAX_VOLUME
}
```

#### Exercise
```kotlin
data class Exercise(
    val exerciseId: String,
    val name: String,
    val muscleGroup: MuscleGroup,
    val secondaryMuscles: List<MuscleGroup>,
    val instructions: String,
    val mediaUrl: String,
    val mediaType: MediaType,
    val difficulty: Difficulty,
    val equipment: List<Equipment>
)

enum class MuscleGroup {
    CHEST, BACK, SHOULDERS, BICEPS, TRICEPS,
    LEGS, QUADRICEPS, HAMSTRINGS, GLUTES, CALVES,
    ABS, CORE
}

enum class MediaType {
    GIF, VIDEO
}

enum class Difficulty {
    BEGINNER, INTERMEDIATE, ADVANCED
}

enum class Equipment {
    BARBELL, DUMBBELL, MACHINE, CABLE,
    BODYWEIGHT, BENCH, PULL_UP_BAR
}
```

#### PersonalRecord
```kotlin
data class PersonalRecord(
    val exerciseId: String,
    val maxWeight: Double,
    val maxVolume: Int,
    val achievedAt: Instant
)
```

### Data Validation Rules

#### Training Log Validation (Cloud Functions)
```typescript
function validateTrainingLog(log: any): ValidationResult {
    const errors: string[] = [];
    
    // Required fields
    if (!log.timestamp) errors.push("timestamp is required");
    if (!log.exercises || !Array.isArray(log.exercises)) {
        errors.push("exercises array is required");
    }
    
    // Timestamp validation
    const timestamp = new Date(log.timestamp);
    const now = new Date();
    const maxFutureTime = new Date(now.getTime() + 24 * 60 * 60 * 1000);
    if (timestamp > maxFutureTime) {
        errors.push("timestamp cannot be more than 24 hours in the future");
    }
    
    // Exercise validation
    log.exercises?.forEach((exercise: any, index: number) => {
        if (!exercise.exerciseId) {
            errors.push(`exercises[${index}].exerciseId is required`);
        }
        
        exercise.sets?.forEach((set: any, setIndex: number) => {
            if (typeof set.weight !== 'number' || set.weight <= 0) {
                errors.push(`exercises[${index}].sets[${setIndex}].weight must be positive`);
            }
            if (!Number.isInteger(set.reps) || set.reps <= 0) {
                errors.push(`exercises[${index}].sets[${setIndex}].reps must be positive integer`);
            }
            if (!Number.isInteger(set.restSeconds) || set.restSeconds < 0) {
                errors.push(`exercises[${index}].sets[${setIndex}].restSeconds must be non-negative integer`);
            }
        });
    });
    
    return {
        isValid: errors.length === 0,
        errors
    };
}
```

### Firestore Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // Helper functions
    function isAuthenticated() {
      return request.auth != null;
    }
    
    function isOwner(userId) {
      return isAuthenticated() && request.auth.uid == userId;
    }
    
    // Users collection
    match /users/{userId} {
      allow read: if isOwner(userId);
      allow create: if isOwner(userId);
      allow update: if isOwner(userId);
      allow delete: if false; // Prevent deletion
    }
    
    // Exercises collection (read-only for all authenticated users)
    match /exercises/{exerciseId} {
      allow read: if isAuthenticated();
      allow write: if false; // Only admins via Cloud Functions
    }
    
    // Workout plans collection
    match /workout_plans/{planId} {
      allow read: if isOwner(resource.data.userId);
      allow create: if isOwner(request.resource.data.userId);
      allow update: if isOwner(resource.data.userId);
      allow delete: if isOwner(resource.data.userId);
    }
    
    // Training logs collection
    match /training_logs/{logId} {
      allow read: if isOwner(resource.data.userId);
      allow create: if isOwner(request.resource.data.userId)
                    && request.resource.data.timestamp is timestamp;
      allow update: if false; // Logs are immutable
      allow delete: if isOwner(resource.data.userId);
    }
  }
}
```


## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property Reflection

After analyzing all acceptance criteria, I identified several areas where properties can be consolidated to avoid redundancy:


**Consolidation Analysis:**

1. **Rest Timer Properties (4.3, 5.3, 13.1)**: All three criteria test that marking a set complete starts a rest timer. These can be combined into one property that applies to both mobile and watch.

2. **Haptic Feedback Properties (5.4, 13.2, 13.3, 13.4)**: Multiple criteria about haptic feedback can be consolidated into properties about feedback triggers rather than separate properties per device.

3. **Data Caching Properties (2.5, 12.1, 12.2)**: All test that data is cached locally. Can be combined into a general caching property.

4. **Training Log Persistence (4.6, 5.6, 6.4)**: All test that completed sessions are saved. Can be consolidated into one property about persistence.

5. **Heart Rate Data Flow (9.2, 9.3, 9.4, 9.5)**: These form a pipeline that can be tested with fewer properties focusing on data completeness.

6. **Calorie Tracking (10.2, 10.3, 10.4)**: Similar pipeline that can be consolidated.

7. **Validation Properties (20.1-20.6)**: These are all part of the same validation function and can be tested together with property-based testing using invalid input generators.

8. **Personal Record Detection (8.1, 8.2, 8.3, 8.4)**: These form a single feature that can be tested with fewer properties.

9. **UI Rendering Properties**: Many properties test that rendered output contains certain data. These can be consolidated by testing data completeness rather than individual fields.


### Core Properties

#### Property 1: Workout Plan Round-Trip Serialization

*For any* valid WorkoutPlan object, serializing to JSON then deserializing back to an object SHALL produce an equivalent WorkoutPlan with the same data.

**Validates: Requirements 19.4**

**Rationale**: This is a critical round-trip property for data integrity. If serialization/deserialization is not bijective, users could lose workout plan data during import/export operations.


#### Property 2: Training Log Validation Rejects Invalid Data

*For any* TrainingLog with invalid data (negative weights, non-positive reps, negative rest times, or future timestamps beyond 24 hours), the Cloud Functions validation SHALL reject it and return a descriptive error message.

**Validates: Requirements 20.1, 20.2, 20.3, 20.4, 20.5, 20.6**

**Rationale**: This property ensures data integrity at the backend. Invalid training data could corrupt statistics, break charts, or cause calculation errors. Property-based testing with generators for invalid inputs will thoroughly test all validation rules.


#### Property 3: Training Log Validation Accepts Valid Data

*For any* TrainingLog with valid data (positive weights, positive integer reps, non-negative rest times, and reasonable timestamps), the Cloud Functions validation SHALL accept it and save it to Firestore.

**Validates: Requirements 6.3, 6.4, 20.1**

**Rationale**: Complement to Property 2. Ensures that valid training data is never incorrectly rejected, which would frustrate users and lose workout data.


#### Property 4: Volume Calculation Correctness

*For any* TrainingLog, the calculated total volume SHALL equal the sum of (weight × reps) across all sets in all exercises.

**Validates: Requirements 7.3**

**Rationale**: Volume is a critical metric for tracking progress. Incorrect calculations would mislead users about their training progress and invalidate trend charts.


#### Property 5: Personal Record Detection for Max Weight

*For any* exercise in a TrainingLog, if the weight used exceeds all previous weights for that exercise in the user's history, the system SHALL mark it as a PersonalRecord with recordType MAX_WEIGHT.

**Validates: Requirements 8.1, 8.2, 8.4**

**Rationale**: Accurate record detection motivates users and tracks genuine progress. False positives or negatives would undermine the feature's value.


#### Property 6: Personal Record Detection for Max Volume

*For any* exercise in a TrainingLog, if the total volume (sum of weight × reps for all sets) exceeds all previous volumes for that exercise in the user's history, the system SHALL mark it as a PersonalRecord with recordType MAX_VOLUME.

**Validates: Requirements 8.1, 8.3, 8.4**

**Rationale**: Volume records are distinct from weight records and equally important for tracking progress. A user might lift less weight but do more reps, achieving a volume record.


#### Property 7: Rest Timer Auto-Start on Set Completion

*For any* training session (mobile or watch), when a set is marked complete, the system SHALL automatically start a Rest_Timer with the configured rest duration for that exercise.

**Validates: Requirements 4.3, 5.3, 13.1**

**Rationale**: Automatic timer start is essential for the "zero friction" experience. Users shouldn't need to manually start timers between sets during intense workouts.


#### Property 8: Haptic Feedback on Timer Expiration

*For any* Rest_Timer that reaches zero, the system SHALL trigger haptic feedback (vibration) to notify the user.

**Validates: Requirements 5.4, 13.2, 13.4**

**Rationale**: Haptic feedback allows users to know when rest is complete without looking at the screen, which is critical during workouts when hands may be occupied or eyes focused elsewhere.


#### Property 9: Exercise Library Data Completeness

*For any* exercise in the exercise library, it SHALL have all required fields: exerciseId, name, muscleGroup, instructions, mediaUrl, and mediaType (either GIF or VIDEO).

**Validates: Requirements 2.1, 2.4**

**Rationale**: Incomplete exercise data would break the UI and prevent users from learning proper form. All exercises must have complete information.


#### Property 10: Exercise Filter by Muscle Group Correctness

*For any* muscle group filter applied to the exercise library, all returned exercises SHALL have that muscle group as either their primary muscleGroup or in their secondaryMuscles list.

**Validates: Requirements 2.2**

**Rationale**: Incorrect filtering would show irrelevant exercises, wasting users' time and potentially leading to incorrect workout planning.


#### Property 11: Workout Plan Creation Preserves Exercise Count

*For any* workout plan created with N exercises across all training days, the saved plan SHALL contain exactly N exercises when retrieved.

**Validates: Requirements 3.1, 3.2, 3.4**

**Rationale**: This is an invariant property ensuring that creating and saving a plan doesn't lose exercises. Loss of exercises would corrupt workout plans.


#### Property 12: Workout Plan Configuration Persistence

*For any* exercise in a workout plan configured with specific sets, reps, and rest time values, those exact values SHALL be present when the plan is retrieved from storage.

**Validates: Requirements 3.3, 3.4**

**Rationale**: Configuration data must persist accurately. If sets/reps/rest times change unexpectedly, users would perform incorrect workouts.


#### Property 13: Training History Chronological Ordering

*For any* user's training history, the logs SHALL be ordered by timestamp in descending order (most recent first).

**Validates: Requirements 7.1**

**Rationale**: Users expect to see their most recent workouts first. Incorrect ordering would make the history confusing and hard to navigate.


#### Property 14: Training Log Data Completeness in History

*For any* TrainingLog displayed in history, it SHALL contain all exercise details including exerciseId, sets with reps, weight, and rest time for each set.

**Validates: Requirements 7.2**

**Rationale**: Incomplete history data would prevent users from reviewing their past performance and tracking progress accurately.


#### Property 15: Offline Cache Synchronization Completeness

*For any* TrainingLog cached locally while offline, when connectivity is restored, the log SHALL be synchronized to Firestore and marked as SYNCED.

**Validates: Requirements 6.5, 12.5**

**Rationale**: This ensures no workout data is lost due to connectivity issues. All offline workouts must eventually reach the cloud for backup and cross-device access.


#### Property 16: Conflict Resolution by Timestamp

*For any* two conflicting versions of the same data (same ID, different content), the system SHALL keep the version with the most recent timestamp.

**Validates: Requirements 12.6**

**Rationale**: Last-write-wins is a simple and predictable conflict resolution strategy. Users expect their most recent changes to be preserved.


#### Property 17: Heart Rate Data Inclusion in Training Logs

*For any* TrainingLog created on the Watch_App with heart rate monitoring active, each set SHALL include heart rate data, and the log SHALL contain this data when synchronized.

**Validates: Requirements 9.2, 9.3, 9.4, 9.5**

**Rationale**: Heart rate is valuable training data. If it's collected but not saved or displayed, the feature provides no value to users.


#### Property 18: Calorie Data Inclusion in Training Logs

*For any* TrainingLog created on the Watch_App with calorie tracking active, the log SHALL include totalCalories field with a non-negative value.

**Validates: Requirements 10.2, 10.3, 10.4**

**Rationale**: Similar to heart rate, calorie data must be captured and persisted to provide value. Missing calorie data would break calorie tracking features.


#### Property 19: Weekly and Monthly Aggregation Correctness

*For any* time period (week or month), the aggregated statistics (total volume, total calories, workout count) SHALL equal the sum of those values from all TrainingLogs within that period.

**Validates: Requirements 7.4, 10.5, 14.3**

**Rationale**: Aggregation errors would show incorrect progress trends, misleading users about their training consistency and intensity.


#### Property 20: Authentication Required for Data Access

*For any* API request to Cloud Functions or Firestore query, if the request lacks a valid Firebase Auth token, the system SHALL reject it with an authentication error.

**Validates: Requirements 6.2, 17.1, 17.2**

**Rationale**: Security is critical. Unauthenticated access could expose user data or allow unauthorized modifications.


#### Property 21: User Data Isolation

*For any* authenticated user, they SHALL only be able to read and write their own data (userId matches auth.uid), and SHALL NOT be able to access other users' data.

**Validates: Requirements 17.3**

**Rationale**: Data isolation prevents privacy breaches. Users must never see or modify other users' workout data.


#### Property 22: Auto-Detect Notification Rate Limiting

*For any* one-hour time window, the Watch_App SHALL trigger at most one auto-detect workout notification, regardless of how many movement patterns are detected.

**Validates: Requirements 15.4**

**Rationale**: Excessive notifications would annoy users and drain battery. Rate limiting ensures the feature is helpful rather than intrusive.


#### Property 23: Touch Target Minimum Size on Watch

*For any* interactive UI element in the Watch_App during training mode, the touch target SHALL be at least 48dp in both width and height.

**Validates: Requirements 18.3**

**Rationale**: Small touch targets are difficult to tap during workouts when hands may be shaky or sweaty. Minimum size ensures usability.


#### Property 24: Touch Target Minimum Size on Mobile

*For any* interactive UI element in the Mobile_App during training mode, the touch target SHALL be at least 56dp in both width and height.

**Validates: Requirements 18.4**

**Rationale**: Similar to watch, but mobile allows slightly larger targets for even better usability during intense workouts.


#### Property 25: Workout Plan Deletion Removes from User's List

*For any* workout plan that is deleted, it SHALL no longer appear in the user's list of workout plans after the deletion completes.

**Validates: Requirements 3.6**

**Rationale**: This is a basic CRUD invariant. Deleted items must not persist in the UI or database.


#### Property 26: In-Session Weight/Reps Adjustment Persistence

*For any* adjustment made to weight or reps during a training session, the adjusted values SHALL be reflected in the final TrainingLog when the session is completed.

**Validates: Requirements 4.5, 5.5**

**Rationale**: Users frequently adjust weights during workouts. If adjustments aren't saved, the training log would be inaccurate.


#### Property 27: Notification Content Completeness

*For any* push notification sent for workout reminders or personal records, the notification SHALL include descriptive text and relevant action buttons.

**Validates: Requirements 15.3, 16.4**

**Rationale**: Incomplete notifications provide poor user experience. Notifications must be actionable and informative.


## Error Handling

### Error Categories

#### 1. Network Errors

**Scenarios:**
- Watch loses Wi-Fi/LTE connection during sync
- Mobile app loses internet connection
- Cloud Functions timeout
- Firestore connection failures

**Handling Strategy:**
- All network operations use exponential backoff retry (3 attempts max)
- Failed operations are cached locally with PENDING_SYNC status
- Background sync service retries pending operations every 15 minutes
- User sees "Offline Mode" indicator in UI
- No data loss: all operations saved locally first

**Implementation:**
```kotlin
sealed class NetworkResult<T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error<T>(val exception: Exception, val canRetry: Boolean) : NetworkResult<T>()
    data class Cached<T>(val data: T) : NetworkResult<T>()
}

class RetryPolicy {
    fun shouldRetry(attempt: Int, exception: Exception): Boolean {
        return attempt < 3 && exception is RetryableException
    }
    
    fun getBackoffDelay(attempt: Int): Long {
        return (2.0.pow(attempt) * 1000).toLong() // 1s, 2s, 4s
    }
}
```


#### 2. Validation Errors

**Scenarios:**
- User enters invalid data (negative weight, zero reps)
- Malformed JSON during import
- Missing required fields in TrainingLog
- Invalid timestamps

**Handling Strategy:**
- Client-side validation before submission
- Server-side validation as final gate
- Descriptive error messages shown to user
- Form fields highlighted with specific errors
- No partial saves: atomic operations only

**Error Response Format:**
```typescript
interface ValidationError {
    field: string;
    message: string;
    code: string;
}

interface ErrorResponse {
    success: false;
    errors: ValidationError[];
    timestamp: string;
}
```


#### 3. Authentication Errors

**Scenarios:**
- Expired auth tokens
- User logged out on another device
- Invalid credentials
- Token refresh failures

**Handling Strategy:**
- Automatic token refresh before expiration
- If refresh fails, redirect to login screen
- Preserve user's current state (draft workout)
- Clear sensitive data on logout
- Show clear "Session Expired" message

**Implementation:**
```kotlin
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authManager.getValidToken() // Auto-refreshes if needed
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
        
        val response = chain.proceed(request)
        
        if (response.code == 401) {
            // Token invalid, trigger re-authentication
            authManager.signOut()
            navigationManager.navigateToLogin()
        }
        
        return response
    }
}
```


#### 4. Data Synchronization Conflicts

**Scenarios:**
- Same workout plan edited on mobile and watch
- Training log created offline on both devices
- Concurrent updates to user profile

**Handling Strategy:**
- Last-write-wins based on server timestamp
- Firestore transactions for critical updates
- Optimistic UI updates with rollback on conflict
- Conflict logs for debugging
- User notified only if data loss possible

**Conflict Resolution:**
```kotlin
suspend fun resolveConflict(local: WorkoutPlan, remote: WorkoutPlan): WorkoutPlan {
    return if (remote.updatedAt > local.updatedAt) {
        // Remote is newer, use it
        roomDao.update(remote)
        remote
    } else {
        // Local is newer, push to Firestore
        firestore.update(local)
        local
    }
}
```


#### 5. Health Services API Errors

**Scenarios:**
- Heart rate sensor unavailable
- Permission denied for health data
- Sensor reading failures
- Unsupported device

**Handling Strategy:**
- Graceful degradation: workout continues without HR data
- Request permissions at appropriate times
- Show clear message if sensor unavailable
- Don't block core functionality
- Log sensor errors for debugging

**Implementation:**
```kotlin
class HeartRateMonitor {
    suspend fun startMonitoring(): Result<Flow<Int>> {
        return try {
            if (!hasPermission()) {
                return Result.failure(PermissionDeniedException())
            }
            
            val client = HealthServicesClient.getClient(context)
            val capabilities = client.getCapabilities()
            
            if (!capabilities.supportedDataTypes.contains(DataType.HEART_RATE_BPM)) {
                return Result.failure(UnsupportedFeatureException())
            }
            
            Result.success(client.heartRateFlow())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```


#### 6. Storage Errors

**Scenarios:**
- Device storage full
- Room database corruption
- Firestore quota exceeded
- Cache eviction failures

**Handling Strategy:**
- Check available storage before large operations
- Implement cache size limits with LRU eviction
- Periodic database integrity checks
- Backup critical data to Firestore immediately
- Show storage warning to user

**Cache Management:**
```kotlin
class CacheManager {
    private val maxCacheSize = 100 * 1024 * 1024 // 100MB
    
    suspend fun ensureSpace(requiredBytes: Long) {
        val currentSize = calculateCacheSize()
        if (currentSize + requiredBytes > maxCacheSize) {
            evictLRUItems(requiredBytes)
        }
    }
    
    private suspend fun evictLRUItems(bytesNeeded: Long) {
        // Remove oldest cached exercises and media first
        // Never evict pending sync data
        val items = roomDao.getCachedItemsByLastAccess()
        var freedBytes = 0L
        
        for (item in items) {
            if (item.syncStatus != SyncStatus.PENDING_SYNC) {
                roomDao.delete(item)
                freedBytes += item.sizeBytes
                if (freedBytes >= bytesNeeded) break
            }
        }
    }
}
```


### Error Logging and Monitoring

**Logging Strategy:**
- Use Firebase Crashlytics for crash reporting
- Log all network errors with context
- Track sync failures and retry attempts
- Monitor validation error frequency
- Never log sensitive user data (weights, personal info)

**Metrics to Track:**
- Sync success rate
- Average retry attempts before success
- Offline mode duration
- Validation error types and frequency
- API response times


## Testing Strategy

### Dual Testing Approach

The V-Trainer system requires both unit tests and property-based tests for comprehensive coverage:

- **Unit Tests**: Verify specific examples, edge cases, error conditions, and integration points
- **Property-Based Tests**: Verify universal properties across all inputs using randomized test data

Both approaches are complementary and necessary. Unit tests catch concrete bugs in specific scenarios, while property-based tests verify general correctness across a wide input space.

### Property-Based Testing Framework

**Framework Selection:**
- **Kotlin/JVM**: Kotest Property Testing (built on top of Kotest framework)
- **TypeScript/Node.js**: fast-check (mature PBT library for JavaScript/TypeScript)

**Configuration:**
- Minimum 100 iterations per property test (due to randomization)
- Each property test must reference its design document property
- Tag format: `Feature: v-trainer, Property {number}: {property_text}`


### Property-Based Test Examples

#### Example 1: Workout Plan Serialization (Property 1)

```kotlin
class WorkoutPlanSerializationTest : FunSpec({
    test("Feature: v-trainer, Property 1: Workout Plan Round-Trip Serialization") {
        checkAll(100, Arb.workoutPlan()) { plan ->
            val json = workoutPlanSerializer.toJson(plan)
            val deserialized = workoutPlanSerializer.fromJson(json)
            
            deserialized shouldBe plan
        }
    }
})

// Custom Arbitrary generator for WorkoutPlan
fun Arb.Companion.workoutPlan(): Arb<WorkoutPlan> = arbitrary {
    WorkoutPlan(
        planId = Arb.uuid().bind(),
        userId = Arb.uuid().bind(),
        name = Arb.string(5..50).bind(),
        description = Arb.string(0..200).orNull().bind(),
        trainingDays = Arb.list(Arb.trainingDay(), 1..7).bind(),
        createdAt = Arb.instant().bind(),
        updatedAt = Arb.instant().bind()
    )
}
```


#### Example 2: Training Log Validation (Property 2)

```typescript
// Feature: v-trainer, Property 2: Training Log Validation Rejects Invalid Data
describe('Training Log Validation', () => {
  it('should reject logs with invalid data', () => {
    fc.assert(
      fc.property(
        fc.record({
          timestamp: fc.date({ max: new Date(Date.now() + 48 * 60 * 60 * 1000) }), // Future dates
          exercises: fc.array(fc.record({
            exerciseId: fc.string(),
            sets: fc.array(fc.record({
              reps: fc.integer({ max: 0 }), // Invalid: non-positive
              weight: fc.double({ max: 0 }), // Invalid: non-positive
              restSeconds: fc.integer({ max: -1 }) // Invalid: negative
            }))
          }))
        }),
        (invalidLog) => {
          const result = validateTrainingLog(invalidLog);
          expect(result.isValid).toBe(false);
          expect(result.errors.length).toBeGreaterThan(0);
        }
      ),
      { numRuns: 100 }
    );
  });
});
```


#### Example 3: Volume Calculation (Property 4)

```kotlin
class VolumeCalculationTest : FunSpec({
    test("Feature: v-trainer, Property 4: Volume Calculation Correctness") {
        checkAll(100, Arb.trainingLog()) { log ->
            val calculatedVolume = volumeCalculator.calculate(log)
            
            val expectedVolume = log.exercises.sumOf { exercise ->
                exercise.sets.sumOf { set ->
                    (set.weight * set.reps).toInt()
                }
            }
            
            calculatedVolume shouldBe expectedVolume
        }
    }
})
```


### Unit Testing Strategy

#### Mobile App (Kotlin)

**Test Structure:**
```
app/src/test/
├── viewmodels/
│   ├── DashboardViewModelTest.kt
│   ├── WorkoutExecutionViewModelTest.kt
│   └── WorkoutPlanViewModelTest.kt
├── repositories/
│   ├── WorkoutRepositoryTest.kt
│   ├── TrainingLogRepositoryTest.kt
│   └── ExerciseRepositoryTest.kt
├── usecases/
│   ├── CalculateVolumeUseCaseTest.kt
│   ├── DetectPersonalRecordUseCaseTest.kt
│   └── SyncTrainingLogUseCaseTest.kt
└── utils/
    ├── DateUtilsTest.kt
    └── ValidationUtilsTest.kt
```

**Key Test Cases:**
- ViewModel state transitions during workout execution
- Repository offline-first behavior (Firestore unavailable)
- Sync retry logic with exponential backoff
- Conflict resolution by timestamp
- Error handling for each error category


#### Watch App (Kotlin)

**Test Structure:**
```
wear/src/test/
├── viewmodels/
│   └── WatchWorkoutViewModelTest.kt
├── services/
│   ├── HeartRateMonitorTest.kt
│   ├── CalorieTrackerTest.kt
│   └── AutoDetectServiceTest.kt
├── sync/
│   └── DirectSyncManagerTest.kt
└── tiles/
    └── WorkoutTileTest.kt
```

**Key Test Cases:**
- Heart rate monitoring integration (mocked Health Services API)
- Direct sync to Cloud Functions with retry
- Haptic feedback triggers
- Auto-detect rate limiting (max 1 per hour)
- Tile update after workout completion


#### Cloud Functions (TypeScript)

**Test Structure:**
```
functions/src/test/
├── syncWorkout.test.ts
├── calculateProgress.test.ts
├── sendWorkoutReminder.test.ts
├── validation/
│   └── trainingLogValidator.test.ts
└── utils/
    ├── personalRecordDetector.test.ts
    └── volumeCalculator.test.ts
```

**Key Test Cases:**
- Authentication token validation
- Training log validation (all validation rules)
- Personal record detection (max weight and volume)
- Firestore write operations
- Error responses with descriptive messages
- Scheduled reminder function execution


### Integration Testing

**Scenarios to Test:**

1. **End-to-End Workout Flow (Mobile)**
   - User starts workout → completes sets → finishes session → data appears in history
   - Verify: TrainingLog in Firestore, Room cache updated, UI reflects completion

2. **End-to-End Workout Flow (Watch)**
   - User starts workout on watch → completes sets → finishes session → data syncs to cloud
   - Verify: Direct sync to Cloud Functions, data in Firestore, mobile app shows workout

3. **Offline-to-Online Sync**
   - User completes workout offline → connectivity restored → data syncs
   - Verify: Pending logs synced, sync status updated, no data loss

4. **Cross-Device Sync**
   - User creates workout plan on mobile → plan appears on watch
   - User completes workout on watch → history appears on mobile

5. **Personal Record Detection**
   - User lifts new max weight → Cloud Function detects record → notification sent
   - Verify: PersonalRecord in user document, push notification received


### UI/Usability Testing

**Test Scenarios:**

1. **Touch Target Sizes**
   - Measure all interactive elements in training mode
   - Verify: Mobile ≥56dp, Watch ≥48dp
   - Test with accessibility scanner tools

2. **Haptic Feedback**
   - Complete set → short vibration
   - Rest timer expires → long vibration (≥1s)
   - Verify feedback is distinct and noticeable

3. **Responsive Layouts**
   - Test mobile on 5", 6", 7" screens
   - Test watch on round and square faces
   - Verify: No clipping, proper spacing, readable text

4. **Offline Mode Indicator**
   - Disconnect network → verify offline indicator appears
   - Reconnect → verify indicator disappears
   - Test sync status messages

5. **Error Messages**
   - Trigger validation errors → verify clear, actionable messages
   - Test network errors → verify retry options
   - Test auth errors → verify redirect to login


### Performance Testing

**Metrics to Measure:**

1. **Sync Performance**
   - Time to sync TrainingLog from watch to Firestore
   - Target: <2 seconds on good connection
   - Test with varying network conditions

2. **UI Responsiveness**
   - Time from "Complete Set" tap to rest timer start
   - Target: <100ms (imperceptible delay)
   - Test on mid-range and low-end devices

3. **Database Query Performance**
   - Time to load training history (100 logs)
   - Target: <500ms
   - Test with large datasets

4. **Battery Impact**
   - Watch battery drain during 1-hour workout with HR monitoring
   - Target: <10% battery consumption
   - Test with continuous sensor usage

5. **Cache Performance**
   - Time to load workout plan from Room cache
   - Target: <50ms
   - Test with various cache sizes

### Test Coverage Goals

- **Unit Tests**: ≥80% code coverage
- **Property-Based Tests**: All 27 correctness properties implemented
- **Integration Tests**: All critical user flows covered
- **UI Tests**: All screens and major interactions tested

### Continuous Integration

- Run unit tests on every commit
- Run property-based tests on every PR
- Run integration tests nightly
- Performance tests weekly
- Manual usability testing before releases

