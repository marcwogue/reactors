package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// --- DATA MODELS FOR CHAT COMPLETIONS ---

data class ChatMessage(
    val role: String, // "system", "user", "assistant"
    val content: String
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 1000,
    val temperature: Double = 0.7
)

data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String?
)

data class ChatCompletionResponse(
    val id: String?,
    val choices: List<ChatChoice>?,
    val created: Long?
)

// --- RETROFIT SERVICE ---

interface HuggingFaceService {
    @POST("models/{modelId}/v1/chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String,
        @Path("modelId", encoded = true) modelId: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

// --- API CLIENT ---

object HuggingFaceClient {
    private const val BASE_URL = "https://api-inference.huggingface.co/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: HuggingFaceService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(HuggingFaceService::class.java)
    }
}

// --- HELPER CLASSES FOR AI RESPONSES ---

data class ChatCorrectionResponse(
    val correction: String?,
    val explanation: String?,
    val reply: String
) {
    companion object {
        fun parse(rawText: String): ChatCorrectionResponse {
            var correction: String? = null
            var explanation: String? = null
            var reply = rawText

            val correctionRegex = "\\[CORRECTION\\](.*?)(?=\\[EXPLANATION\\]|\\[REPLY\\]|$)".toRegex(RegexOption.DOT_MATCHES_ALL)
            val explanationRegex = "\\[EXPLANATION\\](.*?)(?=\\[CORRECTION\\]|\\[REPLY\\]|$)".toRegex(RegexOption.DOT_MATCHES_ALL)
            val replyRegex = "\\[REPLY\\](.*)".toRegex(RegexOption.DOT_MATCHES_ALL)

            val correctionMatch = correctionRegex.find(rawText)
            val explanationMatch = explanationRegex.find(rawText)
            val replyMatch = replyRegex.find(rawText)

            if (correctionMatch != null) {
                val extracted = correctionMatch.groupValues[1].trim()
                if (extracted.isNotEmpty() && !extracted.equals("NONE", ignoreCase = true) && !extracted.equals("EMPTY", ignoreCase = true)) {
                    correction = extracted
                }
            }
            if (explanationMatch != null) {
                val extracted = explanationMatch.groupValues[1].trim()
                if (extracted.isNotEmpty() && !extracted.equals("NONE", ignoreCase = true) && !extracted.equals("EMPTY", ignoreCase = true)) {
                    explanation = extracted
                }
            }
            if (replyMatch != null) {
                reply = replyMatch.groupValues[1].trim()
            } else {
                // Fallback: strip tags and treat everything as reply
                reply = rawText
                    .replace(correctionRegex, "")
                    .replace(explanationRegex, "")
                    .replace("[REPLY]", "")
                    .trim()
            }

            return ChatCorrectionResponse(correction, explanation, reply)
        }
    }
}

data class ExerciseGenerationResponse(
    val type: String,
    val instruction: String,
    val questions: String
) {
    companion object {
        fun parse(rawText: String): ExerciseGenerationResponse {
            var type = "Grammaire"
            var instruction = "Complétez l'exercice suivant"
            var questions = rawText

            val exerciseRegex = "\\[EXERCISE\\](.*)".toRegex(RegexOption.DOT_MATCHES_ALL)
            val match = exerciseRegex.find(rawText)
            val body = if (match != null) match.groupValues[1].trim() else rawText

            // Parse simple lines
            val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val typeLine = lines.firstOrNull { it.contains("Type d'exercice", ignoreCase = true) || it.contains("Type", ignoreCase = true) }
            if (typeLine != null) {
                type = typeLine.substringAfter(":").trim()
            }

            val instructionLine = lines.firstOrNull { it.contains("Consigne", ignoreCase = true) }
            if (instructionLine != null) {
                instruction = instructionLine.substringAfter(":").trim()
            }

            // Extract questions (all lines starting with a number or simply everything that isn't the header lines)
            val questionLines = lines.filter {
                !it.contains("Type d'exercice", ignoreCase = true) &&
                !it.contains("Type:", ignoreCase = true) &&
                !it.contains("Consigne", ignoreCase = true) &&
                !it.startsWith("[EXERCISE")
            }
            if (questionLines.isNotEmpty()) {
                questions = questionLines.joinToString("\n")
            }

            return ExerciseGenerationResponse(type, instruction, questions)
        }
    }
}

data class ExerciseEvaluationResponse(
    val grade: Int,
    val feedback: String
) {
    companion object {
        fun parse(rawText: String): ExerciseEvaluationResponse {
            var grade = 80 // default fallback
            var feedback = rawText

            val gradeRegex = "\\[GRADE\\](.*?)(?=\\[CORRECTION\\]|$)".toRegex(RegexOption.DOT_MATCHES_ALL)
            val correctionRegex = "\\[CORRECTION\\](.*)".toRegex(RegexOption.DOT_MATCHES_ALL)

            val gradeMatch = gradeRegex.find(rawText)
            if (gradeMatch != null) {
                val numStr = gradeMatch.groupValues[1].replace("[^0-9]".toRegex(), "").trim()
                if (numStr.isNotEmpty()) {
                    grade = numStr.toIntOrNull() ?: 80
                }
            }

            val correctionMatch = correctionRegex.find(rawText)
            if (correctionMatch != null) {
                feedback = correctionMatch.groupValues[1].trim()
            } else {
                feedback = rawText.replace("\\[GRADE\\]\\s*\\d+".toRegex(), "").trim()
            }

            // Limit grade to valid boundaries
            grade = grade.coerceIn(0, 100)

            return ExerciseEvaluationResponse(grade, feedback)
        }
    }
}

data class WeeklyLevelEvaluationResponse(
    val level: String,
    val justification: String
) {
    companion object {
        fun parse(rawText: String, defaultLevel: String): WeeklyLevelEvaluationResponse {
            var level = defaultLevel
            var justification = rawText

            val levelRegex = "\\[LEVEL\\](.*?)(?=\\[JUSTIFICATION\\]|$)".toRegex(RegexOption.DOT_MATCHES_ALL)
            val justificationRegex = "\\[JUSTIFICATION\\](.*)".toRegex(RegexOption.DOT_MATCHES_ALL)

            val levelMatch = levelRegex.find(rawText)
            if (levelMatch != null) {
                val extracted = levelMatch.groupValues[1].trim().uppercase()
                val levels = listOf("A1", "A2", "B1", "B2", "C1", "C2")
                if (extracted in levels) {
                    level = extracted
                }
            }

            val justificationMatch = justificationRegex.find(rawText)
            if (justificationMatch != null) {
                justification = justificationMatch.groupValues[1].trim()
            } else {
                justification = rawText.replace("\\[LEVEL\\]\\s*[A-Z0-9]+".toRegex(), "").trim()
            }

            return WeeklyLevelEvaluationResponse(level, justification)
        }
    }
}
