package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "reactors_wakeup_channel"
        const val NOTIFICATION_ID_BASE = 2000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val languageId = intent.getStringExtra("language_id") ?: return
        val alarmIndex = intent.getIntExtra("alarm_index", 0)

        // Run coroutine on IO thread to query database and fire notification
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val language = database.languageDao().getLanguageById(languageId) ?: return@launch

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // Create channel for Android 8.0+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Rappels d'apprentissage",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notifications de rappel pour pratiquer la langue cible"
                    }
                    notificationManager.createNotificationChannel(channel)
                }

                // Create launcher intent
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    alarmIndex,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Select a message content based on the language/interlocutor
                val title = "${language.flagEmoji} Message de ${language.interlocutorName} (${language.name})"
                val messages = listOf(
                    "Hey ! C'est le moment idéal pour faire notre petite discussion du jour ! 💬😊",
                    "Tu as 5 minutes ? Viens pratiquer mon ami ! J'ai hâte de discuter avec toi. 🌟",
                    "Coucou ! Ne perdons pas notre rythme d'apprentissage. Raconte-moi ta journée ! ✨",
                    "C'est l'heure de notre défi ! Viens faire un exercice ou discuter un moment. 💪🔥"
                )
                val text = messages[alarmIndex % messages.size]

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now) // standard fallback icon
                    .setContentTitle(title)
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

                notificationManager.notify(NOTIFICATION_ID_BASE + alarmIndex, notification)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
