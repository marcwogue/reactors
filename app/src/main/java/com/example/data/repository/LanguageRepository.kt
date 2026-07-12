package com.example.data.repository

import android.util.Log
import com.example.data.api.ChatCompletionRequest
import com.example.data.api.ChatMessage
import com.example.data.api.ChatCorrectionResponse
import com.example.data.api.ExerciseEvaluationResponse
import com.example.data.api.ExerciseGenerationResponse
import com.example.data.api.HuggingFaceClient
import com.example.data.db.ApiKeyDao
import com.example.data.db.ApiKeyEntity
import com.example.data.db.AppDatabase
import com.example.data.db.AppSettingsDao
import com.example.data.db.AppSettingsEntity
import com.example.data.db.ExerciseDao
import com.example.data.db.ExerciseEntity
import com.example.data.db.LanguageDao
import com.example.data.db.LanguageEntity
import com.example.data.db.MessageDao
import com.example.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class LanguageRepository(
    private val database: AppDatabase
) {
    private val languageDao = database.languageDao()
    private val messageDao = database.messageDao()
    private val exerciseDao = database.exerciseDao()
    private val apiKeyDao = database.apiKeyDao()
    private val appSettingsDao = database.appSettingsDao()

    // --- LANGUAGES ---
    val allLanguagesFlow: Flow<List<LanguageEntity>> = languageDao.getAllLanguagesFlow()
    
    suspend fun getAllLanguages(): List<LanguageEntity> = withContext(Dispatchers.IO) {
        languageDao.getAllLanguages()
    }

    suspend fun getLanguageById(id: String): LanguageEntity? = withContext(Dispatchers.IO) {
        languageDao.getLanguageById(id)
    }

    fun getLanguageByIdFlow(id: String): Flow<LanguageEntity?> {
        return languageDao.getLanguageByIdFlow(id)
    }

    suspend fun insertLanguage(language: LanguageEntity) = withContext(Dispatchers.IO) {
        languageDao.insertLanguage(language)
    }

    suspend fun updateLanguageLevel(id: String, level: String) = withContext(Dispatchers.IO) {
        languageDao.updateLanguageLevel(id, level)
    }

    suspend fun updateLanguageSettings(id: String, times: String, personality: String, freq: String) = withContext(Dispatchers.IO) {
        languageDao.updateLanguageSettings(id, times, personality, freq)
    }

    suspend fun updateFullLanguageSettings(
        id: String,
        name: String,
        flagEmoji: String,
        level: String,
        interlocutorName: String,
        personality: String,
        times: String,
        freq: String
    ) = withContext(Dispatchers.IO) {
        languageDao.updateFullLanguageSettings(id, name, flagEmoji, level, interlocutorName, personality, times, freq)
    }

    suspend fun setActiveLanguage(id: String) = withContext(Dispatchers.IO) {
        languageDao.setActiveLanguage(id)
        val currentSettings = appSettingsDao.getSettings()
        if (currentSettings != null) {
            appSettingsDao.insertSettings(currentSettings.copy(activeLanguageId = id))
        } else {
            appSettingsDao.insertSettings(AppSettingsEntity(activeLanguageId = id))
        }
    }

    // --- MESSAGES ---
    fun getMessagesForLanguageFlow(languageId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesForLanguageFlow(languageId)
    }

    suspend fun insertMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(message)
    }

    suspend fun clearMessagesForLanguage(languageId: String) = withContext(Dispatchers.IO) {
        messageDao.clearMessagesForLanguage(languageId)
    }

    // --- EXERCISES ---
    fun getExercisesForLanguageFlow(languageId: String): Flow<List<ExerciseEntity>> {
        return exerciseDao.getExercisesForLanguageFlow(languageId)
    }

    val allExercisesFlow: Flow<List<ExerciseEntity>> = exerciseDao.getAllExercisesFlow()

    suspend fun insertExercise(exercise: ExerciseEntity) = withContext(Dispatchers.IO) {
        exerciseDao.insertExercise(exercise)
    }

    // --- API KEYS ---
    val allApiKeysFlow: Flow<List<ApiKeyEntity>> = apiKeyDao.getAllApiKeysFlow()
    
    suspend fun getAllApiKeys(): List<ApiKeyEntity> = withContext(Dispatchers.IO) {
        apiKeyDao.getAllApiKeys()
    }

    suspend fun insertApiKey(key: ApiKeyEntity) = withContext(Dispatchers.IO) {
        apiKeyDao.insertApiKey(key)
    }

    suspend fun deleteApiKeyById(id: Long) = withContext(Dispatchers.IO) {
        apiKeyDao.deleteApiKeyById(id)
    }

    // --- SETTINGS ---
    val appSettingsFlow: Flow<AppSettingsEntity?> = appSettingsDao.getSettingsFlow()

    suspend fun getSettings(): AppSettingsEntity? = withContext(Dispatchers.IO) {
        appSettingsDao.getSettings()
    }

    suspend fun saveSettings(settings: AppSettingsEntity) = withContext(Dispatchers.IO) {
        appSettingsDao.insertSettings(settings)
    }

    // --- API KEY ROTATION & FALLBACK ---
    private suspend fun getActiveAuthorizationHeader(): String {
        val keys = apiKeyDao.getAllApiKeys()
        if (keys.isNotEmpty()) {
            // Rotate keys or pick the first one for simplicity, or select randomly
            val selectedKey = keys.random().apiKey.trim()
            return "Bearer $selectedKey"
        }
        // Fallback: Check if there's a key in BuildConfig / environment (can be Gemini, but let's try to return it if it is available)
        // Since we are running in an environment with GEMINI_API_KEY, let's see if the user entered it or we can read a system secret
        // If no keys are set, we will throw a clear exception that can be caught to prompt the user to enter a key.
        throw IllegalStateException("Aucune clé API Hugging Face trouvée. Veuillez en ajouter une dans l'onglet Paramètres pour commencer !")
    }

    // --- AI CHAT INTEGRATION ---
    suspend fun sendMessageToAi(languageId: String, userText: String, isVoice: Boolean = false): Unit = withContext(Dispatchers.IO) {
        val language = languageDao.getLanguageById(languageId) ?: return@withContext
        val authHeader = getActiveAuthorizationHeader()
        val settings = appSettingsDao.getSettings() ?: AppSettingsEntity()

        // 1. Save user's message
        val userMsg = MessageEntity(
            languageId = languageId,
            sender = "user",
            text = userText,
            isVoice = isVoice
        )
        messageDao.insertMessage(userMsg)

        // 2. Fetch past conversation context (last 8 messages)
        val history = messageDao.getMessagesForLanguage(languageId)
            .takeLast(8)
            .filter { it.sender != "assistant_correction" } // ignore intermediate corrections for the conversational flow
            .map {
                ChatMessage(
                    role = if (it.sender == "user") "user" else "assistant",
                    content = it.text
                )
            }

        // 3. Prepare System Prompt
        val systemContent = """
            Tu es un partenaire de conversation d'apprentissage de langue très chaleureux, naturel et encourageant.
            Tu parles avec l'utilisateur dans la langue cible : ${language.name} (flag emoji: ${language.flagEmoji}).
            L'utilisateur s'appelle ${settings.userName}. Adhère à cela et adresse-toi à lui directement par son nom de temps en temps pour rendre l'échange chaleureux et personnalisé !
            Le niveau actuel de l'utilisateur est : ${language.currentLevel} (sur l'échelle officielle CEFR : A1, A2, B1, B2, C1, C2). 
            Tu DOIS absolument adapter ton vocabulaire, ta grammaire, la complexité de tes phrases et la longueur de tes réponses à ce niveau pour qu'il comprenne et progresse !
            
            Ton nom est ${language.interlocutorName}. Ta personnalité : ${language.interlocutorPersonality}.
            Tu te comportes comme un véritable ami humain qui discute. Tu adores utiliser des emojis pour être expressif et sympathique ! 😊🌟
            
            IMPORTANT : Analyse le dernier message de l'utilisateur. 
            S'il y a des fautes d'orthographe, de grammaire, de vocabulaire ou de conjugaison dans la langue d'apprentissage (${language.name}), fournis la version correcte et une explication brève et bienveillante en français.
            
            Tu dois STRICTEMENT respecter ce format de réponse :
            [CORRECTION] <La phrase de l'utilisateur entièrement corrigée en ${language.name}. Si la phrase de l'utilisateur est déjà correcte, écris obligatoirement "NONE">
            [EXPLANATION] <Une explication très rapide et claire en français de la faute commise et pourquoi. Si c'est déjà correct, écris obligatoirement "NONE">
            [REPLY] <Ta réponse chaleureuse, interactive et naturelle en ${language.name}, qui poursuit la conversation en posant éventuellement une question simple de niveau ${language.currentLevel}. Utilise des emojis !>
        """.trimIndent()

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage("system", systemContent))
        messages.addAll(history)

        try {
            val response = HuggingFaceClient.service.getChatCompletion(
                authorization = authHeader,
                modelId = settings.activeModel,
                request = ChatCompletionRequest(
                    model = settings.activeModel,
                    messages = messages
                )
            )

            val rawContent = response.choices?.firstOrNull()?.message?.content
            if (rawContent.isNullOrBlank()) {
                throw Exception("La réponse de l'IA est vide.")
            }

            // 4. Parse the AI response
            val parsed = ChatCorrectionResponse.parse(rawContent)

            // 5. If there's a correction, save it first
            if (parsed.correction != null) {
                val correctionText = "💡 *Correction de votre phrase :*\n« ${parsed.correction} »\n\n📝 *Explication :*\n${parsed.explanation}"
                val correctionMsg = MessageEntity(
                    languageId = languageId,
                    sender = "assistant_correction",
                    text = correctionText
                )
                messageDao.insertMessage(correctionMsg)
            }

            // 6. Save AI's conversational response
            val replyMsg = MessageEntity(
                languageId = languageId,
                sender = "assistant_reply",
                text = parsed.reply
            )
            messageDao.insertMessage(replyMsg)

            // 7. Level progression is evaluated automatically on Sunday at 00H by the IA.
            // evaluateAdaptiveLevelProgression(languageId)

        } catch (e: Exception) {
            Log.e("LanguageRepository", "Error in chat completions", e)
            val errMsg = MessageEntity(
                languageId = languageId,
                sender = "assistant_reply",
                text = "⚠️ Erreur de connexion avec l'IA : ${e.localizedMessage ?: "Vérifiez votre connexion internet ou votre clé API Hugging Face dans les paramètres."}"
            )
            messageDao.insertMessage(errMsg)
            throw e
        }
    }

    // --- EXERCISES GENERATION & GRADING ---
    suspend fun generateNewExercise(languageId: String): Unit = withContext(Dispatchers.IO) {
        val language = languageDao.getLanguageById(languageId) ?: return@withContext
        val authHeader = getActiveAuthorizationHeader()
        val settings = appSettingsDao.getSettings() ?: AppSettingsEntity()

        val systemContent = """
            Tu es un enseignant de langue spécialisé et bienveillant. Ton rôle est de générer une fiche d'exercice stimulante pour apprendre le ${language.name}.
            Le niveau actuel de l'utilisateur est : ${language.currentLevel} (échelle officielle CEFR).
            
            L'exercice doit comporter exactement 3 questions courtes adaptées au niveau ${language.currentLevel} (par exemple : conjuguer un verbe, traduire une courte phrase, ou corriger une faute).
            Sois créatif et utilise des emojis ! 
            
            Tu dois STRICTEMENT respecter ce format de réponse :
            [EXERCISE]
            Type d'exercice : <Grammaire, Vocabulaire, Traduction, ou Compréhension>
            Consigne : <Consigne générale très claire rédigée en français, ex: Conjuguez les verbes au présent>
            1. <Question 1>
            2. <Question 2>
            3. <Question 3>
        """.trimIndent()

        val messages = listOf(
            ChatMessage("system", systemContent),
            ChatMessage("user", "Génère une nouvelle fiche d'exercice s'il te plaît.")
        )

        try {
            val response = HuggingFaceClient.service.getChatCompletion(
                authorization = authHeader,
                modelId = settings.activeModel,
                request = ChatCompletionRequest(
                    model = settings.activeModel,
                    messages = messages
                )
            )

            val rawContent = response.choices?.firstOrNull()?.message?.content
            if (rawContent.isNullOrBlank()) {
                throw Exception("La fiche d'exercice reçue est vide.")
            }

            val parsed = ExerciseGenerationResponse.parse(rawContent)

            val exercise = ExerciseEntity(
                languageId = languageId,
                type = parsed.type,
                instruction = parsed.instruction,
                questions = parsed.questions
            )
            exerciseDao.insertExercise(exercise)

        } catch (e: Exception) {
            Log.e("LanguageRepository", "Error generating exercise", e)
            throw e
        }
    }

    suspend fun evaluateExercise(exerciseId: Long, userAnswer: String): Unit = withContext(Dispatchers.IO) {
        val exercise = exerciseDao.getExerciseById(exerciseId) ?: return@withContext
        val language = languageDao.getLanguageById(exercise.languageId) ?: return@withContext
        val authHeader = getActiveAuthorizationHeader()
        val settings = appSettingsDao.getSettings() ?: AppSettingsEntity()

        val systemContent = """
            Tu es un examinateur de langue rigoureux mais bienveillant. Tu dois corriger la fiche d'exercice d'un étudiant et lui attribuer une note globale sur 100.
            Langue : ${language.name}
            Niveau de l'exercice : ${language.currentLevel}
            
            Voici l'exercice proposé :
            Type d'exercice : ${exercise.type}
            Consigne : ${exercise.instruction}
            Questions :
            ${exercise.questions}
            
            Voici la réponse rédigée par l'étudiant :
            $userAnswer
            
            Corrige chaque question précisément en français, explique les erreurs éventuelles et donne la bonne réponse attendue.
            Attribue une note globale méritée (nombre entier entre 0 et 100).
            
            Tu dois STRICTEMENT respecter ce format de réponse :
            [GRADE] <Note sur 100, ex: 85. Écris uniquement le nombre entier.>
            [CORRECTION] <Ta correction bienveillante, détaillée question par question en français avec des emojis d'encouragement.>
        """.trimIndent()

        val messages = listOf(
            ChatMessage("system", systemContent),
            ChatMessage("user", "Voici mes réponses pour évaluation.")
        )

        try {
            val response = HuggingFaceClient.service.getChatCompletion(
                authorization = authHeader,
                modelId = settings.activeModel,
                request = ChatCompletionRequest(
                    model = settings.activeModel,
                    messages = messages
                )
            )

            val rawContent = response.choices?.firstOrNull()?.message?.content
            if (rawContent.isNullOrBlank()) {
                throw Exception("L'évaluation reçue est vide.")
            }

            val parsed = ExerciseEvaluationResponse.parse(rawContent)

            // Save the results
            exerciseDao.submitExerciseAnswer(
                id = exerciseId,
                answer = userAnswer,
                feedback = parsed.feedback,
                grade = parsed.grade
            )

            // Level progression is evaluated automatically on Sunday at 00H by the IA.
            // evaluateAdaptiveLevelProgression(exercise.languageId)

        } catch (e: Exception) {
            Log.e("LanguageRepository", "Error evaluating exercise", e)
            throw e
        }
    }

    // --- WEEKLY LEVEL EVALUATION (CEFR A1...C2) CALCULATED ON SUNDAYS AT 00H BY THE IA ---
    suspend fun evaluateWeeklyLevelWithAI(context: android.content.Context, languageId: String, force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        val language = languageDao.getLanguageById(languageId) ?: return@withContext
        val prefs = context.getSharedPreferences("reactors_eval_prefs", android.content.Context.MODE_PRIVATE)
        
        val lastEvalKey = "last_weekly_eval_ms_$languageId"
        val lastEvalTime = prefs.getLong(lastEvalKey, 0L)
        val now = System.currentTimeMillis()

        // Get Sunday 00:00 of the current week
        val calendar = java.util.Calendar.getInstance()
        calendar.firstDayOfWeek = java.util.Calendar.SUNDAY
        calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        
        if (calendar.timeInMillis > now) {
            calendar.add(java.util.Calendar.WEEK_OF_YEAR, -1)
        }
        val targetSundayMidnight = calendar.timeInMillis

        // If it's a completely new language study setup, initialize lastEvalTime to now so they get evaluated on next Sunday, not immediately.
        if (lastEvalTime == 0L && !force) {
            prefs.edit().putLong(lastEvalKey, now).apply()
            Log.d("LanguageRepository", "Initializing last weekly evaluation time for $languageId to current time.")
            return@withContext
        }

        if (!force && lastEvalTime >= targetSundayMidnight) {
            // Already evaluated for this week!
            Log.d("LanguageRepository", "Weekly evaluation for $languageId already completed for this week. Last eval: $lastEvalTime, Target Sunday: $targetSundayMidnight")
            return@withContext
        }

        val authHeader = getActiveAuthorizationHeader()
        val settings = appSettingsDao.getSettings() ?: AppSettingsEntity()

        val messages = messageDao.getMessagesForLanguage(languageId)
        val exercises = database.exerciseDao().getExercisesForLanguageFlow(languageId).firstOrNull() ?: emptyList()

        val oneWeekAgo = now - 7 * 24 * 60 * 60 * 1000L
        val recentUserMessages = messages.filter { it.sender == "user" && it.timestamp >= oneWeekAgo }
        val recentCompletedExercises = exercises.filter { it.isCompleted && it.grade != null && it.timestamp >= oneWeekAgo }

        val chatSummary = if (recentUserMessages.isNotEmpty()) {
            recentUserMessages.joinToString("\n") { "- ${it.text}" }
        } else {
            "Aucun message envoyé cette semaine."
        }

        val exerciseSummary = if (recentCompletedExercises.isNotEmpty()) {
            recentCompletedExercises.joinToString("\n") { "- Exercice de type ${it.type} avec la note de ${it.grade}/100" }
        } else {
            "Aucun exercice complété cette semaine."
        }

        val systemContent = """
            Tu es un examinateur et conseiller linguistique expert. Ton rôle est d'analyser l'activité de l'apprenant au cours de la semaine passée pour décider s'il doit progresser, rester au même niveau, ou descendre de niveau.
            
            Informations de l'apprenant :
            - Langue étudiée : ${language.name}
            - Niveau actuel : ${language.currentLevel} (A1, A2, B1, B2, C1, C2)
            - Nom d'utilisateur : ${settings.userName}
            
            Activité de la semaine passée :
            1. Messages de discussion rédigés par l'apprenant :
            $chatSummary
            
            2. Résultats aux fiches d'exercices complétées :
            $exerciseSummary
            
            Critères d'évaluation officiels (CEFR) :
            - A1 (Débutant) : Phrases extrêmement simples, vocabulaire très limité.
            - A2 (Élémentaire) : Peut communiquer sur des tâches simples, phrases courtes de la vie quotidienne.
            - B1 (Intermédiaire) : Peut s'exprimer sur des sujets familiers, faire des récits simples, donner son opinion.
            - B2 (Intermédiaire supérieur) : Compréhension de sujets complexes, discussion fluide, grammaire généralement correcte.
            - C1 (Avancé) : Expression fluide, structurée et naturelle, peu d'erreurs, vocabulaire riche.
            - C2 (Maîtrise) : Niveau quasi-bilingue, maîtrise totale de la langue.
            
            Consigne :
            Évalue rigoureusement mais avec bienveillance la performance globale de l'apprenant. Détermine son niveau CEFR le plus approprié pour la semaine à venir parmi A1, A2, B1, B2, C1, C2.
            Sois juste : s'il a d'excellents résultats (moyenne d'exercices > 85 et participation active en chat), fais-le progresser. S'il n'a pas ou peu participé, ou s'il a beaucoup de difficultés (moyenne d'exercices < 50), maintiens-le ou ajuste son niveau vers le bas pour l'encourager à consolider ses bases.
            
            Tu dois STRICTEMENT respecter ce format de réponse :
            [LEVEL] <A1/A2/B1/B2/C1/C2 - Écris uniquement le niveau choisi.>
            [JUSTIFICATION] <Ta justification en français, rédigée de manière chaleureuse et constructive, adressée directement à ${settings.userName}. Explique-lui pourquoi ce niveau est mérité, souligne ses forces, et donne-lui des conseils pratiques pour la semaine prochaine.>
        """.trimIndent()

        val apiMessages = listOf(
            ChatMessage("system", systemContent),
            ChatMessage("user", "Calcule mon niveau officiel pour cette semaine.")
        )

        try {
            val response = HuggingFaceClient.service.getChatCompletion(
                authorization = authHeader,
                modelId = settings.activeModel,
                request = ChatCompletionRequest(
                    model = settings.activeModel,
                    messages = apiMessages
                )
            )

            val rawContent = response.choices?.firstOrNull()?.message?.content
            if (rawContent.isNullOrBlank()) {
                throw Exception("La réponse de l'évaluation est vide.")
            }

            val parsed = com.example.data.api.WeeklyLevelEvaluationResponse.parse(rawContent, language.currentLevel)

            if (parsed.level != language.currentLevel) {
                languageDao.updateLanguageLevel(languageId, parsed.level)
            }

            // Insert a notification-like message in the chat to tell the user about the decision
            val displayMessage = """
                📊 **Bilan Hebdomadaire de l'IA (Calculé ce dimanche à 00h00)**
                
                Niveau évalué : **${parsed.level}** (précédent : ${language.currentLevel})
                
                📝 *Justification de votre instructeur IA :*
                ${parsed.justification}
            """.trimIndent()

            messageDao.insertMessage(
                MessageEntity(
                    languageId = languageId,
                    sender = "assistant_correction", // Appears as a high-contrast card
                    text = displayMessage
                )
            )

            // Save the successful evaluation timestamp
            prefs.edit().putLong(lastEvalKey, now).apply()

        } catch (e: Exception) {
            Log.e("LanguageRepository", "Error in weekly level evaluation", e)
            // Save a fallback log in the chat so the user knows
            messageDao.insertMessage(
                MessageEntity(
                    languageId = languageId,
                    sender = "assistant_correction",
                    text = "⚠️ L'IA n'a pas pu effectuer votre évaluation hebdomadaire automatique ce dimanche en raison d'un problème réseau ou d'une clé API manquante. Vos configurations restent inchangées."
                )
            )
        }
    }

    // --- POPULATE DEFAULT LANGUAGES ---
    suspend fun populateDefaultLanguagesIfEmpty() = withContext(Dispatchers.IO) {
        val existing = languageDao.getAllLanguages()
        if (existing.isEmpty()) {
            val defaults = listOf(
                LanguageEntity(
                    id = "en",
                    name = "Anglais",
                    flagEmoji = "🇬🇧",
                    currentLevel = "A1",
                    interlocutorName = "Sarah",
                    interlocutorPersonality = "Une londonienne enthousiaste qui adore le thé, le rock britannique, les romans de Jane Austen et parler de la météo."
                ),
                LanguageEntity(
                    id = "es",
                    name = "Espagnol",
                    flagEmoji = "🇪🇸",
                    currentLevel = "A1",
                    interlocutorName = "Mateo",
                    interlocutorPersonality = "Un madrilène très chaleureux et bavard, passionné de football, de cuisine de tapas et d'art de rue."
                ),
                LanguageEntity(
                    id = "de",
                    name = "Allemand",
                    flagEmoji = "🇩🇪",
                    currentLevel = "A1",
                    interlocutorName = "Hans",
                    interlocutorPersonality = "Un berlinois décontracté, féru de musique électronique, d'écologie, de vélos et de technologie."
                ),
                LanguageEntity(
                    id = "it",
                    name = "Italien",
                    flagEmoji = "🇮🇹",
                    currentLevel = "A1",
                    interlocutorName = "Giulia",
                    interlocutorPersonality = "Une romaine passionnée de cinéma classique, de café serré, d'histoire romaine antique et d'opéra."
                ),
                LanguageEntity(
                    id = "ja",
                    name = "Japonais",
                    flagEmoji = "🇯🇵",
                    currentLevel = "A1",
                    interlocutorName = "Yuki",
                    interlocutorPersonality = "Une habitante de Kyoto polie et attentionnée, amatrice d'animes, de mangas, de calligraphie traditionnelle et de cuisine de sushi."
                )
            )
            defaults.forEach { languageDao.insertLanguage(it) }
            
            // Set English active by default
            languageDao.setActiveLanguage("en")
            appSettingsDao.insertSettings(AppSettingsEntity(activeLanguageId = "en"))
        }
    }
}
