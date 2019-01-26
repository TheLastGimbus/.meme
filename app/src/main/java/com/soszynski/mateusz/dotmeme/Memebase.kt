package com.soszynski.mateusz.dotmeme

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import io.realm.Case
import io.realm.Realm
import io.realm.RealmResults
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File


/*
 * Almost all things related to database of memes.
 *
 * All functions here can't stop uiThread.
 * All must be done by using doAsync, context and callbacks.
 *
 * Do I need to say that almost all of this requires READ_EXTERNAL_MEMORY permission?
 */

class Memebase {
    companion object {
        val TAG = "Memebase class"
    }

    var isSyncing = false
    var isScanning = false

    // Returns new found folders
    private fun syncFoldersIndex(realm: Realm, paths: List<String>): List<MemeFolder> {
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
                    .equalTo(MemeFolder.FOLDER_PATH, path).findAll()
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
                    .not().`in`(MemeFolder.FOLDER_PATH, devicePaths.toTypedArray()).findAll()
            if (result.count() > 0) {
                Log.i(TAG, "Deleting folders from database, because they were not found on device: \n$result")
                result.deleteAllFromRealm()
            }
        }
    }

    // This only updates index in database. Does't actually scan any image.
    // Returns new memes that were added.
    private fun syncFolder(
        realm: Realm,
        folder: MemeFolder,
        finished: (List<Meme>) -> Unit
    ) {
        val folderPath = File(folder.folderPath) // Realms can't go between threads :/
        doAsync {
            val imagesInFolderList = PainKiller().getAllImagesInFolder(folderPath)
            uiThread {
                deleteNotExistingMemesFromFolder(realm, folder, imagesInFolderList) {
                    // when finished
                    addNewMemesToFolder(realm, folder, imagesInFolderList) { newMemes ->
                        // when finished
                        finished(newMemes)
                    }
                }
            }
        }
    }

    private fun addNewMemesToFolder(
        realm: Realm,
        folder: MemeFolder,
        filesList: List<File>, // for better optimization
        finished: (List<Meme>) -> Unit
    ) {
        val newMemes = mutableListOf<Meme>()

        // We need to do this, because
        // "Realm objects can only be accessed on the thread they were created"
        val folderPath = folder.folderPath

        realm.executeTransactionAsync(
            { realm ->
                // transaction

                // Yeah, kinda boilerplate, but whatever...
                val folder = realm.where(MemeFolder::class.java)
                    .equalTo(MemeFolder.FOLDER_PATH, folderPath).findFirst()!!

                for (image in filesList) {
                    // If Meme with certain path doesn't exist yet
                    if (folder.memes.where().equalTo(Meme.FILE_PATH, image.absolutePath).findAll().count() == 0) {
                        val meme = Meme()
                        meme.filePath = image.absolutePath
                        folder.memes.add(meme)
                        newMemes.add(meme)
                    }
                }
            },
            {
                // success
                finished(newMemes)
            },
            { e: Throwable ->
                // error
                e.printStackTrace()
                finished(newMemes)
            }
        )
    }

    private fun deleteNotExistingMemesFromFolder(
        realm: Realm,
        folder: MemeFolder,
        filesList: List<File>, // for better optimization
        finished: () -> Unit
    ) {
        // We need to do this, because
        // "Realm objects can only be accessed on the thread they were created"
        val folderPath = folder.folderPath
        realm.executeTransactionAsync(
            { realm ->
                // Yeah, kinda boilerplate, but whatever...
                val folder = realm.where(MemeFolder::class.java)
                    .equalTo(MemeFolder.FOLDER_PATH, folderPath).findFirst()!!

                val result =
                    folder.memes.where()
                        .not().`in`(Meme.FILE_PATH, filesList.map(File::getAbsolutePath).toTypedArray())
                        .findAll()
                result.deleteAllFromRealm()
            },
            {
                // success
                finished()
            },
            { e: Throwable ->
                // error
                e.printStackTrace()
                finished()
            }
        )
    }

    // You should call this only after syncing folders index
    fun syncAllFolders(realm: Realm, ctx: Context, finished: () -> Unit) {
        isSyncing = true
        doAsync {
            val foldersList = PainKiller().getAllFoldersWithImages(ctx)
                .map(File::getAbsolutePath)
            uiThread {
                syncFoldersIndex(realm, foldersList)
                val foldersToSync = realm.where(MemeFolder::class.java)
                    .equalTo(MemeFolder.IS_SCANNABLE, true).findAll()
                syncAllFoldersRecursive(realm, foldersToSync) {
                    isSyncing = false
                    finished()
                }
            }

        }
    }

    private fun syncAllFoldersRecursive(
        realm: Realm,
        foldersToSync: List<MemeFolder>,
        finished: () -> Unit
    ) {
        if (foldersToSync.isEmpty()) {
            finished()
            return
        }

        val folder = foldersToSync.first()
        syncFolder(realm, folder) {
            Log.i(TAG, "Finished syncing folder ${File(folder.folderPath).name}")
            syncAllFoldersRecursive(realm, foldersToSync.drop(1), finished)
        }
    }

    fun scanFolder(
        realm: Realm,
        folder: MemeFolder,
        progress: (all: Int, scanned: Int) -> Unit,
        finished: () -> Unit
    ) {
        isScanning = true
        // TODO: Make scanning safe by syncing folder before scanning
        val notScannedMemes = folder.memes.where()
            .equalTo(Meme.IS_SCANNED, false).findAll()
        if (notScannedMemes.count() > 0) {
            val meme = notScannedMemes.first()!!
            val bitmap = BitmapFactory.decodeFile(meme.filePath)
            val fireImage = FirebaseVisionImage.fromBitmap(bitmap)
            val ocr = FirebaseVision.getInstance().onDeviceTextRecognizer
            ocr.processImage(fireImage)
                .addOnSuccessListener { fireText ->
                    realm.executeTransaction {
                        meme.rawText = fireText.text
                        meme.isScanned = true
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
                .continueWith {
                    val all = folder.memes.count()
                    val scanned = folder.memes.where().equalTo(Meme.IS_SCANNED, true).findAll().count()
                    progress(all, scanned)

                    // This is theoretically recursion, but actually no code is left in stack
                    scanFolder(realm, folder, progress, finished)
                }
        } else {
            isScanning = false
            finished()
        }

    }

    // TODO: Make better, more flexible search
    fun search(
        realm: Realm,
        query: String,
        folders: RealmResults<MemeFolder>? = realm.where(MemeFolder::class.java).findAll()
    ): List<Meme> {
        val memeList = mutableListOf<Meme>()

        val keywords = query.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (folders != null) {
            for(folder in folders) {
                for (keyword in keywords) {
                    memeList.addAll(
                        folder.memes.where()
                            .contains(
                                Meme.RAW_TEXT
                                , keyword, Case.INSENSITIVE
                            )
                            .findAll()
                    )
                }
            }
        }
        return memeList
    }

}