package com.soszynski.mateusz.dotmeme

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import io.realm.Realm
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File


class FullMemeSyncIntentService : Service() {
    private val mBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        val service: FullMemeSyncIntentService
            get() = this@FullMemeSyncIntentService
    }

    override fun onBind(intent: Intent?): IBinder? {
        return mBinder
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            Notifs.NOTIFICATION_ID_SYNCING,
            Notifs.getScanningForegroundNotification(this, null, null, null)
        )
        fullSync()
    }

    private fun fullSync() {
        val prefs = defaultSharedPreferences
        val realm = Realm.getDefaultInstance()
        val memebase = Memebase()
        memebase.syncAllFolders(realm, this) { newFolders ->

            if (newFolders.count() > 0 &&
                prefs.getBoolean(
                    Prefs.Keys.SHOW_NEW_FOLDER_NOTIFICATION,
                    Prefs.Defaults.SHOW_NEW_FOLDER_NOTIFICATION
                )
            ) {
                for ((index, folder) in newFolders.withIndex()) {
                    val id =
                        Notifs.NOTIFICATION_ID_NEW_FOLDER + index + 1 // because we want separate notifications
                    NotificationManagerCompat.from(this).notify(
                        id,
                        Notifs.getNewFolderFoundNotification(this, File(folder.folderPath), id)
                    )
                }
            }

            memebase.scanAllFolders(realm,
                { memeFolder: MemeFolder, all: Int, progress: Int ->
                    // progress
                    startForeground(
                        Notifs.NOTIFICATION_ID_SYNCING,
                        Notifs.getScanningForegroundNotification(
                            this,
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

    companion object {
        fun isRunning(ctx: Context, needToBeForeground: Boolean = true): Boolean {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager? ?: return false
            val runningServices = am.getRunningServices(100)
            for (serviceInfo in runningServices) {
                if (serviceInfo.service.className == FullMemeSyncIntentService::class.java.name) {
                    return if (needToBeForeground) serviceInfo.foreground else true
                }
            }
            return false
        }
    }
}