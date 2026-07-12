package com.example.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---

@Entity(tableName = "languages")
data class LanguageEntity(
    @PrimaryKey val id: String, // e.g., "en", "es", "de", "it", "ja", "zh"
    val name: String,
    val flagEmoji: String,
    val currentLevel: String = "A1", // A1, A2, B1, B2, C1, C2
    val interlocutorName: String,
    val interlocutorPersonality: String,
    val wakeUpTimes: String = "09:00,14:00,18:00,21:00", // Comma-separated wake-up times (up to 4)
    val exerciseFrequency: String = "Chaque jour", // "Chaque jour", "Tous les 2 jours", "Chaque message"
    val isActive: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val languageId: String,
    val sender: String, // "user", "assistant_reply", "assistant_correction"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isVoice: Boolean = false
)

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val languageId: String,
    val type: String, // e.g., "Grammaire", "Vocabulaire", "Traduction"
    val instruction: String,
    val questions: String, // Stored as formatted text or simple JSON
    val userAnswer: String? = null,
    val aiFeedback: String? = null,
    val grade: Int? = null, // Score out of 100
    val timestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false
)

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiKey: String,
    val label: String
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val activeLanguageId: String? = null,
    val activeModel: String = "Qwen/Qwen2.5-7B-Instruct", // Hugging Face model
    val userName: String = "Apprenant",
    val themeMode: String = "system" // "system", "light", "dark"
)

// --- DAOs ---

@Dao
interface LanguageDao {
    @Query("SELECT * FROM languages")
    fun getAllLanguagesFlow(): Flow<List<LanguageEntity>>

    @Query("SELECT * FROM languages")
    suspend fun getAllLanguages(): List<LanguageEntity>

    @Query("SELECT * FROM languages WHERE id = :id LIMIT 1")
    suspend fun getLanguageById(id: String): LanguageEntity?

    @Query("SELECT * FROM languages WHERE id = :id LIMIT 1")
    fun getLanguageByIdFlow(id: String): Flow<LanguageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLanguage(language: LanguageEntity)

    @Query("UPDATE languages SET currentLevel = :level WHERE id = :id")
    suspend fun updateLanguageLevel(id: String, level: String)

    @Query("UPDATE languages SET wakeUpTimes = :times, interlocutorPersonality = :personality, exerciseFrequency = :freq WHERE id = :id")
    suspend fun updateLanguageSettings(id: String, times: String, personality: String, freq: String)

    @Query("UPDATE languages SET name = :name, flagEmoji = :flagEmoji, currentLevel = :level, interlocutorName = :interlocutorName, interlocutorPersonality = :personality, wakeUpTimes = :times, exerciseFrequency = :freq WHERE id = :id")
    suspend fun updateFullLanguageSettings(
        id: String,
        name: String,
        flagEmoji: String,
        level: String,
        interlocutorName: String,
        personality: String,
        times: String,
        freq: String
    )

    @Query("UPDATE languages SET isActive = (id = :activeId)")
    suspend fun setActiveLanguage(activeId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE languageId = :languageId ORDER BY timestamp ASC")
    fun getMessagesForLanguageFlow(languageId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE languageId = :languageId ORDER BY timestamp ASC")
    suspend fun getMessagesForLanguage(languageId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE languageId = :languageId")
    suspend fun clearMessagesForLanguage(languageId: String)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE languageId = :languageId ORDER BY timestamp DESC")
    fun getExercisesForLanguageFlow(languageId: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY timestamp DESC")
    fun getAllExercisesFlow(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getExerciseById(id: Long): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: ExerciseEntity)

    @Query("UPDATE exercises SET userAnswer = :answer, aiFeedback = :feedback, grade = :grade, isCompleted = 1 WHERE id = :id")
    suspend fun submitExerciseAnswer(id: Long, answer: String, feedback: String, grade: Int)
}

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY id ASC")
    fun getAllApiKeysFlow(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY id ASC")
    suspend fun getAllApiKeys(): List<ApiKeyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(key: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun deleteApiKeyById(id: Long)
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: AppSettingsEntity)
}

// --- DATABASE ---

@Database(
    entities = [
        LanguageEntity::class,
        MessageEntity::class,
        ExerciseEntity::class,
        ApiKeyEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun languageDao(): LanguageDao
    abstract fun messageDao(): MessageDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun apiKeyDao(): ApiKeyDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "polylingo_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
