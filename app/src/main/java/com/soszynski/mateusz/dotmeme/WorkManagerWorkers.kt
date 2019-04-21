package com.soszynski.mateusz.dotmeme

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.realm.Realm
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.runOnUiThread
import java.io.File
import kotlin.random.Random

class FullSyncWorker(private val ctx: Context, workerParams: WorkerParameters) : Worker(ctx, workerParams) {
    companion object {
        const val UNIQUE_WORK_NAME = "unique_work_name_full_sync"
    }

    override fun doWork(): Result {
        var finished = false

        val prefs = ctx.defaultSharedPreferences


        val notification = NotificationCompat.Builder(ctx, Notifs.CHANNEL_ID_SYNCING)
            .setChannelId(Notifs.CHANNEL_ID_SYNCING)
            .setSmallIcon(R.drawable.ic_launcher_icon)
            .setColor(ctx.getColor(R.color.colorPrimary))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentTitle("Work manager working")
            .build()
        NotificationManagerCompat.from(ctx).notify(Random.nextInt(), notification)

        ctx.runOnUiThread {
            val realm = Realm.getDefaultInstance()
            val memebase = Memebase()
            memebase.syncAllFolders(realm, ctx) { newFolders ->

                if (newFolders.count() > 0 &&
                    prefs.getBoolean(
                        Prefs.Keys.SHOW_NEW_FOLDER_NOTIFICATION,
                        Prefs.Defaults.SHOW_NEW_FOLDER_NOTIFICATION
                    )
                ) {
                    for ((index, folder) in newFolders.withIndex()) {
                        val id =
                            Notifs.NOTIFICATION_ID_NEW_FOLDER + index + 1 // because we want separate notifications
                        NotificationManagerCompat.from(ctx).notify(
                            id,
                            Notifs.getNewFolderFoundNotification(ctx, File(folder.folderPath), id)
                        )
                    }
                }

                // foreground service is running, so we don't need to scan
                if (FullMemeSyncIntentService.isRunning(ctx)) {
                    finished = true
                    return@syncAllFolders
                }

                memebase.scanAllFolders(realm,
                    { memeFolder: MemeFolder, all: Int, progress: Int ->
                        // progress
                        // we have a bigger job to do here, so we will let foreground service do this
                        if (all - progress > 15) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                startForegroundService(Intent(ctx, FullMemeSyncIntentService::class.java))
                            } else {
                                startService(Intent(ctx, FullMemeSyncIntentService::class.java))
                            }
                            memebase.scanningCanceled = true
                        }
                    },
                    {
                        // finished
                        finished = true
                    }
                )
            }
        }
        while (!finished);
        return Result.success()
    }
}