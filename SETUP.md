# V-Trainer - Guia de Configuração Completo

Este guia detalha todos os passos necessários para configurar o projeto V-Trainer do zero.

## Pré-requisitos

### Software Necessário

1. **Android Studio** (Hedgehog | 2023.1.1 ou superior)
   - Download: https://developer.android.com/studio

2. **JDK 17**
   - Incluído no Android Studio ou instale separadamente

3. **Node.js** (v18 ou superior)
   - Download: https://nodejs.org/

4. **Firebase CLI**
   ```bash
   npm install -g firebase-tools
   ```

5. **Git**
   - Download: https://git-scm.com/

### Contas Necessárias

1. **Conta Google** para Firebase Console
2. **Conta Google Cloud Platform** (criada automaticamente com Firebase)

---

## Passo 1: Configurar Firebase Project

### 1.1 Criar Projeto no Firebase Console

1. Acesse https://console.firebase.google.com/
2. Clique em "Adicionar projeto"
3. Nome do projeto: `v-trainer-project`
4. Aceite os termos e clique em "Continuar"
5. Desabilite Google Analytics (opcional) ou configure
6. Clique em "Criar projeto"

### 1.2 Ativar Serviços Firebase

#### Authentication
1. No menu lateral, clique em "Authentication"
2. Clique em "Começar"
3. Ative os provedores:
   - **Google**: Clique em "Google" → Ativar → Salvar
   - **Email/Password**: (opcional para testes)

#### Firestore Database
1. No menu lateral, clique em "Firestore Database"
2. Clique em "Criar banco de dados"
3. Selecione "Iniciar no modo de produção"
4. Escolha a localização: `southamerica-east1` (São Paulo)
5. Clique em "Ativar"

#### Cloud Storage
1. No menu lateral, clique em "Storage"
2. Clique em "Começar"
3. Aceite as regras padrão
4. Escolha a localização: `southamerica-east1`
5. Clique em "Concluído"

#### Cloud Messaging
1. No menu lateral, clique em "Cloud Messaging"
2. O serviço já está ativado por padrão

### 1.3 Configurar Android Apps no Firebase

#### App Mobile

1. No Firebase Console, clique no ícone Android (</>) na página inicial
2. Preencha os dados:
   - **Nome do pacote Android**: `com.vtrainer.app`
   - **Apelido do app**: `V-Trainer Mobile`
   - **Certificado de assinatura SHA-1**: (opcional para desenvolvimento)
3. Clique em "Registrar app"
4. **Baixe o arquivo `google-services.json`**
5. Coloque o arquivo em: `app/google-services.json`
6. Clique em "Próxima" até finalizar

#### App Wear OS

1. Clique novamente no ícone Android (</>) para adicionar outro app
2. Preencha os dados:
   - **Nome do pacote Android**: `com.vtrainer.wear`
   - **Apelido do app**: `V-Trainer Wear`
3. Clique em "Registrar app"
4. **Baixe o arquivo `google-services.json`**
5. Coloque o arquivo em: `wear/google-services.json`
6. Clique em "Próxima" até finalizar

---

## Passo 2: Configurar Cloud Functions

### 2.1 Fazer Login no Firebase CLI

```bash
firebase login
```

Isso abrirá o navegador para autenticação.

### 2.2 Inicializar Firebase no Projeto

```bash
# Na raiz do projeto
firebase init
```

Selecione:
- [x] Firestore
- [x] Functions
- [x] Storage
- [x] Emulators

Configurações:
- **Projeto Firebase**: Selecione `v-trainer-project`
- **Firestore rules**: `firestore.rules` (já existe)
- **Firestore indexes**: `firestore.indexes.json` (já existe)
- **Functions language**: TypeScript
- **ESLint**: Yes
- **Install dependencies**: Yes
- **Storage rules**: `storage.rules` (já existe)
- **Emulators**: Selecione Authentication, Functions, Firestore, Storage

### 2.3 Instalar Dependências das Functions

```bash
cd functions
npm install
```

### 2.4 Compilar Functions

```bash
npm run build
```

### 2.5 Testar Localmente (Opcional)

```bash
npm run serve
```

Isso iniciará os emuladores Firebase localmente.

### 2.6 Deploy das Functions

```bash
firebase deploy --only functions
```

---

## Passo 3: Configurar Firestore Security Rules

### 3.1 Deploy das Rules

```bash
firebase deploy --only firestore:rules
```

### 3.2 Deploy dos Indexes

```bash
firebase deploy --only firestore:indexes
```

---

## Passo 4: Configurar Storage Rules

```bash
firebase deploy --only storage
```

---

## Passo 5: Configurar Android Mobile App

### 5.1 Abrir Projeto no Android Studio

1. Abra o Android Studio
2. File → Open
3. Selecione a pasta raiz do projeto
4. Aguarde a sincronização do Gradle

### 5.2 Verificar google-services.json

Certifique-se de que o arquivo `app/google-services.json` está presente e contém suas credenciais reais do Firebase.

### 5.3 Sincronizar Gradle

```bash
./gradlew build
```

Ou no Android Studio: File → Sync Project with Gradle Files

### 5.4 Criar Emulador Android (Opcional)

