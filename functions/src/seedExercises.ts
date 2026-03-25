/**
 * Exercise Library Seed Script
 *
 * Populates Firestore with exercise data for all major muscle groups.
 * Requirements: 2.1
 *
 * Usage:
 *   npx ts-node --project tsconfig.json src/seedExercises.ts
 *
 * Or compile and run:
 *   npm run build && node lib/seedExercises.js
 */

import * as admin from "firebase-admin";

// Initialize Firebase Admin (uses GOOGLE_APPLICATION_CREDENTIALS or default credentials)
if (!admin.apps.length) {
    admin.initializeApp();
}

interface ExerciseData {
    exerciseId: string;
    name: string;
    muscleGroup: string;
    secondaryMuscles: string[];
    instructions: string;
    mediaUrl: string;
    mediaType: string;
    difficulty: string;
    equipment: string[];
}

const exercises: ExerciseData[] = [
    // ─── CHEST ───────────────────────────────────────────────────────────────
    {
        exerciseId: "supino_reto",
        name: "Supino Reto",
        muscleGroup: "chest",
        secondaryMuscles: ["triceps", "shoulders"],
        instructions:
            "Deite-se no banco reto com os pés apoiados no chão. Segure a barra com pegada um pouco mais larga que a largura dos ombros. Desça a barra de forma controlada até tocar levemente o peito e empurre de volta à posição inicial, estendendo os cotovelos sem travá-los.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/supino_reto.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell", "bench"],
    },
    {
        exerciseId: "supino_inclinado",
        name: "Supino Inclinado",
        muscleGroup: "chest",
        secondaryMuscles: ["triceps", "shoulders"],
        instructions:
            "Ajuste o banco a 30–45 graus. Deite-se e segure a barra com pegada um pouco mais larga que os ombros. Desça a barra até a parte superior do peito de forma controlada e empurre de volta à posição inicial, focando na contração da parte superior do peitoral.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/supino_inclinado.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell", "bench"],
    },
    {
        exerciseId: "crucifixo_halteres",
        name: "Crucifixo com Halteres",
        muscleGroup: "chest",
        secondaryMuscles: ["shoulders"],
        instructions:
            "Deite-se no banco reto segurando um halter em cada mão com os braços estendidos acima do peito. Abra os braços em arco, descendo os halteres até a altura do peito com leve flexão nos cotovelos. Retorne à posição inicial contraindo o peitoral.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/crucifixo_halteres.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell", "bench"],
    },
    {
        exerciseId: "flexao_de_braco",
        name: "Flexão de Braço",
        muscleGroup: "chest",
        secondaryMuscles: ["triceps", "core"],
        instructions:
            "Posicione-se em prancha com as mãos um pouco mais largas que os ombros. Desça o corpo até o peito quase tocar o chão, mantendo o core contraído e o corpo alinhado. Empurre de volta à posição inicial estendendo os cotovelos.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/flexao_de_braco.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["bodyweight"],
    },
    {
        exerciseId: "crossover_cabo",
        name: "Crossover no Cabo",
        muscleGroup: "chest",
        secondaryMuscles: ["shoulders"],
        instructions:
            "Posicione-se no centro do cabo com as polias na posição alta. Segure as alças e dê um passo à frente. Com leve flexão nos cotovelos, traga as mãos em arco até se encontrarem na frente do corpo, contraindo o peitoral. Retorne de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/crossover_cabo.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["cable"],
    },

    // ─── BACK ────────────────────────────────────────────────────────────────
    {
        exerciseId: "barra_fixa",
        name: "Barra Fixa",
        muscleGroup: "back",
        secondaryMuscles: ["biceps", "core"],
        instructions:
            "Segure a barra com pegada pronada (palmas para frente) um pouco mais larga que os ombros. Puxe o corpo para cima até o queixo ultrapassar a barra, contraindo as costas. Desça de forma controlada até os braços ficarem quase estendidos.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/barra_fixa.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["pull_up_bar", "bodyweight"],
    },
    {
        exerciseId: "remada_curvada",
        name: "Remada Curvada com Barra",
        muscleGroup: "back",
        secondaryMuscles: ["biceps", "core"],
        instructions:
            "Fique em pé com os pés na largura dos ombros, segure a barra com pegada pronada. Incline o tronco para frente a aproximadamente 45 graus, mantendo a coluna neutra. Puxe a barra em direção ao abdômen, contraindo as escápulas. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/remada_curvada.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell"],
    },
    {
        exerciseId: "puxada_frente",
        name: "Puxada pela Frente",
        muscleGroup: "back",
        secondaryMuscles: ["biceps", "shoulders"],
        instructions:
            "Sente-se na máquina de puxada e segure a barra com pegada pronada mais larga que os ombros. Puxe a barra até a altura do queixo, contraindo o grande dorsal. Retorne de forma controlada à posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/puxada_frente.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["machine", "cable"],
    },
    {
        exerciseId: "remada_unilateral",
        name: "Remada Unilateral com Halter",
        muscleGroup: "back",
        secondaryMuscles: ["biceps"],
        instructions:
            "Apoie um joelho e a mão do mesmo lado no banco. Com a outra mão segure o halter. Puxe o halter em direção ao quadril, mantendo o cotovelo próximo ao corpo e contraindo as costas. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/remada_unilateral.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell", "bench"],
    },
    {
        exerciseId: "levantamento_terra",
        name: "Levantamento Terra",
        muscleGroup: "back",
        secondaryMuscles: ["glutes", "hamstrings", "core"],
        instructions:
            "Fique em pé com os pés na largura dos quadris, barra sobre o meio dos pés. Agache e segure a barra com pegada pronada ou alternada. Mantenha a coluna neutra, empurre o chão com os pés e levante a barra até ficar em pé. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/levantamento_terra.gif",
        mediaType: "gif",
        difficulty: "advanced",
        equipment: ["barbell"],
    },

    // ─── SHOULDERS ───────────────────────────────────────────────────────────
    {
        exerciseId: "desenvolvimento_barra",
        name: "Desenvolvimento com Barra",
        muscleGroup: "shoulders",
        secondaryMuscles: ["triceps", "core"],
        instructions:
            "Fique em pé ou sentado com a barra na altura dos ombros, pegada pronada um pouco mais larga que os ombros. Empurre a barra para cima até os braços ficarem quase estendidos. Desça de forma controlada até a posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/desenvolvimento_barra.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell"],
    },
    {
        exerciseId: "elevacao_lateral",
        name: "Elevação Lateral com Halteres",
        muscleGroup: "shoulders",
        secondaryMuscles: [],
        instructions:
            "Fique em pé com um halter em cada mão ao lado do corpo. Com leve flexão nos cotovelos, eleve os braços lateralmente até a altura dos ombros. Desça de forma controlada. Evite usar impulso do tronco.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/elevacao_lateral.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell"],
    },
    {
        exerciseId: "elevacao_frontal",
        name: "Elevação Frontal com Halteres",
        muscleGroup: "shoulders",
        secondaryMuscles: ["chest"],
        instructions:
            "Fique em pé com um halter em cada mão na frente das coxas. Eleve um braço de cada vez (ou ambos) à frente até a altura dos ombros, mantendo leve flexão no cotovelo. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/elevacao_frontal.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell"],
    },
    {
        exerciseId: "desenvolvimento_halteres",
        name: "Desenvolvimento com Halteres",
        muscleGroup: "shoulders",
        secondaryMuscles: ["triceps"],
        instructions:
            "Sente-se no banco com encosto, segure um halter em cada mão na altura dos ombros com as palmas voltadas para frente. Empurre os halteres para cima até quase estender os braços. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/desenvolvimento_halteres.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell", "bench"],
    },
    {
        exerciseId: "encolhimento_ombros",
        name: "Encolhimento de Ombros",
        muscleGroup: "shoulders",
        secondaryMuscles: [],
        instructions:
            "Fique em pé com um halter em cada mão ao lado do corpo. Eleve os ombros em direção às orelhas o máximo possível, contraindo o trapézio. Segure por um segundo e desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/encolhimento_ombros.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell"],
    },

    // ─── BICEPS ──────────────────────────────────────────────────────────────
    {
        exerciseId: "rosca_direta",
        name: "Rosca Direta com Barra",
        muscleGroup: "biceps",
        secondaryMuscles: ["forearms"],
        instructions:
            "Fique em pé com a barra segura com pegada supinada na largura dos ombros. Mantenha os cotovelos fixos ao lado do corpo e flexione os braços, trazendo a barra até a altura dos ombros. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/rosca_direta.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["barbell"],
    },
    {
        exerciseId: "rosca_alternada",
        name: "Rosca Alternada com Halteres",
        muscleGroup: "biceps",
        secondaryMuscles: ["forearms"],
        instructions:
            "Fique em pé com um halter em cada mão ao lado do corpo. Flexione um braço de cada vez, girando o pulso para cima (supinação) durante o movimento. Desça de forma controlada e alterne os braços.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/rosca_alternada.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell"],
    },
    {
        exerciseId: "rosca_martelo",
        name: "Rosca Martelo",
        muscleGroup: "biceps",
        secondaryMuscles: ["forearms"],
        instructions:
            "Fique em pé com um halter em cada mão com as palmas voltadas para o corpo (pegada neutra). Flexione os braços alternadamente mantendo a pegada neutra durante todo o movimento. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/rosca_martelo.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell"],
    },
    {
        exerciseId: "rosca_scott",
        name: "Rosca Scott",
        muscleGroup: "biceps",
        secondaryMuscles: [],
        instructions:
            "Sente-se no banco Scott e apoie os braços no suporte inclinado. Segure a barra com pegada supinada. Flexione os braços trazendo a barra até a altura dos ombros, contraindo o bíceps. Desça de forma controlada até quase estender os braços.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/rosca_scott.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell", "machine"],
    },
    {
        exerciseId: "rosca_cabo",
        name: "Rosca no Cabo",
        muscleGroup: "biceps",
        secondaryMuscles: [],
        instructions:
            "Fique em pé de frente para a polia baixa. Segure a barra reta ou corda com pegada supinada. Mantenha os cotovelos fixos e flexione os braços até a altura dos ombros. Desça de forma controlada mantendo tensão constante.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/rosca_cabo.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["cable"],
    },

    // ─── TRICEPS ─────────────────────────────────────────────────────────────
    {
        exerciseId: "triceps_testa",
        name: "Tríceps Testa",
        muscleGroup: "triceps",
        secondaryMuscles: [],
        instructions:
            "Deite-se no banco reto segurando a barra com pegada pronada na largura dos ombros. Mantenha os cotovelos apontados para cima e desça a barra em direção à testa flexionando os cotovelos. Estenda os braços de volta à posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/triceps_testa.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell", "bench"],
    },
    {
        exerciseId: "triceps_corda",
        name: "Tríceps na Corda",
        muscleGroup: "triceps",
        secondaryMuscles: [],
        instructions:
            "Fique em pé de frente para a polia alta. Segure a corda com as palmas voltadas uma para a outra. Mantenha os cotovelos fixos ao lado do corpo e estenda os braços para baixo, separando as pontas da corda ao final. Retorne de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/triceps_corda.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["cable"],
    },
    {
        exerciseId: "mergulho_banco",
        name: "Mergulho no Banco",
        muscleGroup: "triceps",
        secondaryMuscles: ["chest", "shoulders"],
        instructions:
            "Sente-se na borda do banco com as mãos ao lado dos quadris. Deslize o corpo para fora do banco e desça flexionando os cotovelos até os braços formarem 90 graus. Empurre de volta à posição inicial estendendo os cotovelos.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/mergulho_banco.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["bench", "bodyweight"],
    },
    {
        exerciseId: "triceps_frances",
        name: "Tríceps Francês com Halter",
        muscleGroup: "triceps",
        secondaryMuscles: [],
        instructions:
            "Sente-se ou fique em pé segurando um halter com ambas as mãos acima da cabeça. Flexione os cotovelos abaixando o halter atrás da cabeça. Estenda os braços de volta à posição inicial contraindo o tríceps.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/triceps_frances.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell"],
    },
    {
        exerciseId: "supino_fechado",
        name: "Supino Fechado",
        muscleGroup: "triceps",
        secondaryMuscles: ["chest", "shoulders"],
        instructions:
            "Deite-se no banco reto e segure a barra com pegada pronada na largura dos ombros ou um pouco mais fechada. Desça a barra até o peito mantendo os cotovelos próximos ao corpo. Empurre de volta à posição inicial focando na contração do tríceps.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/supino_fechado.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell", "bench"],
    },

    // ─── QUADRICEPS ──────────────────────────────────────────────────────────
    {
        exerciseId: "agachamento",
        name: "Agachamento com Barra",
        muscleGroup: "quadriceps",
        secondaryMuscles: ["glutes", "hamstrings", "core"],
        instructions:
            "Posicione a barra sobre os trapézios, pés na largura dos ombros com os dedos levemente voltados para fora. Desça flexionando os joelhos e quadris até as coxas ficarem paralelas ao chão, mantendo o tronco ereto. Empurre o chão com os pés para retornar.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/agachamento.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell"],
    },
    {
        exerciseId: "leg_press",
        name: "Leg Press",
        muscleGroup: "quadriceps",
        secondaryMuscles: ["glutes", "hamstrings"],
        instructions:
            "Sente-se na máquina de leg press com os pés na plataforma na largura dos ombros. Desça a plataforma flexionando os joelhos até formarem 90 graus. Empurre de volta à posição inicial sem travar os joelhos.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/leg_press.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["machine"],
    },
    {
        exerciseId: "extensao_joelho",
        name: "Extensão de Joelho",
        muscleGroup: "quadriceps",
        secondaryMuscles: [],
        instructions:
            "Sente-se na máquina de extensão com os tornozelos sob o rolo. Estenda as pernas até ficarem quase retas, contraindo o quadríceps. Desça de forma controlada até a posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/extensao_joelho.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["machine"],
    },
    {
        exerciseId: "afundo",
        name: "Afundo com Halteres",
        muscleGroup: "quadriceps",
        secondaryMuscles: ["glutes", "hamstrings"],
        instructions:
            "Fique em pé com um halter em cada mão. Dê um passo à frente com uma perna e desça o joelho traseiro em direção ao chão. O joelho da frente não deve ultrapassar a ponta do pé. Empurre de volta à posição inicial e alterne as pernas.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/afundo.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell"],
    },
    {
        exerciseId: "hack_squat",
        name: "Hack Squat",
        muscleGroup: "quadriceps",
        secondaryMuscles: ["glutes", "hamstrings"],
        instructions:
            "Posicione-se na máquina de hack squat com os ombros sob os apoios e os pés na plataforma. Desça flexionando os joelhos até as coxas ficarem paralelas à plataforma. Empurre de volta à posição inicial sem travar os joelhos.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/hack_squat.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["machine"],
    },

    // ─── HAMSTRINGS ──────────────────────────────────────────────────────────
    {
        exerciseId: "mesa_flexora",
        name: "Mesa Flexora",
        muscleGroup: "hamstrings",
        secondaryMuscles: ["calves"],
        instructions:
            "Deite-se de bruços na máquina com os tornozelos sob o rolo. Flexione os joelhos trazendo os calcanhares em direção aos glúteos. Desça de forma controlada até a posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/mesa_flexora.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["machine"],
    },
    {
        exerciseId: "stiff",
        name: "Stiff com Barra",
        muscleGroup: "hamstrings",
        secondaryMuscles: ["glutes", "back"],
        instructions:
            "Fique em pé com a barra na frente das coxas, pés na largura dos quadris. Mantendo as pernas quase estendidas e a coluna neutra, incline o tronco para frente descendo a barra ao longo das pernas. Retorne à posição inicial contraindo os isquiotibiais e glúteos.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/stiff.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell"],
    },
    {
        exerciseId: "cadeira_flexora",
        name: "Cadeira Flexora",
        muscleGroup: "hamstrings",
        secondaryMuscles: [],
        instructions:
            "Sente-se na máquina com os tornozelos sob o rolo. Flexione os joelhos puxando o rolo em direção aos glúteos. Desça de forma controlada à posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/cadeira_flexora.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["machine"],
    },
    {
        exerciseId: "agachamento_sumô",
        name: "Agachamento Sumô",
        muscleGroup: "hamstrings",
        secondaryMuscles: ["glutes", "quadriceps", "adductors"],
        instructions:
            "Fique em pé com os pés mais largos que os ombros e os dedos voltados para fora. Segure um halter com ambas as mãos entre as pernas. Desça flexionando os joelhos e quadris mantendo o tronco ereto. Empurre de volta à posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/agachamento_sumo.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell"],
    },

    // ─── GLUTES ──────────────────────────────────────────────────────────────
    {
        exerciseId: "hip_thrust",
        name: "Hip Thrust com Barra",
        muscleGroup: "glutes",
        secondaryMuscles: ["hamstrings", "core"],
        instructions:
            "Apoie as costas no banco com a barra sobre os quadris. Com os pés apoiados no chão na largura dos ombros, empurre os quadris para cima contraindo os glúteos até o corpo ficar paralelo ao chão. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/hip_thrust.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["barbell", "bench"],
    },
    {
        exerciseId: "elevacao_quadril",
        name: "Elevação de Quadril",
        muscleGroup: "glutes",
        secondaryMuscles: ["hamstrings"],
        instructions:
            "Deite-se de costas com os joelhos flexionados e os pés apoiados no chão. Empurre os quadris para cima contraindo os glúteos até o corpo formar uma linha reta dos ombros aos joelhos. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/elevacao_quadril.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["bodyweight"],
    },
    {
        exerciseId: "kickback_cabo",
        name: "Kickback no Cabo",
        muscleGroup: "glutes",
        secondaryMuscles: ["hamstrings"],
        instructions:
            "Fique em pé de frente para a polia baixa com o tornozelo preso à alça. Apoie-se no suporte e estenda a perna para trás contraindo o glúteo. Retorne de forma controlada à posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/kickback_cabo.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["cable"],
    },
    {
        exerciseId: "agachamento_bulgaro",
        name: "Agachamento Búlgaro",
        muscleGroup: "glutes",
        secondaryMuscles: ["quadriceps", "hamstrings"],
        instructions:
            "Fique em pé de costas para o banco e apoie o pé traseiro sobre ele. Com um halter em cada mão, desça o joelho traseiro em direção ao chão flexionando o joelho da frente. Empurre de volta à posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/agachamento_bulgaro.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["dumbbell", "bench"],
    },
    {
        exerciseId: "abdução_quadril",
        name: "Abdução de Quadril na Máquina",
        muscleGroup: "glutes",
        secondaryMuscles: [],
        instructions:
            "Sente-se na máquina de abdução com as pernas contra os apoios internos. Abra as pernas contra a resistência contraindo os glúteos. Retorne de forma controlada à posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/abducao_quadril.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["machine"],
    },

    // ─── CALVES ──────────────────────────────────────────────────────────────
    {
        exerciseId: "panturrilha_em_pe",
        name: "Panturrilha em Pé",
        muscleGroup: "calves",
        secondaryMuscles: [],
        instructions:
            "Fique em pé na máquina de panturrilha com os ombros sob os apoios e a ponta dos pés na plataforma. Eleve os calcanhares o máximo possível contraindo as panturrilhas. Desça de forma controlada até sentir o alongamento.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/panturrilha_em_pe.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["machine"],
    },
    {
        exerciseId: "panturrilha_sentado",
        name: "Panturrilha Sentado",
        muscleGroup: "calves",
        secondaryMuscles: [],
        instructions:
            "Sente-se na máquina de panturrilha sentado com os joelhos sob os apoios e a ponta dos pés na plataforma. Eleve os calcanhares contraindo as panturrilhas. Desça de forma controlada.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/panturrilha_sentado.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["machine"],
    },
    {
        exerciseId: "panturrilha_leg_press",
        name: "Panturrilha no Leg Press",
        muscleGroup: "calves",
        secondaryMuscles: [],
        instructions:
            "No leg press, posicione apenas a ponta dos pés na parte inferior da plataforma. Estenda os tornozelos empurrando a plataforma com as pontas dos pés. Retorne de forma controlada sentindo o alongamento.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/panturrilha_leg_press.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["machine"],
    },
    {
        exerciseId: "panturrilha_unilateral",
        name: "Panturrilha Unilateral com Halter",
        muscleGroup: "calves",
        secondaryMuscles: [],
        instructions:
            "Fique em pé em um degrau ou plataforma com a ponta de um pé na borda. Segure um halter na mão do mesmo lado. Eleve o calcanhar o máximo possível. Desça de forma controlada. Repita e alterne as pernas.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/panturrilha_unilateral.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["dumbbell"],
    },

    // ─── ABS / CORE ──────────────────────────────────────────────────────────
    {
        exerciseId: "abdominal_crunch",
        name: "Abdominal Crunch",
        muscleGroup: "abs",
        secondaryMuscles: ["core"],
        instructions:
            "Deite-se de costas com os joelhos flexionados e os pés apoiados no chão. Com as mãos atrás da cabeça, contraia o abdômen elevando os ombros do chão. Desça de forma controlada sem apoiar completamente a cabeça.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/abdominal_crunch.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["bodyweight"],
    },
    {
        exerciseId: "prancha",
        name: "Prancha",
        muscleGroup: "core",
        secondaryMuscles: ["abs", "shoulders"],
        instructions:
            "Apoie-se nos antebraços e nas pontas dos pés, mantendo o corpo em linha reta da cabeça aos calcanhares. Contraia o abdômen e os glúteos. Mantenha a posição pelo tempo determinado sem deixar o quadril cair ou subir.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/prancha.gif",
        mediaType: "gif",
        difficulty: "beginner",
        equipment: ["bodyweight"],
    },
    {
        exerciseId: "abdominal_roda",
        name: "Abdominal com Roda",
        muscleGroup: "core",
        secondaryMuscles: ["abs", "shoulders", "back"],
        instructions:
            "Ajoelhe-se no chão segurando a roda abdominal com ambas as mãos. Role a roda para frente estendendo o corpo até quase tocar o chão. Contraia o core e puxe de volta à posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/abdominal_roda.gif",
        mediaType: "gif",
        difficulty: "advanced",
        equipment: ["bodyweight"],
    },
    {
        exerciseId: "elevacao_pernas",
        name: "Elevação de Pernas",
        muscleGroup: "abs",
        secondaryMuscles: ["core", "hip_flexors"],
        instructions:
            "Deite-se de costas com as pernas estendidas. Mantenha as mãos ao lado do corpo ou sob os glúteos. Eleve as pernas até formarem 90 graus com o tronco, contraindo o abdômen. Desça de forma controlada sem tocar o chão.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/elevacao_pernas.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["bodyweight"],
    },
    {
        exerciseId: "abdominal_cabo",
        name: "Abdominal no Cabo",
        muscleGroup: "abs",
        secondaryMuscles: ["core"],
        instructions:
            "Ajoelhe-se de frente para a polia alta segurando a corda atrás da cabeça. Contraia o abdômen flexionando o tronco para baixo em direção aos joelhos. Retorne de forma controlada à posição inicial.",
        mediaUrl: "https://storage.googleapis.com/v-trainer/exercises/abdominal_cabo.gif",
        mediaType: "gif",
        difficulty: "intermediate",
        equipment: ["cable"],
    },
];

