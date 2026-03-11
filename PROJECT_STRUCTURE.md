# V-Trainer - Estrutura do Projeto

## Visão Geral

Este documento descreve a estrutura completa do projeto V-Trainer após a configuração inicial (Task 1).

## Estrutura de Diretórios

```
v-trainer/
│
├── .firebase/                      # Firebase cache (gitignored)
├── .git/                          # Git repository
├── .github/                       # GitHub Actions (opcional)
├── .gradle/                       # Gradle cache (gitignored)
├── .idea/                         # Android Studio config (gitignored)
├── .kiro/                         # Kiro specs
│   └── specs/
│       └── v-trainer/
│           ├── requirements.md
│           ├── design.md
│           └── tasks.md
│
├── app/                           # Android Mobile App
│   ├── build/                     # Build output (gitignored)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/vtrainer/app/
│   │   │   │   ├── VTrainerApplication.kt
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── services/
│   │   │   │       └── VTrainerMessagingService.kt
│   │   │   ├── res/
│   │   │   │   └── values/
│   │   │   │       ├── strings.xml
│   │   │   │       └── themes.xml
│   │   │   └── AndroidManifest.xml
│   │   └── test/                  # Unit tests
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── google-services.json       # Firebase config (add your own)
│
├── wear/                          # Wear OS App
│   ├── build/                     # Build output (gitignored)
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/com/vtrainer/wear/
│   │   │   │   ├── VTrainerWearApplication.kt
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── tiles/
│   │   │   │   │   └── WorkoutTileService.kt
│   │   │   │   └── services/
│   │   │   │       ├── WorkoutAutoDetectService.kt
│   │   │   │       └── VTrainerWearMessagingService.kt
│   │   │   ├── res/
│   │   │   │   ├── drawable/
│   │   │   │   │   └── tile_preview.xml
│   │   │   │   └── values/
│   │   │   │       └── strings.xml
│   │   │   └── AndroidManifest.xml
│   │   └── test/                  # Unit tests
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── google-services.json       # Firebase config (add your own)
│
├── functions/                     # Firebase Cloud Functions
│   ├── lib/                       # Compiled output (gitignored)
│   ├── node_modules/              # Dependencies (gitignored)
│   ├── src/
│   │   ├── index.ts               # Main entry point
│   │   ├── syncWorkout.ts         # Workout sync function
│   │   ├── calculateProgress.ts   # Progress calculation function
│   │   └── sendWorkoutReminder.ts # Reminder function
│   ├── .eslintrc.js
│   ├── package.json
│   └── tsconfig.json
│
├── docs/                          # Documentation
│   └── plan.nd
│
├── .firebaserc                    # Firebase project config
├── .gitignore
├── build.gradle.kts               # Root Gradle config
├── firebase.json                  # Firebase services config
├── firestore.indexes.json         # Firestore indexes
├── firestore.rules                # Firestore security rules
├── gradle.properties              # Gradle properties
├── PROJECT_STRUCTURE.md           # This file
├── README.md                      # Project overview
├── settings.gradle.kts            # Gradle settings
├── SETUP.md                       # Setup guide
└── storage.rules                  # Storage security rules
```

## Componentes Principais

### 1. Firebase Configuration

#### firebase.json
Configura todos os serviços Firebase:
- Firestore (rules e indexes)
- Cloud Functions
- Cloud Storage
- Emulators

#### .firebaserc
Define o projeto Firebase padrão: `v-trainer-project`

#### firestore.rules
Regras de segurança do Firestore:
- Autenticação obrigatória
- Isolamento de dados por usuário
- Exercícios read-only
- Training logs imutáveis

#### firestore.indexes.json
Índices compostos para queries otimizadas:
- `training_logs`: userId + timestamp
- `training_logs`: userId + syncStatus
- `workout_plans`: userId + updatedAt
- `exercises`: muscleGroup + name

#### storage.rules
Regras de segurança do Storage:
- Exercícios: read-only
- Perfis de usuário: read/write próprio

### 2. Android Mobile App (app/)

#### Tecnologias
- **Kotlin** 1.9.20
- **Jetpack Compose** (Material 3)
- **Firebase SDK** (Auth, Firestore, Storage, Messaging)
- **Room Database** 2.6.1
- **Coroutines & Flow**
- **Coil** (imagens/GIFs)
- **Kotest** (property-based testing)