1. Tools → Device Manager
2. Create Device
3. Selecione um dispositivo (ex: Pixel 6)
4. Selecione uma imagem do sistema (API 34 recomendado)
5. Clique em "Finish"

### 5.5 Executar App Mobile

1. Selecione o módulo `app` na barra de ferramentas
2. Selecione o dispositivo/emulador
3. Clique em Run (▶️)

---

## Passo 6: Configurar Wear OS App

### 6.1 Verificar google-services.json

Certifique-se de que o arquivo `wear/google-services.json` está presente.

### 6.2 Criar Emulador Wear OS

1. Tools → Device Manager
2. Create Device
3. Aba "Wear OS"
4. Selecione "Wear OS Small Round" ou "Wear OS Large Round"
5. Selecione API 33 ou superior
6. Clique em "Finish"

### 6.3 Executar App Wear OS

1. Selecione o módulo `wear` na barra de ferramentas
2. Selecione o emulador Wear OS
3. Clique em Run (▶️)

---

## Passo 7: Configurar Permissões Health Services (Wear OS)

### 7.1 Ativar Permissões no Emulador

No emulador Wear OS:
1. Settings → Apps → V-Trainer
2. Permissions → Body sensors → Allow
3. Permissions → Physical activity → Allow

### 7.2 Testar Health Services

As APIs de Health Services requerem um dispositivo físico ou emulador configurado com Google Play Services.

---

## Passo 8: Popular Banco de Dados com Exercícios

### 8.1 Criar Script de Seed (Opcional)

Você pode criar um script Cloud Function para popular a coleção `exercises` com dados iniciais:

```typescript
// functions/src/seedExercises.ts
export const seedExercises = functions.https.onRequest(async (req, res) => {
  const exercises = [
    {
      exerciseId: "supino_reto",
      name: "Supino Reto",
      muscleGroup: "chest",
      secondaryMuscles: ["triceps", "shoulders"],
      instructions: "Deite-se no banco...",
      mediaUrl: "https://example.com/supino_reto.gif",
      mediaType: "gif",
      difficulty: "intermediate",
      equipment: ["barbell", "bench"]
    },
    // Adicione mais exercícios...
  ];
  
  const batch = admin.firestore().batch();
  exercises.forEach(exercise => {
    const ref = admin.firestore().collection("exercises").doc(exercise.exerciseId);
    batch.set(ref, exercise);
  });
  
  await batch.commit();
  res.send("Exercises seeded successfully");
});
```

### 8.2 Executar Seed

Acesse a URL da função após o deploy:
```
https://us-central1-v-trainer-project.cloudfunctions.net/seedExercises
```

---

## Passo 9: Testar Integração Completa

### 9.1 Teste de Autenticação

1. Execute o app mobile
2. Faça login com Google
3. Verifique se o usuário aparece no Firebase Console → Authentication

### 9.2 Teste de Sincronização

1. Crie uma planilha de treino no app mobile
2. Verifique se aparece no Firestore Console → workout_plans

### 9.3 Teste de Notificações

1. No Firebase Console → Cloud Messaging
2. Envie uma notificação de teste para o app

---

## Passo 10: Configurar CI/CD (Opcional)

### 10.1 GitHub Actions

Crie `.github/workflows/deploy.yml`:

```yaml
name: Deploy to Firebase

on:
  push:
    branches: [ main ]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-node@v2
        with:
          node-version: '18'
      - run: npm ci
        working-directory: functions
      - run: npm run build
        working-directory: functions
      - uses: w9jds/firebase-action@master
        with:
          args: deploy --only functions
        env:
          FIREBASE_TOKEN: ${{ secrets.FIREBASE_TOKEN }}
```

---

## Troubleshooting

### Erro: "google-services.json not found"

**Solução**: Certifique-se de que os arquivos `google-services.json` estão nos locais corretos:
- `app/google-services.json`
- `wear/google-services.json`

### Erro: "Firebase Auth failed"

**Solução**: 
1. Verifique se o SHA-1 está configurado no Firebase Console
2. Gere o SHA-1 com: `./gradlew signingReport`
3. Adicione no Firebase Console → Project Settings → Your apps

### Erro: "Health Services not available"

**Solução**: Health Services requer:
- Wear OS 3.0+ (API 30+)
- Google Play Services instalado
- Permissões concedidas

### Erro: "Firestore permission denied"

**Solução**: Verifique se as Firestore Rules foram deployadas:
```bash
firebase deploy --only firestore:rules
```

---

## Próximos Passos

Após a configuração completa:

1. ✅ Implementar telas de UI (Dashboard, Workout Execution, etc.)
2. ✅ Implementar ViewModels e Repositories
3. ✅ Configurar Room Database para cache offline
4. ✅ Implementar Health Services integration
5. ✅ Criar Wear OS Tile
6. ✅ Implementar Auto-detect Service
7. ✅ Escrever testes unitários e property-based tests

Consulte o arquivo `tasks.md` para a lista completa de tarefas.

---

## Recursos Úteis

- [Firebase Documentation](https://firebase.google.com/docs)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Wear OS Documentation](https://developer.android.com/training/wearables)
- [Health Services API](https://developer.android.com/training/wearables/health-services)
- [Kotest Documentation](https://kotest.io/)

---

## Suporte

Para dúvidas ou problemas, consulte a documentação ou entre em contato com a equipe de desenvolvimento.
