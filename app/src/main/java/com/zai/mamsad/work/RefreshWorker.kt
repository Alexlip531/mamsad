package com.zai.mamsad.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zai.mamsad.MainActivity
import com.zai.mamsad.MamsadApp
import com.zai.mamsad.R

/**
 * Periodic worker that:
 *   1. Refreshes the org cache from mamsad.ru
 *   2. If new orgs appeared since last check, posts a notification
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MamsadApp ?: return Result.success()
        val repo = app.repository

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty()
            .mapNotNull { it.toIntOrNull() }.toSet()

        // Refresh
        val result = repo.fetchOrgs()
        if (result.isFailure) return Result.retry()

        val current = result.getOrNull().orEmpty()

        // Find newly added orgs
        val newOnes = if (lastSeen.isEmpty()) {
            // First run — don't notify
            emptyList()
        } else {
            current.map { it.id }.filter { it !in lastSeen }
        }

        // Save current ids as last seen
        prefs.edit().putStringSet(KEY_SEEN_IDS, current.map { it.id.toString() }.toSet()).apply()

        if (newOnes.isNotEmpty()) {
            val titles = current.filter { it.id in newOnes }.take(3)
                .joinToString(", ") { "«${it.title}»" }
            showNotification(applicationContext, newOnes.size, titles)
        }

        return Result.success()
    }

    private fun showNotification(context: Context, count: Int, titles: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = context.resources.getQuantityString(
            R.plurals.notif_new_kindergartens_title, count, count
        )
        val text = if (titles.isNotBlank()) {
            context.getString(R.string.notif_new_text, titles)
        } else {
            context.getString(R.string.notif_new_text_empty)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_logo_white)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setColor(context.getColor(R.color.mamsad_coral))
            .build()

        nm.notify(NOTIF_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "mamsad_new_orgs"
        const val NOTIF_ID = 1001
        const val PREFS = "mamsad_prefs"
        const val KEY_SEEN_IDS = "seen_org_ids"
        const val UNIQUE_WORK_NAME = "mamsad_refresh_work"
    }
}
