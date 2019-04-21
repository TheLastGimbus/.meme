package com.soszynski.mateusz.dotmeme

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.File
import kotlin.random.Random


/**
 * All stuff related to notifications.
 */

class Notifs {
    companion object {
        const val CHANNEL_ID_SYNCING = "channel_id_syncing"
        const val NOTIFICATION_ID_SYNCING = 15
        const val CHANNEL_ID_NEW_FOLDER = "channel_id_new__folder"
        const val NOTIFICATION_ID_NEW_FOLDER = 2500


        /**
         * @param ctx [Context]
         * @param folderName name of folder that is currently being scanned.
         * @param progress number of scanned memes.
         * @param max number of all memes to scan.
         * @return notification indicating that scanning memes is in progress.
         */
        fun getScanningForegroundNotification(
            ctx: Context,
            folderName: String?,
            progress: Int?,
            max: Int?
        ): Notification {
            val piMain = PendingIntent
                .getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), 0)

            val builder = NotificationCompat.Builder(ctx, CHANNEL_ID_SYNCING)
                .setChannelId(CHANNEL_ID_SYNCING)
                .setSmallIcon(R.drawable.ic_launcher_icon)
                .setColor(ctx.getColor(R.color.colorPrimary))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentTitle("Scanning memes...")
                .setContentIntent(piMain)

            if (!folderName.isNullOrEmpty()) {
                builder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Folder: $folderName \nScanned: $progress, all: $max")
                )
            }
            if (max != null && progress != null) {
                builder.setProgress(max, progress, false)
            }

            return builder.build()
        }

        fun getNewFolderFoundNotification(
            ctx: Context,
            folder: File,
            notificationId: Int
        ): Notification {
            // TODO: Highlight folder in SettingsActivity

            val mainPi = PendingIntent.getActivity(
                ctx,
                0,
                Intent(ctx, SettingsActivity::class.java),
                0
            )
            val brdIntent = Intent(ctx, NewFolderFoundDecisionReceiver::class.java).apply {
                putExtra(NewFolderFoundDecisionReceiver.EXTRA_FOLDER_PATH, folder.absolutePath)
                putExtra(NewFolderFoundDecisionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            }
            val yesPi = PendingIntent.getBroadcast(
                ctx,
                Random.nextInt(),
                brdIntent.apply { action = "yes"; putExtra(NewFolderFoundDecisionReceiver.EXTRA_SYNC_BOOL, true) },
                0
            )
            val noPi = PendingIntent.getBroadcast(
                ctx,
                Random.nextInt(),
                brdIntent.apply { action = "no"; putExtra(NewFolderFoundDecisionReceiver.EXTRA_SYNC_BOOL, false) },
                0
            )

            val builder = NotificationCompat.Builder(ctx, CHANNEL_ID_NEW_FOLDER)
                .setChannelId(CHANNEL_ID_NEW_FOLDER)
                .setSmallIcon(R.drawable.ic_launcher_icon)
                .setColor(ctx.getColor(R.color.colorPrimary))
                .setContentTitle(ctx.getString(R.string.notif_title_new_folder))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(ctx.getString(R.string.notif_desc_new_folder).replace("[folder_name]", folder.name))
                )
                .addAction(0, ctx.getString(R.string.yes), yesPi)
                .addAction(0, ctx.getString(R.string.no), noPi)
                .setContentIntent(mainPi)

            return builder.build()
        }

        fun createChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createChannel(
                    ctx,
                    CHANNEL_ID_SYNCING,
                    ctx.getString(R.string.notif_channel_meme_sync),
                    null,
                    NotificationManager.IMPORTANCE_LOW
                )
                createChannel(
                    ctx,
                    CHANNEL_ID_NEW_FOLDER,
                    ctx.getString(R.string.notif_channel_new_folder),
                    null,
                    NotificationManager.IMPORTANCE_DEFAULT
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
            if (!description.isNullOrEmpty()) {
                mChannel.description = description
            }
            notificationManager.createNotificationChannel(mChannel)
        }
    }
}