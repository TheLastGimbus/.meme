package com.soszynski.mateusz.dotmeme

import android.content.Context
import io.realm.Realm
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File

/*
 * Almost all things related to database of memes.
 *
 * But it also handles higher-level UX stuff,
 * for example showing notification when new folder was found.
 * This is caused because a lot of stuff that happens here takes a lot of time,
 * but some of it (mostly Realm) needs to be done in UI thread.
 *
 * Do I need to say that almost all of this requires READ_EXTERNAL_MEMORY permission?
 */

class Memebase {
    companion object {
        val TAG = "Memebase class"
    }

    var doingStuffInAsync: Boolean = false

    fun syncAll(ctx: Context, realm: Realm) {

    }

    fun syncFoldersIndex(ctx: Context, realm: Realm) {
        ctx.doAsync {
            val paths = PainKiller().getAllFoldersWithImages(ctx).map(File::getAbsolutePath)
            uiThread {
                // realm.executeTransaction must be done in uiThread
                addNewFolders(realm, paths)
                deleteNotExistingFolders(realm, paths)
            }
        }
    }

    // Discover new folders and add them to database. TODO: Display notification if new folder was found.
    private fun addNewFolders(realm: Realm, devicePaths: List<String>) {
        realm.executeTransactionAsync { realm ->
            for (path in devicePaths) {
                val result = realm.where(MemeFolder::class.java)
                    .equalTo("folderPath", path).findAll()
                val count = result.count()
                if (count == 0) {
                    val folder = MemeFolder()
                    folder.folderPath = path
                    realm.copyToRealm(folder)
                }
            }
        }
    }

    // Delete folders that are still in database, but where deleted on device.
    private fun deleteNotExistingFolders(realm: Realm, devicePaths: List<String>) {
        realm.executeTransaction { realm ->
            realm.where(MemeFolder::class.java)
                .not().`in`("folderPath", devicePaths.toTypedArray()).findAll()
                .deleteAllFromRealm()
        }
    }

    fun syncFolder(ctx: Context, realm: Realm, folder: MemeFolder) {

    }


}