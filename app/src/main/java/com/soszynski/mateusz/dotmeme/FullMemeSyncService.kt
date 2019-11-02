package com.soszynski.mateusz.dotmeme

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import io.realm.Realm
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File


class FullMemeSyncService : Service() {
    private val mBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: FullMemeSyncService
            get() = this@FullMemeSyncService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Full sync foreground service started")
        if (!PainKiller().hasStoragePermission(this)) {
            Log.w(TAG, "Can't start foreground service: no storage permission!")
            return
        }

        startForeground(
            Notifs.NOTIFICATION_ID_SYNCING,
            Notifs.getScanningForegroundNotification(this, null, null, null)
        )
        fullSync()
    }

    private fun fullSync() {
        doAsync {
            val prefs = defaultSharedPreferences

            Memebase.handleRealmConfigs()
            val realm = Realm.getDefaultInstance()
            val memebase = Memebase()

            Log.i(TAG, "Syncing all folders...")
            val newFolders = memebase.syncAllFolders(
                realm,
                this@FullMemeSyncService,
                syncUnofficialFoldersIndex = false
            )

            if (newFolders.count() > 0 &&
                prefs.getBoolean(
                    Prefs.Keys.SHOW_NEW_FOLDER_NOTIFICATION,
                    Prefs.Defaults.SHOW_NEW_FOLDER_NOTIFICATION
                )
            ) {
                Log.i(TAG, "Showing notifications about new folders")
                for ((index, folder) in newFolders.withIndex()) {
                    val id =
                        Notifs.NOTIFICATION_ID_NEW_FOLDER + index + 1 // because we want separate notifications
                    NotificationManagerCompat.from(this@FullMemeSyncService).notify(
                        id,
                        Notifs.getNewFolderFoundNotification(
                            this@FullMemeSyncService,
                            File(folder.folderPath),
                            id
                        )
                    )
                }
            }

            uiThread {
                val realm = Realm.getDefaultInstance()
                Log.i(TAG, "Scanning all folders")
                memebase.scanAllFolders(
                    realm, this@FullMemeSyncService,
                    { memeFolder: MemeFolder, all: Int, progress: Int ->
                        // progress
                        startForeground(
                            Notifs.NOTIFICATION_ID_SYNCING,
                            Notifs.getScanningForegroundNotification(
                                this@FullMemeSyncService,
                                File(memeFolder.folderPath).name,
                                progress,
                                all
                            )
                        )
                    },
                    {
                        // finished
                        stopForeground(true)
                        stopSelf()
                    }
                )
            }
        }
    }

    companion object {
        const val TAG = "FullMemeSyncService"

        fun start(ctx: Context) {
            if (isRunning(ctx)) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(Intent(ctx, FullMemeSyncService::class.java))
            } else {
                ctx.startService(Intent(ctx, FullMemeSyncService::class.java))
            }
        }

        fun isRunning(ctx: Context, needToBeForeground: Boolean = true): Boolean {
            val am =
                ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager? ?: return false
            val runningServices = am.getRunningServices(100)
            for (serviceInfo in runningServices) {
                if (serviceInfo.service.className == FullMemeSyncService::class.java.name) {
                    return if (needToBeForeground) serviceInfo.foreground else true
                }
            }
            return false
        }
    }
}
