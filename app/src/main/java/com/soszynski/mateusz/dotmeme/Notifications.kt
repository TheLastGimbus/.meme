package com.soszynski.mateusz.dotmeme

import android.app.Notification
import android.content.Context
import android.support.v4.app.NotificationCompat

/*
 * All stuff related to notifications.
 */

class Notifications {
    val CHANNEL_ID_SCANNING = "channel_id_scanning"
    val NOTI_ID_SCANNING = 10

    fun notifyNewFoldersFound(ctx: Context) {

    }

    // TODO: Change placeholders to fancy things
    fun getScanningForegroundNotification(ctx: Context, folderName: String, max: Int, progress: Int): Notification {
        var builder = NotificationCompat.Builder(ctx, CHANNEL_ID_SCANNING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Scanning...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setProgress(max, progress, false)

        return builder.build()
    }
}