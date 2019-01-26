package com.soszynski.mateusz.dotmeme

import android.app.IntentService
import android.app.Service
import android.content.Context
import android.content.Intent
import android.util.Log
import io.realm.Realm
import org.jetbrains.anko.runOnUiThread
import java.io.File

private const val ACTION_SYNC_ALL = "com.soszynski.mateusz.dotmeme.action.SYNC_ALL"

class MemeManagerIntentService : IntentService("MemeManagerIntentService") {
    lateinit var realm: Realm
    var lastStartId: Int = 0

    override fun onCreate() {
        super.onCreate()
        realm = Realm.getDefaultInstance()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        realm.close()
    }

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SYNC_ALL -> {
                handleActionSyncAll()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        onHandleIntent(intent)
        return Service.START_REDELIVER_INTENT
    }

    private fun handleActionSyncAll() {
        runOnUiThread {
            Memebase().syncAllFolders(realm, this) {
                Log.i(TAG, "Global sync finished!")

                recursiveScanAllFolders {
                    Log.i(TAG, "Scanning folders finished!")
                    stopSelf(lastStartId)
                }
            }
        }
    }

    private fun recursiveScanAllFolders(finished: () -> Unit) {
        var folderToScan: MemeFolder? = null
        for (folder in realm.where(MemeFolder::class.java).findAll()) {
            if (!MemeFolder.isFolderFullyScanned(folder)) {
                folderToScan = folder
                break
            }
        }
        if (folderToScan == null) {
            stopForeground(true)
            finished()
            return
        }

        val folderName = File(folderToScan.folderPath).name

        Memebase().scanFolder(realm, folderToScan,
            { max, progress ->
                // progress
                val notification = Notifications().getScanningForegroundNotification(
                    this,
                    folderName,
                    progress,
                    max
                )
                Log.i(TAG, "Scanning folder $folderName, progress: $progress, max: $max")
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            },
            {
                // finished
                Log.i(TAG, "Finished scanning folder $folderName")
                recursiveScanAllFolders(finished)
            })
    }

    companion object {
        val TAG = "MemeManager"
        val FOREGROUND_NOTIFICATION_ID = 15

        @JvmStatic
        fun startActionSyncAll(context: Context) {
            val intent = Intent(context, MemeManagerIntentService::class.java).apply {
                action = ACTION_SYNC_ALL
            }
            context.startService(intent)
        }
    }
}
