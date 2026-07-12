package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

object NotificationScheduler {
    private const val TAG = "NotificationScheduler"

    fun scheduleAlarms(context: Context, languageId: String, wakeUpTimesStr: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        cancelAlarms(context) // clear first

        val times = wakeUpTimesStr.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains(":") }

        times.forEachIndexed { index, timeStr ->
            try {
                val parts = timeStr.split(":")
                val hour = parts[0].toIntOrNull() ?: return@forEachIndexed
                val minute = parts[1].toIntOrNull() ?: return@forEachIndexed

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)

                    // If time is in the past, set to tomorrow
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("language_id", languageId)
                    putExtra("alarm_index", index)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Set repeating alarm
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )

                Log.d(TAG, "Scheduled alarm $index for language $languageId at $hour:$minute")

            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling alarm at index $index: $timeStr", e)
            }
        }
    }

    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (index in 0 until 4) {
            try {
                val intent = Intent(context, AlarmReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    index,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                    Log.d(TAG, "Cancelled alarm $index")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling alarm $index", e)
            }
        }
    }
}
