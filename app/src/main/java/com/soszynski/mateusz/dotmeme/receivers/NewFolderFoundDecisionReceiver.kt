package com.soszynski.mateusz.dotmeme.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.soszynski.mateusz.dotmeme.MemeFolder
import com.soszynski.mateusz.dotmeme.memebase.Memebase
import io.realm.Realm

class NewFolderFoundDecisionReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "NewFolderReceiver"
        const val EXTRA_FOLDER_PATH = "extra_folder_path"
        const val EXTRA_SYNC_BOOL = "extra_sync_bool"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        Memebase.handleRealmConfigs()
        val realm = Realm.getDefaultInstance()

        val folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH)
        val toSave = intent.getBooleanExtra(EXTRA_SYNC_BOOL, false)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val folder = realm.where(MemeFolder::class.java).equalTo(MemeFolder.FOLDER_PATH, folderPath).findFirst()
        if (folder == null) {
            Log.e(TAG, "No folder with path '$folderPath' found in memebase")
        } else {
            realm.executeTransaction {
                folder.isScannable = toSave
            }
        }
        NotificationManagerCompat.from(ctx).cancel(notificationId)

        realm.close()
    }
}