/**
 * Seeds the Firestore `exercises` collection with the exercise library.
 * Uses batch writes for efficiency (max 500 operations per batch).
 */
async function seedExercises(): Promise<void> {
    const db = admin.firestore();
    const collectionRef = db.collection("exercises");

    console.log(`[V-Trainer Seed] Iniciando seed de ${exercises.length} exercícios...`);

    // Firestore batch limit is 500 operations
    const BATCH_SIZE = 500;
    let seeded = 0;

    for (let i = 0; i < exercises.length; i += BATCH_SIZE) {
        const batch = db.batch();
        const chunk = exercises.slice(i, i + BATCH_SIZE);

        for (const exercise of chunk) {
            const docRef = collectionRef.doc(exercise.exerciseId);
            batch.set(docRef, exercise, { merge: false });
        }

        await batch.commit();
        seeded += chunk.length;
        console.log(`[V-Trainer Seed] ${seeded}/${exercises.length} exercícios salvos.`);
    }

    console.log(`[V-Trainer Seed] Seed concluído com sucesso! ${seeded} exercícios adicionados à coleção 'exercises'.`);
}

// Run when executed directly
seedExercises()
    .then(() => {
        console.log("[V-Trainer Seed] Processo finalizado.");
        process.exit(0);
    })
    .catch((error) => {
        console.error("[V-Trainer Seed] Erro durante o seed:", error);
        process.exit(1);
    });
