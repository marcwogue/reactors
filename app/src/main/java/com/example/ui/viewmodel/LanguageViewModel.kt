package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ApiKeyEntity
import com.example.data.db.AppDatabase
import com.example.data.db.AppSettingsEntity
import com.example.data.db.ExerciseEntity
import com.example.data.db.LanguageEntity
import com.example.data.db.MessageEntity
import com.example.data.repository.LanguageRepository
import com.example.receiver.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

enum class AppScreen {
    CHAT,
    EXERCISES,
    SETTINGS,
    LANGUAGES
}

class LanguageViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {
    private val database = AppDatabase.getDatabase(application)
    private val repository = LanguageRepository(database)

    // --- VIEWMODEL STATE ---
    val allLanguages: StateFlow<List<LanguageEntity>> = repository.allLanguagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettingsEntity> = repository.appSettingsFlow
        .combine(flowOf(Unit)) { s, _ -> s ?: AppSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    val activeLanguage: StateFlow<LanguageEntity?> = settings
        .flatMapLatest { s ->
            s.activeLanguageId?.let { repository.getLanguageByIdFlow(it) } ?: flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<MessageEntity>> = activeLanguage
        .flatMapLatest { lang ->
            lang?.let { repository.getMessagesForLanguageFlow(it.id) } ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exercises: StateFlow<List<ExerciseEntity>> = activeLanguage
        .flatMapLatest { lang ->
            lang?.let { repository.getExercisesForLanguageFlow(it.id) } ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiKeys: StateFlow<List<ApiKeyEntity>> = repository.allApiKeysFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMessages: StateFlow<List<MessageEntity>> = repository.allMessagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state
    private val _currentScreen = MutableStateFlow(AppScreen.CHAT)
    val currentScreen = _currentScreen.asStateFlow()

    private val _isChatDetailOpen = MutableStateFlow(false)
    val isChatDetailOpen = _isChatDetailOpen.asStateFlow()

    private val _chatInput = MutableStateFlow("")
    val chatInput = _chatInput.asStateFlow()

    private val _exerciseInput = MutableStateFlow("")
    val exerciseInput = _exerciseInput.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading = _isChatLoading.asStateFlow()

    private val _isExerciseLoading = MutableStateFlow(false)
    val isExerciseLoading = _isExerciseLoading.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError = _chatError.asStateFlow()

    private val _exerciseError = MutableStateFlow<String?>(null)
    val exerciseError = _exerciseError.asStateFlow()

    private val _selectedExerciseForEvaluation = MutableStateFlow<ExerciseEntity?>(null)
    val selectedExerciseForEvaluation = _selectedExerciseForEvaluation.asStateFlow()

    // --- TEXT TO SPEECH (TTS) ---
    private var textToSpeech: TextToSpeech? = null
    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady = _isTtsReady.asStateFlow()

    // --- SPEECH RECOGNITION (STT) ---
    private var speechRecognizer: SpeechRecognizer? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText = _recognizedText.asStateFlow()

    init {
        viewModelScope.launch {
            repository.populateDefaultLanguagesIfEmpty()
            activeLanguage.collect { lang ->
                if (lang != null) {
                    repository.evaluateWeeklyLevelWithAI(getApplication(), lang.id, force = false)
                }
            }
        }
        initializeTts()
        initializeStt()
    }

    fun forceWeeklyLevelEvaluation(languageId: String) {
        viewModelScope.launch {
            _isChatLoading.value = true
            try {
                repository.evaluateWeeklyLevelWithAI(getApplication(), languageId, force = true)
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    // --- NAVIGATION ---
    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // --- INPUTS ---
    fun updateChatInput(text: String) {
        _chatInput.value = text
    }

    fun updateExerciseInput(text: String) {
        _exerciseInput.value = text
    }

    // --- LANGUAGES ACTION ---
    fun selectLanguage(langId: String) {
        viewModelScope.launch {
            repository.setActiveLanguage(langId)
            val lang = repository.getLanguageById(langId)
            if (lang != null) {
                // Reschedule notifications for the new active language
                NotificationScheduler.scheduleAlarms(getApplication(), langId, lang.wakeUpTimes)
            }
            _currentScreen.value = AppScreen.CHAT
            _isChatDetailOpen.value = true
        }
    }

    fun openChatDetail(langId: String) {
        viewModelScope.launch {
            repository.setActiveLanguage(langId)
            _isChatDetailOpen.value = true
        }
    }

    fun closeChatDetail() {
        _isChatDetailOpen.value = false
    }

    fun addNewLanguage(id: String, name: String, emoji: String, personality: String, contactName: String) {
        viewModelScope.launch {
            val cleanId = id.trim().lowercase()
            if (cleanId.isEmpty() || name.trim().isEmpty() || emoji.trim().isEmpty()) return@launch

            val newLang = LanguageEntity(
                id = cleanId,
                name = name.trim(),
                flagEmoji = emoji.trim(),
                currentLevel = "A1",
                interlocutorName = contactName.trim().ifEmpty { "Compagnon IA" },
                interlocutorPersonality = personality.trim().ifEmpty { "Un ami bienveillant pour pratiquer la langue." }
            )
            repository.insertLanguage(newLang)
            repository.setActiveLanguage(cleanId)
            _currentScreen.value = AppScreen.CHAT
            _isChatDetailOpen.value = true
        }
    }

    // --- CHAT ACTIONS ---
    fun sendChatMessage() {
        val input = _chatInput.value.trim()
        val lang = activeLanguage.value ?: return
        if (input.isEmpty()) return

        _chatInput.value = ""
        _chatError.value = null
        _isChatLoading.value = true

        viewModelScope.launch {
            try {
                repository.sendMessageToAi(lang.id, input, isVoice = false)
            } catch (e: Exception) {
                _chatError.value = e.localizedMessage ?: "Une erreur est survenue."
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    fun clearChatHistory() {
        val lang = activeLanguage.value ?: return
        viewModelScope.launch {
            repository.clearMessagesForLanguage(lang.id)
        }
    }

    // --- EXERCISE ACTIONS ---
    fun generateExercise() {
        val lang = activeLanguage.value ?: return
        _isExerciseLoading.value = true
        _exerciseError.value = null

        viewModelScope.launch {
            try {
                repository.generateNewExercise(lang.id)
            } catch (e: Exception) {
                _exerciseError.value = e.localizedMessage ?: "Impossible de générer l'exercice."
            } finally {
                _isExerciseLoading.value = false
            }
        }
    }

    fun selectExerciseForEvaluation(exercise: ExerciseEntity) {
        _selectedExerciseForEvaluation.value = exercise
        _exerciseInput.value = exercise.userAnswer ?: ""
    }

    fun submitExerciseAnswers() {
        val exercise = _selectedExerciseForEvaluation.value ?: return
        val answers = _exerciseInput.value.trim()
        if (answers.isEmpty()) return

        _isExerciseLoading.value = true
        _exerciseError.value = null

        viewModelScope.launch {
            try {
                repository.evaluateExercise(exercise.id, answers)
                _selectedExerciseForEvaluation.value = null
                _exerciseInput.value = ""
            } catch (e: Exception) {
                _exerciseError.value = e.localizedMessage ?: "Erreur d'évaluation."
            } finally {
                _isExerciseLoading.value = false
            }
        }
    }

    // --- API KEY ACTIONS ---
    fun addApiKey(key: String, label: String) {
        val cleanKey = key.trim()
        val cleanLabel = label.trim().ifEmpty { "Clé ${apiKeys.value.size + 1}" }
        if (cleanKey.isEmpty()) return

        viewModelScope.launch {
            repository.insertApiKey(ApiKeyEntity(apiKey = cleanKey, label = cleanLabel))
        }
    }

    fun deleteApiKey(id: Long) {
        viewModelScope.launch {
            repository.deleteApiKeyById(id)
        }
    }

    // --- UPDATE SETTINGS ---
    fun updateLanguageSettings(times: String, personality: String, freq: String) {
        val lang = activeLanguage.value ?: return
        viewModelScope.launch {
            repository.updateLanguageSettings(lang.id, times, personality, freq)
            // Reschedule notification alarms
            NotificationScheduler.scheduleAlarms(getApplication(), lang.id, times)
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            val curr = settings.value
            repository.saveSettings(curr.copy(userName = name.trim().ifEmpty { "Apprenant" }))
        }
    }

    fun updateLanguageConfig(
        name: String,
        flagEmoji: String,
        level: String,
        interlocutorName: String,
        personality: String,
        times: String,
        freq: String
    ) {
        val lang = activeLanguage.value ?: return
        viewModelScope.launch {
            repository.updateFullLanguageSettings(
                id = lang.id,
                name = name.trim(),
                flagEmoji = flagEmoji.trim(),
                level = level.trim(),
                interlocutorName = interlocutorName.trim(),
                personality = personality.trim(),
                times = times,
                freq = freq
            )
            // Reschedule notification alarms
            NotificationScheduler.scheduleAlarms(getApplication(), lang.id, times)
        }
    }

    fun updateActiveModel(model: String) {
        viewModelScope.launch {
            val curr = settings.value
            repository.saveSettings(curr.copy(activeModel = model))
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            val curr = settings.value
            repository.saveSettings(curr.copy(themeMode = mode))
        }
    }

    // --- TEXT TO SPEECH (TTS) IMPLEMENTATION ---
    private fun initializeTts() {
        textToSpeech = TextToSpeech(getApplication(), this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            _isTtsReady.value = true
            Log.d("LanguageViewModel", "TTS Initialized successfully.")
        } else {
            Log.e("LanguageViewModel", "TTS Initialization failed.")
        }
    }

    fun speakText(text: String, languageCode: String) {
        val tts = textToSpeech ?: return
        if (!_isTtsReady.value) return

        viewModelScope.launch {
            val locale = when (languageCode.lowercase()) {
                "en" -> Locale.ENGLISH
                "es" -> Locale("es", "ES")
                "de" -> Locale.GERMAN
                "it" -> Locale.ITALIAN
                "ja" -> Locale.JAPANESE
                "zh" -> Locale.CHINESE
                "fr" -> Locale.FRENCH
                else -> Locale.ENGLISH
            }

            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // fallback to default
                tts.setLanguage(Locale.ENGLISH)
            }

            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ReactorsSpeech")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    // --- SPEECH TO TEXT (STT) IMPLEMENTATION ---
    private fun initializeStt() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(getApplication())) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _recognizedText.value = "Écoute en cours..."
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            _isRecording.value = false
                        }

                        override fun onError(error: Int) {
                            _isRecording.value = false
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Erreur audio"
                                SpeechRecognizer.ERROR_CLIENT -> "Erreur client"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions insuffisantes"
                                SpeechRecognizer.ERROR_NETWORK -> "Erreur réseau"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout réseau"
                                SpeechRecognizer.ERROR_NO_MATCH -> "Aucune correspondance"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Service occupé"
                                SpeechRecognizer.ERROR_SERVER -> "Erreur serveur"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Aucun son détecté"
                                else -> "Erreur inconnue"
                            }
                            _recognizedText.value = ""
                            Log.e("LanguageViewModel", "STT Error: $errorMsg ($error)")
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val text = matches[0]
                                _chatInput.value = text
                                _recognizedText.value = ""
                                // Automatically send if desired, or let user edit: we let user edit/send
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
        } catch (e: Exception) {
            Log.e("LanguageViewModel", "STT Initialization error", e)
        }
    }

    fun startListening(languageCode: String) {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            // Simulated voice message insertion for emulators or unsupported environments
            _isRecording.value = true
            _recognizedText.value = "Simulation d'enregistrement vocal..."
            viewModelScope.launch {
                kotlinx.coroutines.delay(2500)
                _isRecording.value = false
                val simulatedText = when (languageCode.lowercase()) {
                    "en" -> "Hello Sarah! How is the weather in London today?"
                    "es" -> "¡Hola Mateo! Me gustaría aprender español con tus consejos."
                    "de" -> "Hallo Hans! Wie geht es dir heute in Berlin?"
                    "it" -> "Ciao Giulia! Che bello parlare italiano con te."
                    "ja" -> "こんにちはゆきさん！"
                    else -> "Hello my friend!"
                }
                _chatInput.value = simulatedText
                _recognizedText.value = ""
            }
            return
        }

        _isRecording.value = true
        _recognizedText.value = "Écoute..."

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val localeStr = when (languageCode.lowercase()) {
                "en" -> "en-US"
                "es" -> "es-ES"
                "de" -> "de-DE"
                "it" -> "it-IT"
                "ja" -> "ja-JP"
                "zh" -> "zh-CN"
                else -> "en-US"
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeStr)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isRecording.value = false
    }

    // --- CLEAN UP ---
    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
    }
}
