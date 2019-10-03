package com.soszynski.mateusz.dotmeme

import android.content.Context
import android.graphics.BitmapFactory
import android.os.FileObserver
import android.util.Log
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.perf.FirebasePerformance
import io.realm.Realm
import org.apache.commons.lang3.StringUtils
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File


/**
 * This class contains almost all things related to database of memes.
 * If you want to do any work with database - do it here.
 *
 * All functions don't take long enough to stop uiThread. If they take long, they use their own doAsync and callbacks.
 */

class Memebase {
    companion object {
        const val TAG = "Memebase class"
    }

    private lateinit var scanningFileObserver: FileObserver

    var isSyncing = false
    var isScanning = false
    var scanningCanceled = false

    private val ocr = FirebaseVision.getInstance().onDeviceTextRecognizer

    /**
     * This function synchronizes folders index on database.
     *
     * @param realm
     * @param paths list of paths on device that contain photos.
     *
     * @return new folders. This can be used to ask user whether to sync those new folders.
     */
    private fun syncFoldersIndex(realm: Realm, paths: List<String>): List<MemeFolder> {
        val newFolders = addNewFolders(realm, paths)
        deleteNotExistingFolders(realm, paths)
        return newFolders
    }

    /**
     * Discover new folders and add them to database.
     *
     * @param realm
     * @param devicePaths list of paths on device that contain photos.
     *
     * @return new folders. This can be used to ask user whether to sync those new folders.
     */
    private fun addNewFolders(realm: Realm, devicePaths: List<String>): List<MemeFolder> {
        val newFound: MutableList<MemeFolder> = mutableListOf()
        realm.executeTransaction { realm ->
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

    /**
     * Delete folders that are still in database, but where deleted on device.
     *
     * @param realm
     * @param devicePaths list of paths on device that contain photos.
     */
    private fun deleteNotExistingFolders(realm: Realm, devicePaths: List<String>) {
        realm.executeTransaction { realm ->
            val result =
                realm.where(MemeFolder::class.java)
                    .not().`in`(MemeFolder.FOLDER_PATH, devicePaths.toTypedArray()).findAll()
            if (result.count() > 0) {
                Log.i(
                    TAG,
                    "Deleting folders from database, because they were not found on device: \n$result"
                )
                result.deleteAllFromRealm()
            }
        }
    }

    /**
     *  This only updates index in database. Does't scan any image
     *
     *  @param realm
     *  @param folder takes [MemeFolder] to sync [Meme]s inside it.
     *  @param finished callback with new [Meme]s that were added to database index.
     */
    private fun syncFolder(
        realm: Realm,
        folder: MemeFolder,
        finished: (newMemes: List<Meme>) -> Unit
    ) {
        val trace = FirebasePerformance.getInstance().newTrace("memebase_sync_folder")
        trace.start()
        val folderPath = File(folder.folderPath) // Realms can't go between threads :/
        doAsync {
            val imagesInFolderList = PainKiller().getAllImagesInFolder(folderPath)
            uiThread {
                deleteNotExistingMemesFromFolder(realm, folder, imagesInFolderList) {
                    // when finished
                    addNewMemesToFolder(realm, folder, imagesInFolderList) { newMemes ->
                        // finished
                        trace.putMetric("folder_images_count", folder.memes.count().toLong())
                        trace.putMetric("folder_new_memes", newMemes.count().toLong())
                        trace.stop()

                        finished(newMemes)
                    }
                }
            }
        }
    }

    /**
     * Adds new [Meme]s found on device to given [MemeFolder]
     *
     * @param realm
     * @param folder [MemeFolder] where new memes will be added.
     * @param filesList list of image files inside [folder]. It is separate for better optimization,
     *        since getting list of 5000 images from folder can take some time.
     * @param finished callback when finished with new [Meme]s that were added to database index.
     */
    private fun addNewMemesToFolder(
        realm: Realm,
        folder: MemeFolder,
        filesList: List<File>, // for better optimization
        finished: (newMemes: List<Meme>) -> Unit
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
                    if (folder.memes.where().equalTo(
                            Meme.FILE_PATH,
                            image.absolutePath
                        ).findAll().count() == 0
                    ) {
                        val meme = Meme()
                        meme.filePath = image.absolutePath
                        folder.memes.add(meme)
                        newMemes.add(meme)
                    }
                }
                Log.d(TAG, "LOL")
            },
            {
                // success
                finished(newMemes)
            },
            { e: Throwable ->
                // error
                e.printStackTrace()
                finished(newMemes) // but we still need to make callback
            }
        )
    }

    /**
     * Deletes [Meme]s from database that weren't found on device.
     *
     * @param realm
     * @param folder [MemeFolder] where memes will be deleted.
     * @param filesList list of image files inside [folder]. It is separate for better optimization,
     *        since getting list of 5000 images from folder can take some time.
     * @param finished callback when finished.
     */
    private fun deleteNotExistingMemesFromFolder(
        realm: Realm,
        folder: MemeFolder,
        filesList: List<File>, // for better optimization
        finished: () -> Unit
    ) {
        // We need to do this, because
        // "Realm objects can only be accessed on the thread they were created"
        val folderPath = folder.folderPath
        val config = realm.configuration
        doAsync {
            val asyncRealm = Realm.getInstance(config) // TODO: this should not be used

            asyncRealm.executeTransaction { realm ->
                // Yeah, kinda boilerplate, but whatever...
                val folder = realm.where(MemeFolder::class.java)
                    .equalTo(MemeFolder.FOLDER_PATH, folderPath).findFirst()!!

                val millis = System.currentTimeMillis()
                val result =
                    folder.memes.where()
                        .not()
                        .`in`(
                            Meme.FILE_PATH,
                            filesList.map(File::getAbsolutePath).toTypedArray()
                        )
                        .findAll()

                result.deleteAllFromRealm()

                // success
                uiThread {
                    finished()
                }
            }
        }
    }

    /**
     * Synchronizes whole index. All [MemeFolder]s and all [Meme]s inside them.
     *
     * @param realm [Realm]
     * @param ctx [Context]
     * @param finished
     */
    fun syncAllFolders(
        realm: Realm,
        ctx: Context,
        syncFoldersIndex: Boolean = true,
        finished: (newFolders: List<MemeFolder>) -> Unit
    ) {
        if (!PainKiller().hasStoragePermission(ctx)) {
            Log.w(TAG, "Can't sync: no storage permission!")
            finished(emptyList())
            return
        }

        isSyncing = true

        var newFolders = emptyList<MemeFolder>()

        doAsync {
            if (syncFoldersIndex) {
                val foldersList = PainKiller().getAllFoldersWithImages(ctx)
                    .map(File::getAbsolutePath)
                uiThread {
                    newFolders = syncFoldersIndex(realm, foldersList)
                }
            }
            uiThread {
                val foldersToSync = realm.where(MemeFolder::class.java)
                    .equalTo(MemeFolder.IS_SCANNABLE, true).findAll()
                syncAllFoldersRecursive(realm, foldersToSync) {
                    // finished
                    isSyncing = false
                    finished(newFolders)
                }
            }

        }
    }

    /**
     * Recursive part of [syncAllFolders].
     *
     * @param realm [Realm]
     * @param foldersToSync list of [MemeFolder]s that are left for syncing.
     * @param finished callback when finished.
     */
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

    /**
     * Scans whole [folder] recursively.
     * Make sure to sync [folder] before calling this.
     *
     * @param realm [Realm]
     * @param folder [MemeFolder] to scan.
     * @param progress callback when progress was made. First val contains number of all [Meme]s in [folder],
     *                 second is number of [Meme]s that are already scanned.
     * @param finished callback when whole scanning is finished. It's also called when [scanningCanceled] is true.
     */
    fun scanFolder(
        realm: Realm,
        ctx: Context,
        folder: MemeFolder,
        progress: (all: Int, scanned: Int) -> Unit,
        finished: () -> Unit
    ) {
        if (!PainKiller().hasStoragePermission(ctx)) {
            Log.w(TAG, "Can't scan: no storage permission!")
            finished()
            return
        }

        isScanning = true

        val folderPath = folder.folderPath

        var changeInFolder = false
        scanningFileObserver = object : FileObserver(folderPath) {
            // keep in mind that this is different thread, we can't access realms there
            override fun onEvent(event: Int, file: String?) {
                val wantedEvents = intArrayOf(
                    CREATE,
                    CLOSE_WRITE,
                    DELETE,
                    DELETE_SELF,
                    MODIFY,
                    MOVED_FROM,
                    MOVED_TO,
                    MOVE_SELF
                )
                if (wantedEvents.contains(event)) {
                    Log.d(TAG, "File observer, change in $folderPath/$file")
                    changeInFolder = true
                }
            }
        }
        scanningFileObserver.startWatching()


        val notScannedMemes = folder.memes.where()
            .equalTo(Meme.IS_SCANNED, false).findAll()
        if (notScannedMemes.count() > 0) {
            val trace = FirebasePerformance.getInstance().newTrace("memebase_meme_scan")
            trace.start()

            val meme = notScannedMemes.first()!!
            val bitmap = BitmapFactory.decodeFile(meme.filePath)

            // Don't even ask me why this needs to be in 'try'
            try {
                trace.putMetric("bitmap_size_bytes", bitmap.byteCount.toLong())
                trace.putMetric("bitmap_size_height", bitmap.height.toLong())
                trace.putMetric("bitmap_size_width", bitmap.width.toLong())
                trace.putMetric("bitmap_size_all", (bitmap.height * bitmap.width).toLong())
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val fireImage = FirebaseVisionImage.fromBitmap(bitmap)
            ocr.processImage(fireImage)
                .addOnSuccessListener { fireText ->
                    if (scanningCanceled) {
                        isScanning = false
                        return@addOnSuccessListener
                    }
                    try {
                        realm.executeTransaction {
                            meme.rawText = fireText.text
                            meme.isScanned = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    syncFolder(
                        realm,
                        folder
                    ) {} // Probably something wrong with current index if error occurred
                }
                .continueWith {
                    trace.stop()

                    if (scanningCanceled) {
                        scanningFileObserver.stopWatching()
                        isScanning = false
                        finished()
                        return@continueWith
                    }

                    val all = folder.memes.count()
                    val scanned =
                        folder.memes.where().equalTo(Meme.IS_SCANNED, true).findAll().count()
                    progress(all, scanned)

                    if (changeInFolder) {
                        syncFolder(realm, folder) {}
                    }
                    scanningFileObserver.stopWatching()

                    // This is theoretically recursion, but there is nothing big to be left in stack
                    scanFolder(realm, ctx, folder, progress, finished)
                }
        } else {
            isScanning = false
            finished()
        }

    }

    /**
     * Scans all [MemeFolder]s with IS_SCANNABLE flag.
     *
     * @param realm [Realm]
     * @param progress callback when progress was made. It contains folder which is scanned right now,
     *                  total number of images in it and number of currently scanned images.
     * @param finished callback when everything was finished.
     */
    fun scanAllFolders(
        realm: Realm,
        ctx: Context,
        progress: (folder: MemeFolder, all: Int, scanned: Int) -> Unit,
        finished: () -> Unit
    ) {
        if (!PainKiller().hasStoragePermission(ctx)) {
            Log.w(TAG, "Can't scan: no storage permission!")
            finished()
            return
        }

        if (scanningCanceled) {
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
            finished()
            return
        }

        val folderName = File(folderToScan.folderPath).name

        scanFolder(
            realm, ctx, folderToScan,
            { max, scanned ->
                // progress
                Log.i(TAG, "Scanning folder $folderName, progress: $scanned, max: $max")
                progress(folderToScan, max, scanned)
            },
            {
                // finished
                Log.i(TAG, "Finished scanning folder $folderName")
                scanAllFolders(realm, ctx, progress, finished)
            }
        )

    }

    /**
     * Search for memes.
     * This function is exception from "no long functions in Memebase class" rule.
     * It's just easier to migrate Realm and all folders to different thread when calling it.
     *
     * @param realm [Realm]
     * @param query text to search.
     * @param folders [MemeFolder]s to scan. Default value is all of them.
     *
     * @return list of found [Meme]s
     */
    fun search(
        realm: Realm,
        query: String,
        folders: List<MemeFolder> = realm.where(MemeFolder::class.java).findAll().toList()
    ): List<Meme> {
        val trace = FirebasePerformance.getInstance().newTrace("memebase_search")
        trace.start()

        val memeList = mutableListOf<Pair<Int, Meme>>()

        Log.i(TAG, "Begin of search, query: $query")

        val keywords =
            StringUtils.stripAccents(query).split(" ".toRegex()).dropLastWhile { it.isEmpty() }
        for (folder in folders) {
            for (meme in folder.memes) {
                var pair = Pair(0, meme)
                val strippedText = StringUtils.stripAccents(meme.rawText)
                for (keyword in keywords) {
                    if (strippedText.contains(keyword, true)) {
                        pair = pair.copy(first = pair.first + 1)
                    }
                }

                if (pair.first > 0) {
                    memeList.add(pair)
                }
            }
        }

        val finalList = memeList
            .sortedByDescending { it.first }
            .map { return@map it.second }

        trace.putMetric("memes_all_count", folders.sumBy { it.memes.count() }.toLong())
        trace.putMetric("memes_found_count", memeList.count().toLong())
        trace.stop()

        Log.i(TAG, "Memes found: ${trace.getLongMetric("memes_found_count")}")

        return finalList
    }

}