package com.soszynski.mateusz.dotmeme

import android.util.Log
import io.realm.Realm
import java.io.File

/*
 * Almost all things related to database of memes. *
 *
 * Do I need to say that almost all of this requires READ_EXTERNAL_MEMORY permission?
 */

class Memebase {
    companion object {
        val TAG = "Memebase class"
    }

    // Returns new found folders
    fun syncFoldersIndex(realm: Realm, paths: List<String>): List<MemeFolder> {
        val newFolders = addNewFolders(realm, paths)
        deleteNotExistingFolders(realm, paths)
        return newFolders
    }

    // Discover new folders and add them to database.
    private fun addNewFolders(realm: Realm, devicePaths: List<String>): List<MemeFolder> {
        val newFound: MutableList<MemeFolder> = mutableListOf()
        realm.executeTransactionAsync { realm ->
            for (path in devicePaths) {
                val count = realm.where(MemeFolder::class.java)
                    .equalTo("folderPath", path).findAll()
                    .count()

                if (count == 0) {
                    val folder = MemeFolder()
                    folder.folderPath = path
                    realm.copyToRealm(folder)

                    newFound.add(folder)
                }
            }
        }
        return newFound.toList()
    }

    // Delete folders that are still in database, but where deleted on device.
    private fun deleteNotExistingFolders(realm: Realm, devicePaths: List<String>) {
        realm.executeTransaction { realm ->
            val result =
                realm.where(MemeFolder::class.java)
                    .not().`in`("folderPath", devicePaths.toTypedArray()).findAll()
            Log.i(TAG, "Deleting folders from database, because they were not found on device: \n$result")
            result.deleteAllFromRealm()
        }
    }

    // This takes a while, so run it async.
    fun syncFolder(realm: Realm, folder: MemeFolder) {
        deleteNotExistingMemesFromFolder(realm, folder)
        scanAndAddNewMemesInFolder(realm, folder)
    }

    private fun scanAndAddNewMemesInFolder(realm: Realm, folder: MemeFolder) {
        // TODO: function working without stoping thread, but with callback when meme was added
    }

    private fun deleteNotExistingMemesFromFolder(realm: Realm, folder: MemeFolder) {
        val imagesList =
            PainKiller()
                .getAllImagesInFolder(File(folder.folderPath))
                .map(File::getAbsolutePath)
                .toTypedArray()

        realm.executeTransaction { realm ->
            val result =
                folder.memes.where()
                    .not().`in`("filePath", imagesList)
                    .findAll()
            Log.i(TAG, "Deleting memes from database, because they were not found on device: \n$result")
            result.deleteAllFromRealm()
        }
    }

}