#### Dependências Principais
```kotlin
// Compose
androidx.compose:compose-bom:2023.10.01
androidx.compose.material3:material3

// Firebase
com.google.firebase:firebase-bom:32.6.0
firebase-auth-ktx, firebase-firestore-ktx, etc.

// Room
androidx.room:room-runtime:2.6.1
androidx.room:room-ktx:2.6.1

// Testing
io.kotest:kotest-property:5.8.0
```

#### Arquivos Criados
- `VTrainerApplication.kt`: Application class
- `MainActivity.kt`: Main activity com Compose
- `VTrainerMessagingService.kt`: FCM service
- `AndroidManifest.xml`: Permissões e componentes
- `build.gradle.kts`: Dependências e configuração

### 3. Wear OS App (wear/)

#### Tecnologias
- **Kotlin** 1.9.20
- **Compose for Wear OS**
- **Health Services API** 1.0.0-beta03
- **Wear Tiles** 1.2.0
- **Firebase SDK**
- **Room Database**

#### Dependências Principais
```kotlin
// Wear OS
androidx.wear:wear:1.3.0
androidx.wear.compose:compose-material:1.2.1

// Health Services
androidx.health:health-services-client:1.0.0-beta03

// Tiles
androidx.wear.tiles:tiles:1.2.0

// Firebase (mesmo do mobile)
```

#### Arquivos Criados
- `VTrainerWearApplication.kt`: Application class
- `MainActivity.kt`: Main activity com Wear Compose
- `WorkoutTileService.kt`: Wear OS Tile
- `WorkoutAutoDetectService.kt`: Auto-detect service
- `VTrainerWearMessagingService.kt`: FCM service
- `AndroidManifest.xml`: Permissões Wear OS específicas

### 4. Cloud Functions (functions/)

#### Tecnologias
- **TypeScript** 5.2.2
- **Node.js** 18
- **Firebase Admin SDK** 11.11.0
- **Firebase Functions** 4.5.0
- **fast-check** (property-based testing)

#### Functions Implementadas

##### syncWorkout
- **Trigger**: HTTPS Callable
- **Função**: Recebe training logs do watch/mobile
- **Validação**: Valida dados antes de salvar
- **Retorno**: Success/error com logId

##### calculateProgress
- **Trigger**: Firestore onCreate (training_logs)
- **Função**: Detecta recordes pessoais
- **Ações**: 
  - Compara com histórico
  - Atualiza user document
  - Envia notificação push

##### sendWorkoutReminder
- **Trigger**: Scheduled (daily 08:00)
- **Função**: Envia lembretes de treino
- **Ações**: Query usuários com reminders enabled

#### Arquivos Criados
- `index.ts`: Exports das functions
- `syncWorkout.ts`: Sync logic + validation
- `calculateProgress.ts`: Record detection
- `sendWorkoutReminder.ts`: Scheduled reminders
- `package.json`: Dependencies
- `tsconfig.json`: TypeScript config

## Configuração Necessária

### Antes de Executar

1. **Firebase Project**
   - Criar projeto no Firebase Console
   - Ativar Authentication, Firestore, Functions, Storage, Messaging
   - Baixar `google-services.json` para app/ e wear/

2. **Cloud Functions**
   ```bash
   cd functions
   npm install
   npm run build
   firebase deploy --only functions
   ```

3. **Firestore Rules & Indexes**
   ```bash
   firebase deploy --only firestore
   ```

4. **Android Studio**
   - Abrir projeto
   - Sync Gradle
   - Executar app ou wear

## Próximas Tarefas

Conforme `tasks.md`, as próximas implementações incluem:

- **Task 2**: Data models e Room database
- **Task 3**: Repository layer com offline-first
- **Task 4**: ViewModels e UI screens (mobile)
- **Task 5**: Wear OS UI e Health Services
- **Task 6**: Testing (unit + property-based)

## Recursos

- **Firebase Console**: https://console.firebase.google.com/
- **Android Studio**: https://developer.android.com/studio
- **Documentação**: Ver README.md e SETUP.md

## Notas Importantes

### Segurança
- ⚠️ Nunca commitar `google-services.json` com credenciais reais
- ⚠️ Usar variáveis de ambiente para secrets
- ✅ Firestore rules já configuradas para produção

### Performance
- ✅ Offline-first architecture
- ✅ Firestore indexes otimizados
- ✅ Room cache para dados críticos

### Testes
- ✅ Kotest configurado (mobile/wear)
- ✅ fast-check configurado (functions)
- ✅ Mínimo 100 iterações por property test

---

**Status**: ✅ Task 1 Completa - Projeto configurado e pronto para desenvolvimento
