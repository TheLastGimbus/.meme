package com.soszynski.mateusz.dotmeme

import android.app.IntentService
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import io.realm.Realm
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File


/**
 * Main service that will do all the job of syncing memes in the background.
 */

class MemeManagerIntentService : IntentService("MemeManagerIntentService"),
    SharedPreferences.OnSharedPreferenceChangeListener {
    lateinit var realm: Realm
    private val memebase = Memebase()
    private lateinit var prefs: SharedPreferences
    private var syncAllStartId: Int = 0
    var isBusy = false

    override fun onCreate() {
        super.onCreate()
        realm = Realm.getDefaultInstance()
        prefs = defaultSharedPreferences
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        realm.close()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SYNC_ALL -> {
                if (!isBusy) {
                    handleActionSyncAll()
                }
            }
            ACTION_PAUSE -> {
                defaultSharedPreferences.edit()
                    .putBoolean(Prefs.PREF_SCANNING_PAUSED, true)
                    .apply()
                memebase.scanningCanceled = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SYNC_ALL) {
            syncAllStartId = startId
        }
        onHandleIntent(intent)
        return Service.START_REDELIVER_INTENT
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Prefs.PREF_SCANNING_PAUSED) {
            memebase.scanningCanceled = prefs.getBoolean(Prefs.PREF_SCANNING_PAUSED, Prefs.PREF_SCANNING_PAUSED_default)
        }
    }

    private fun handleActionSyncAll() {
        isBusy = true

        memebase.syncAllFolders(realm, this) {
            Log.i(TAG, "Global sync finished!")

            if (prefs.getBoolean(Prefs.PREF_SCANNING_PAUSED, Prefs.PREF_SCANNING_PAUSED_default)) {
                isBusy = false
                stopForeground(true)
                stopSelf(syncAllStartId)
            } else {
                memebase.scanAllFolders(realm,
                    { folder, max, progress ->
                        // progress
                        val notification = Notifications().getScanningForegroundNotification(
                            this,
                            File(folder.folderPath).name,
                            progress,
                            max
                        )
                        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
                    },
                    {
                        // finished
                        Log.i(TAG, "Scanning folders finished!")
                        isBusy = false
                        stopForeground(true)
                        stopSelf(syncAllStartId)
                    }
                )
            }
        }
    }

    companion object {
        const val TAG = "MemeManager"

        const val ACTION_SYNC_ALL = "service.action.SYNC_ALL"
        const val ACTION_PAUSE = "service.action.PAUSE"

        const val FOREGROUND_NOTIFICATION_ID = 15

        @JvmStatic
        fun startActionSyncAll(context: Context) {
            val intent = Intent(context, MemeManagerIntentService::class.java).apply {
                action = ACTION_SYNC_ALL
            }
            context.startService(intent)
        }
    }
}
