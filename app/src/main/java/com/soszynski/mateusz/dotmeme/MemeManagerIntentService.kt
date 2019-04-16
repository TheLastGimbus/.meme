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
                handleActionSyncAll()
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
        memebase.syncAllFolders(realm, this) {
            Log.i(TAG, "Global sync finished!")

            recursiveScanAllFolders {
                Log.i(TAG, "Scanning folders finished!")
                stopSelf(syncAllStartId)
            }
        }
    }

    private fun recursiveScanAllFolders(finished: () -> Unit) {
        if (
            prefs.getBoolean(Prefs.PREF_SCANNING_PAUSED, Prefs.PREF_SCANNING_PAUSED_default)
            ||
            memebase.scanningCanceled
        ) {
            stopForeground(true)
            finished()
            return
        }

        var folderToScan: MemeFolder? = null
        for (folder in realm.where(MemeFolder::class.java)
            .equalTo(MemeFolder.IS_SCANNABLE, true)
            .findAll()
        ) {
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

        memebase.scanFolder(
            realm, folderToScan,
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
