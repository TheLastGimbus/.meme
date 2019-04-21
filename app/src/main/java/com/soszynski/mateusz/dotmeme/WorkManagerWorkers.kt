package com.soszynski.mateusz.dotmeme

import android.content.Context
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

                memebase.scanAllFolders(realm,
                    { memeFolder: MemeFolder, all: Int, progress: Int ->
                        // progress
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