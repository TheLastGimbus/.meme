package com.soszynski.mateusz.dotmeme

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.realm.Realm
import org.jetbrains.anko.runOnUiThread

class FullSyncWorker(private val ctx: Context, workerParams: WorkerParameters) : Worker(ctx, workerParams) {
    override fun doWork(): Result {
        var finished = false

        ctx.runOnUiThread {
            val realm = Realm.getDefaultInstance()
            val memebase = Memebase()
            memebase.syncAllFolders(realm, ctx) {
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