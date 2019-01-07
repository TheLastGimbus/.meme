package com.soszynski.mateusz.dotmeme

import android.graphics.BitmapFactory
import android.util.Log
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import io.realm.Realm
import java.io.File

/*
 * Almost all things related to database of memes.
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
            if (result.count() > 0) {
                Log.i(TAG, "Deleting folders from database, because they were not found on device: \n$result")
                result.deleteAllFromRealm()
            }
        }
    }

    // This only updates index in database. Does't actually scan any image.
    // Returns new memes that were added.
    fun syncFolder(realm: Realm, folder: MemeFolder): List<Meme> {
        deleteNotExistingMemesFromFolder(realm, folder)
        return addNewMemesToFolder(realm, folder)
    }

    private fun addNewMemesToFolder(realm: Realm, folder: MemeFolder): List<Meme> {
        val newMemes = mutableListOf<Meme>()

        val images = PainKiller().getAllImagesInFolder(File(folder.folderPath))
        realm.executeTransaction {
            for (image in images) {
                // If Meme with certain path doesn't exist yet
                if (folder.memes.where().equalTo("filePath", image.absolutePath).findAll().count() == 0) {
                    val meme = Meme()
                    meme.filePath = image.absolutePath
                    folder.memes.add(meme)
                    newMemes.add(meme)
                }
            }
        }

        return newMemes
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
            if (result.count() > 0) {
                Log.i(TAG, "Deleting memes from database, because they were not found on device: \n$result")
                result.deleteAllFromRealm()
            }
        }
    }

    fun scanFolder(
        realm: Realm,
        folder: MemeFolder,
        progress: (all: Int, scanned: Int) -> Unit,
        finished: () -> Unit
    ) {
        // very important to do it all the time if user deletes images while scanning
        syncFolder(realm, folder)

        val notScannedMemes = folder.memes.where()
            .equalTo("isScanned", false).findAll()
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
                    val scanned = folder.memes.where().equalTo("isScanned", true).findAll().count()
                    progress(all, scanned)

                    // This is theoretically recursion, but actually no code is left in stack
                    scanFolder(realm, folder, progress, finished)
                }
        } else {
            finished()
        }
    }

}