package com.soszynski.mateusz.dotmeme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.app.NotificationCompat
import androidx.annotation.RequiresApi


/*
 * All stuff related to notifications.
 */

class Notifications {
    companion object {
        val CHANNEL_ID_SYNCING = "channel_id_syncing"
        val NOTIFICATION_ID_SYNCING = 15
    }

    fun notifyNewFoldersFound(ctx: Context) {

    }

    // TODO: Change placeholders to fancy things
    fun getScanningForegroundNotification(
        ctx: Context,
        folderName: String?,
        progress: Int?,
        max: Int?
    ): Notification {
        val piMain = PendingIntent
            .getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), 0)
        val piPause = PendingIntent.getService(
            ctx,
            0,
            Intent(ctx, MemeManagerIntentService::class.java).apply {
                action = MemeManagerIntentService.ACTION_PAUSE
            },
            0
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID_SYNCING)
            .setChannelId(CHANNEL_ID_SYNCING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentTitle("Scanning memes...")
            .setContentIntent(piMain)

        if (!folderName.isNullOrEmpty() && max != null && progress != null) {
            builder
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Folder: $folderName \nScanned: $progress, all: $max")
                )
                .setProgress(max, progress, false)
                .addAction(0, "Pause", piPause)
        }

        return builder.build()
    }

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(
                ctx,
                CHANNEL_ID_SYNCING,
                "Meme sync",
                null,
                NotificationManager.IMPORTANCE_LOW
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(
        ctx: Context,
        id: String,
        name: String,
        description: String?,
        importance: Int = NotificationManager.IMPORTANCE_DEFAULT
    ) {
        val notificationManager =
            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val mChannel = NotificationChannel(id, name, importance)
        if (description.isNullOrEmpty() == false) {
            mChannel.description = description
        }
        notificationManager.createNotificationChannel(mChannel)
    }
}