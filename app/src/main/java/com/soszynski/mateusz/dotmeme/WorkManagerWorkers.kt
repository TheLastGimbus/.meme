package com.soszynski.mateusz.dotmeme

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.realm.Realm
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.runOnUiThread
import java.io.File

class FullSyncWorker(private val ctx: Context, workerParams: WorkerParameters) : Worker(ctx, workerParams) {
    companion object {
        const val TAG = "FullSyncWorker"
        const val UNIQUE_WORK_NAME = "unique_work_name_full_sync"

        fun isScheduled(): Boolean {
            try {
                val workInfoList = WorkManager.getInstance()
                    .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
                for (workInfo in workInfoList) {
                    if (workInfo.state != WorkInfo.State.CANCELLED) {
                        return true
                    }
                }
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    }

    override fun doWork(): Result {
        if (!PainKiller().hasStoragePermission(ctx)) {
            Log.w(TAG, "Can't start full sync work: no storage permission!")
            return Result.retry()
        }

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

                // foreground service is running, so we don't need to scan
                if (FullMemeSyncService.isRunning(ctx)) {
                    finished = true
                    return@syncAllFolders
                }

                memebase.scanAllFolders(
                    realm, ctx,
                    { memeFolder: MemeFolder, all: Int, progress: Int ->
                        // progress
                        // we have a bigger job to do here, so we will let foreground service do this
                        if (all - progress > 10) {
                            FullMemeSyncService.start(this)
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