package com.soszynski.mateusz.dotmeme

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.util.Log
import io.realm.Realm
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File

private const val ACTION_SYNC_ALL = "com.soszynski.mateusz.dotmeme.action.SYNC_ALL"

class MemeManagerIntentService : IntentService("MemeManagerIntentService") {
    lateinit var realm: Realm

    override fun onCreate() {
        super.onCreate()
        realm = Realm.getDefaultInstance()
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SYNC_ALL -> {
                handleActionSyncAll()
            }
        }
    }

    private fun handleActionSyncAll() {
        doAsync {
            val paths = PainKiller().getAllFoldersWithImages(this@MemeManagerIntentService)
                .map(File::getAbsolutePath)

            uiThread {
                Memebase().syncFoldersIndex(realm, paths)
                Log.i(TAG, "Begining syncing folders")
                val memeFolders = realm.where(MemeFolder::class.java).findAll()
                for (folder in memeFolders) {
                    Memebase().syncFolder(realm, folder)
                }
                Log.i(TAG, "End syncing")
            }
        }
    }

    companion object {
        val TAG = "MemeManager"

        @JvmStatic
        fun startActionSyncAll(context: Context) {
            val intent = Intent(context, MemeManagerIntentService::class.java).apply {
                action = ACTION_SYNC_ALL
            }
            context.startService(intent)
        }
    }
}
